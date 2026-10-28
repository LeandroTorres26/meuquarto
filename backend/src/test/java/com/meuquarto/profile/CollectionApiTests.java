package com.meuquarto.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The collection API against a real PostgreSQL, with a fake catalog behind the registry (no real provider). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CollectionApiTests.FakeCatalogs.class})
class CollectionApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";

    /** MOVIE catalog: 1 Zebra (2001), 2 Alpha (1999), 3 Mid (no year), 99 adult. Other categories are not served. */
    private static final Map<String, CatalogEntry> WORKS = Map.of(
            "1", entry("1", "Zebra", 2001, false),
            "2", entry("2", "Alpha", 1999, false),
            "3", entry("3", "mid", null, false),
            "99", entry("99", "Adult", 2020, true));

    private static CatalogEntry entry(String id, String title, Integer year, boolean adult) {
        return new CatalogEntry(MediaCategory.MOVIE, "FAKE", id, title, year, "https://img.example/" + id + ".jpg", adult);
    }

    private record FakeCatalog() implements CatalogProvider {

        @Override
        public String id() {
            return "FAKE";
        }

        @Override
        public boolean serves(MediaCategory category) {
            return category == MediaCategory.MOVIE;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public List<CatalogEntry> search(MediaCategory category, String query) {
            return List.of();
        }

        @Override
        public Optional<CatalogEntry> fetch(MediaCategory category, String externalId) {
            return Optional.ofNullable(WORKS.get(externalId));
        }
    }

    @TestConfiguration
    static class FakeCatalogs {

        @Bean
        @Primary
        CatalogRegistry fakeRegistry() {
            return new CatalogRegistry(List.of(new FakeCatalog()));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions add(Cookie session, String category, String externalId) throws Exception {
        return mvc.perform(post("/api/collection").cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"%s\",\"externalId\":\"%s\"}".formatted(category, externalId)));
    }

    private long addMovie(Cookie session, String externalId) throws Exception {
        String body = add(session, "MOVIE", externalId).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions change(MockHttpServletRequestBuilder request, Cookie session, String body) throws Exception {
        return mvc.perform(request.cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions list(Cookie session, String... queryPairs) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/collection").cookie(session).param("category", "MOVIE");
        for (int i = 0; i < queryPairs.length; i += 2) {
            request.param(queryPairs[i], queryPairs[i + 1]);
        }
        return mvc.perform(request);
    }

    /** Pretends a sync brought the work in, the way the sync code will (provider + linked account). */
    private void markAsSynced(long itemId) {
        jdbc.update("insert into collection_item_sources (collection_item_id, source, linked_account_id) values (?, 'LETTERBOXD', 7)", itemId);
    }

    /** Asserts the titles of the page, in order. */
    private static ResultMatcher titles(String... expected) {
        return result -> {
            List<String> actual = JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].media.title");
            org.assertj.core.api.Assertions.assertThat(actual).containsExactly(expected);
        };
    }

    // --- add ---

    @Test
    void addingSnapshotsTheWorkFromTheCatalogAndReturnsTheItem() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        add(session, "MOVIE", "1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.category").value("MOVIE"))
                .andExpect(jsonPath("$.media.title").value("Zebra"))
                .andExpect(jsonPath("$.media.year").value(2001))
                .andExpect(jsonPath("$.media.provider").value("FAKE"))
                .andExpect(jsonPath("$.media.externalId").value("1"))
                .andExpect(jsonPath("$.media.imageUrl").value("https://img.example/1.jpg"))
                .andExpect(jsonPath("$.hidden").value(false))
                .andExpect(jsonPath("$.note").doesNotExist())
                .andExpect(jsonPath("$.sources[0]").value("MANUAL"))
                .andExpect(jsonPath("$.removable").value(true));
    }

    @Test
    void br07AddingTheSameWorkTwiceReturnsTheExistingItem() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long first = addMovie(session, "1");

        add(session, "MOVIE", "1").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first));

        Integer rows = jdbc.queryForObject("select count(*) from collection_items", Integer.class);
        org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(1);
    }

    @Test
    void addingAHiddenItemAgainShowsIt() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long id = addMovie(session, "1");
        change(patch("/api/collection/" + id), session, "{\"hidden\":true}").andExpect(status().isOk());

        add(session, "MOVIE", "1").andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(false));
    }

    @Test
    void twoUsersCanHoldTheSameWork() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");

        add(ana, "MOVIE", "1").andExpect(status().isCreated());
        add(bia, "MOVIE", "1").andExpect(status().isCreated());
    }

    @Test
    void theClientCannotChooseTitleOrImage() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        mvc.perform(post("/api/collection").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MOVIE\",\"externalId\":\"1\",\"title\":\"Hacked\",\"imageUrl\":\"https://evil.example/x.png\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media.title").value("Zebra"))
                .andExpect(jsonPath("$.media.imageUrl").value("https://img.example/1.jpg"));
    }

    @Test
    void unknownWorksAndUnservedCategoriesAreCatalogUnavailable() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        add(session, "MOVIE", "12345").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("CATALOG_UNAVAILABLE"));
        add(session, "BOOK", "1").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("CATALOG_UNAVAILABLE"));
    }

    @Test
    void adultWorksAreRejected() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        add(session, "MOVIE", "99").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ADULT_NOT_ALLOWED"));
    }

    @Test
    void invalidAddBodiesAreBadRequests() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        add(session, "PODCAST", "1").andExpect(status().isBadRequest());
        add(session, "MOVIE", "").andExpect(status().isBadRequest());
        add(session, "MOVIE", "../../etc").andExpect(status().isBadRequest());
        add(session, "MOVIE", "a".repeat(65)).andExpect(status().isBadRequest());
        change(post("/api/collection"), session, "{}").andExpect(status().isBadRequest());
    }

    // --- list ---

    @Test
    void listSortsByRecentTitleAndYear() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        addMovie(session, "1");
        addMovie(session, "2");
        addMovie(session, "3");

        list(session).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(titles("mid", "Alpha", "Zebra"));
        list(session, "sort", "title")
                .andExpect(titles("Alpha", "mid", "Zebra"));
        list(session, "sort", "year")
                .andExpect(titles("Zebra", "Alpha", "mid"));
        list(session, "sort", "weird").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SORT"));
    }

    @Test
    void listPaginatesInEverySort() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        addMovie(session, "1");
        addMovie(session, "2");
        addMovie(session, "3");

        list(session, "sort", "title", "size", "2", "page", "0")
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.size").value(2))
                .andExpect(titles("Alpha", "mid"));
        list(session, "sort", "title", "size", "2", "page", "1")
                .andExpect(titles("Zebra"));
        list(session, "sort", "title", "size", "2", "page", "5")
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.total").value(3));
        list(session, "size", "2", "page", "1")
                .andExpect(titles("Zebra"));
    }

    @Test
    void pageSizeIsCappedAndParametersValidated() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        list(session, "size", "50").andExpect(status().isOk());
        list(session, "size", "51").andExpect(status().isBadRequest());
        list(session, "size", "0").andExpect(status().isBadRequest());
        list(session, "page", "-1").andExpect(status().isBadRequest());
        mvc.perform(get("/api/collection").cookie(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/collection").cookie(session).param("category", "PODCAST")).andExpect(status().isBadRequest());
    }

    @Test
    void hiddenItemsAreListedOnlyWhenAsked() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long zebra = addMovie(session, "1");
        addMovie(session, "2");
        change(patch("/api/collection/" + zebra), session, "{\"hidden\":true}").andExpect(status().isOk());

        list(session).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].media.title").value("Alpha"));
        list(session, "sort", "title").andExpect(jsonPath("$.total").value(1));
        list(session, "includeHidden", "true").andExpect(jsonPath("$.total").value(2));
        list(session, "includeHidden", "true", "sort", "year").andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void listShowsOnlyTheCallersItems() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        addMovie(ana, "1");
        addMovie(bia, "2");

        list(ana).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].media.title").value("Zebra"));
        list(bia).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].media.title").value("Alpha"));
        list(bia, "sort", "title").andExpect(jsonPath("$.items[0].media.title").value("Alpha"));
    }

    // --- update ---

    @Test
    void noteCanBeSetReplacedAndCleared() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long id = addMovie(session, "1");

        change(patch("/api/collection/" + id), session, "{\"note\":\"  vale a pena  \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").value("vale a pena"));
        change(patch("/api/collection/" + id), session, "{\"hidden\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").value("vale a pena"))
                .andExpect(jsonPath("$.hidden").value(true));
        change(patch("/api/collection/" + id), session, "{\"note\":\"   \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    void noteIsStoredAsPlainText() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long id = addMovie(session, "1");

        change(patch("/api/collection/" + id), session, "{\"note\":\"<script>alert(1)</script>\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").value("<script>alert(1)</script>"));
    }

    @Test
    void noteRulesAreEnforced() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long id = addMovie(session, "1");

        change(patch("/api/collection/" + id), session, "{\"note\":\"" + "a".repeat(140) + "\"}").andExpect(status().isOk());
        change(patch("/api/collection/" + id), session, "{\"note\":\"" + "a".repeat(141) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("NOTE_TOO_LONG"));
        change(patch("/api/collection/" + id), session, "{\"note\":\"" + "a".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest());
        change(patch("/api/collection/" + id), session, "{\"note\":\"a\\nb\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("NOTE_INVALID"));
        change(patch("/api/collection/" + id), session, "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("EMPTY_UPDATE"));
        change(patch("/api/collection/" + id), session, "{\"hidden\":\"maybe\"}").andExpect(status().isBadRequest());
    }

    @Test
    void hideAndUnhideRoundTrip() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long id = addMovie(session, "1");

        change(patch("/api/collection/" + id), session, "{\"hidden\":true}").andExpect(jsonPath("$.hidden").value(true));
        change(patch("/api/collection/" + id), session, "{\"hidden\":false}").andExpect(jsonPath("$.hidden").value(false));
    }

    // --- remove ---

    @Test
    void manualItemsCanBeDeleted() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long id = addMovie(session, "1");

        mvc.perform(delete("/api/collection/" + id).cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());

        list(session, "includeHidden", "true").andExpect(jsonPath("$.total").value(0));
        mvc.perform(delete("/api/collection/" + id).cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNotFound());
    }

    @Test
    void fr09SyncedItemsCannotBeDeletedOnlyHidden() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long id = addMovie(session, "1");
        markAsSynced(id);

        list(session).andExpect(jsonPath("$.items[0].removable").value(false))
                .andExpect(jsonPath("$.items[0].sources.length()").value(2));
        mvc.perform(delete("/api/collection/" + id).cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CANNOT_REMOVE_SYNCED"));
        change(patch("/api/collection/" + id), session, "{\"hidden\":true}").andExpect(status().isOk());
        list(session).andExpect(jsonPath("$.total").value(0));
    }

    // --- authorization ---

    @Test
    void anonymousVisitorsAreRejectedOnEveryEndpoint() throws Exception {
        mvc.perform(get("/api/collection").param("category", "MOVIE")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/collection").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"MOVIE\",\"externalId\":\"1\"}")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/collection/1").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/collection/1").with(RealCsrf.token(mvc))).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherUsersItemLooksLikeAMissingOneAndIsNeverChanged() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long anasItem = addMovie(ana, "1");

        change(patch("/api/collection/" + anasItem), bia, "{\"note\":\"mine now\",\"hidden\":true}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ITEM_NOT_FOUND"));
        mvc.perform(delete("/api/collection/" + anasItem).cookie(bia).with(RealCsrf.token(mvc)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ITEM_NOT_FOUND"));
        change(patch("/api/collection/999999"), bia, "{\"hidden\":true}").andExpect(status().isNotFound());

        list(ana).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].note").doesNotExist())
                .andExpect(jsonPath("$.items[0].hidden").value(false));
    }

    @Test
    void theOwnerComesFromTheSessionNotFromTheRequest() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        Long anasProfile = jdbc.queryForObject("select id from profiles where slug = 'ana'", Long.class);

        mvc.perform(post("/api/collection").cookie(bia).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MOVIE\",\"externalId\":\"1\",\"profileId\":%d,\"userId\":1}".formatted(anasProfile)))
                .andExpect(status().isCreated());

        list(ana).andExpect(jsonPath("$.total").value(0));
        list(bia).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void stateChangingRequestsNeedACsrfToken() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long id = addMovie(session, "1");

        mvc.perform(post("/api/collection").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"MOVIE\",\"externalId\":\"2\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/collection/" + id).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":true}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/collection/" + id).cookie(session)).andExpect(status().isForbidden());

        list(session).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].hidden").value(false));
    }

    @Test
    void deletingTheAccountDeletesTheCollection() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        addMovie(session, "1");

        jdbc.update("delete from users");

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from collection_items", Integer.class)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from collection_item_sources", Integer.class)).isZero();
    }
}
