package com.meuquarto.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.profile.application.PlanResolver;
import com.meuquarto.profile.domain.FreePlan;
import com.meuquarto.profile.domain.PackPlan;
import com.meuquarto.profile.domain.Plan;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The highlights API against a real PostgreSQL, with a fake catalog and a plan the test can switch. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, HighlightApiTests.Fakes.class})
class HighlightApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";

    /** Serves movies "m1".."m20" (MOVIE), series "s1" and books "b1". */
    private record FakeCatalog() implements CatalogProvider {

        @Override
        public String id() {
            return "FAKE";
        }

        @Override
        public boolean serves(MediaCategory category) {
            return category == MediaCategory.MOVIE || category == MediaCategory.SERIES || category == MediaCategory.BOOK;
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
            return Optional.of(new CatalogEntry(category, "FAKE", externalId, "Title " + externalId, 2000,
                    "https://img.example/" + externalId + ".jpg", false));
        }
    }

    /** The plan every user has in these tests; a test swaps it to see the same API behave with the pack. */
    static class SwitchablePlans implements PlanResolver {

        private volatile Plan current = new FreePlan();

        @Override
        public Plan planOf(long userId) {
            return current;
        }
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        CatalogRegistry fakeRegistry() {
            return new CatalogRegistry(List.of(new FakeCatalog()));
        }

        @Bean
        @Primary
        SwitchablePlans switchablePlans() {
            return new SwitchablePlans();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SwitchablePlans plans;

    @AfterEach
    void cleanUp() {
        plans.current = new FreePlan();
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    /** Adds a work to the collection and returns the collection item id. */
    private long addToCollection(Cookie session, String category, String externalId) throws Exception {
        String body = mvc.perform(post("/api/collection").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"%s\",\"externalId\":\"%s\"}".formatted(category, externalId)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String categoryJson(String category) {
        return category == null ? "null" : "\"" + category + "\"";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie session, String body) throws Exception {
        return mvc.perform(request.cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions highlight(Cookie session, String category, long collectionItemId) throws Exception {
        return send(post("/api/highlights"), session,
                "{\"category\":%s,\"collectionItemId\":%d}".formatted(categoryJson(category), collectionItemId));
    }

    private long highlightId(Cookie session, String category, long collectionItemId) throws Exception {
        String body = highlight(session, category, collectionItemId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions reorder(Cookie session, String category, long... ids) throws Exception {
        String list = java.util.Arrays.stream(ids).mapToObj(Long::toString).collect(java.util.stream.Collectors.joining(","));
        return send(put("/api/highlights/order"), session, "{\"category\":%s,\"ids\":[%s]}".formatted(categoryJson(category), list));
    }

    /** The titles of one list in the overview, in order; {@code category} null is favorites. */
    @SuppressWarnings("unchecked")
    private List<String> titlesOf(Cookie session, String category) throws Exception {
        String body = mvc.perform(get("/api/highlights").cookie(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<java.util.Map<String, Object>> lists = JsonPath.read(body, "$.lists");
        for (java.util.Map<String, Object> list : lists) {
            if (java.util.Objects.equals(list.get("category"), category)) {
                List<java.util.Map<String, Object>> items = (List<java.util.Map<String, Object>>) list.get("items");
                return items.stream().map(item -> (String) ((java.util.Map<String, Object>) item.get("media")).get("title")).toList();
            }
        }
        throw new AssertionError("no list for " + category);
    }

    private long countRows(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    // --- mark, list, unmark ---

    @Test
    void markingAddsAtTheEndAndTheOverviewShowsTheOrderWithPlanAndLimit() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long one = addToCollection(session, "MOVIE", "m1");
        long two = addToCollection(session, "MOVIE", "m2");

        highlight(session, "MOVIE", one).andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionItemId").value(one)).andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.media.title").value("Title m1"));
        highlight(session, "MOVIE", two).andExpect(status().isCreated()).andExpect(jsonPath("$.position").value(1));

        mvc.perform(get("/api/highlights").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE")).andExpect(jsonPath("$.limit").value(4))
                // favorites first, then the seven categories, empty lists included
                .andExpect(jsonPath("$.lists.length()").value(8))
                .andExpect(jsonPath("$.lists[0].category").doesNotExist())
                .andExpect(jsonPath("$.lists[0].items.length()").value(0));
        assertThat(titlesOf(session, "MOVIE")).containsExactly("Title m1", "Title m2");
    }

    @Test
    void unmarkingRemovesItClosesTheGapAndFreesASlot() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long[] items = new long[5];
        long[] marks = new long[4];
        for (int i = 0; i < 5; i++) {
            items[i] = addToCollection(session, "MOVIE", "m" + (i + 1));
        }
        for (int i = 0; i < 4; i++) {
            marks[i] = highlightId(session, "MOVIE", items[i]);
        }
        highlight(session, "MOVIE", items[4]).andExpect(status().isConflict());

        send(delete("/api/highlights/" + marks[1]), session, "").andExpect(status().isNoContent());

        assertThat(titlesOf(session, "MOVIE")).containsExactly("Title m1", "Title m3", "Title m4");
        highlight(session, "MOVIE", items[4]).andExpect(status().isCreated()).andExpect(jsonPath("$.position").value(3));
        send(delete("/api/highlights/" + marks[1]), session, "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("HIGHLIGHT_NOT_FOUND"));
    }

    // --- BR-02 limit ---

    @Test
    void fifthHighlightIsRefusedOnTheFreePlan_BR02() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        for (int i = 1; i <= 4; i++) {
            highlightId(session, "MOVIE", addToCollection(session, "MOVIE", "m" + i));
        }

        highlight(session, "MOVIE", addToCollection(session, "MOVIE", "m5")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("HIGHLIGHT_LIMIT_REACHED"));
        assertThat(countRows("highlights")).isEqualTo(4);
    }

    @Test
    void theSameApiAllowsTwelveWhenThePlanIsThePack_BR02() throws Exception {
        plans.current = new PackPlan();
        Cookie session = signUp("a@example.com", "leandro");
        for (int i = 1; i <= 12; i++) {
            highlightId(session, "MOVIE", addToCollection(session, "MOVIE", "m" + i));
        }

        highlight(session, "MOVIE", addToCollection(session, "MOVIE", "m13")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("HIGHLIGHT_LIMIT_REACHED"));
        mvc.perform(get("/api/highlights").cookie(session)).andExpect(jsonPath("$.plan").value("PACK"))
                .andExpect(jsonPath("$.limit").value(12));
        assertThat(countRows("highlights")).isEqualTo(12);
    }

    @Test
    void theLimitIsPerList_BR02() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        for (int i = 1; i <= 4; i++) {
            highlightId(session, "MOVIE", addToCollection(session, "MOVIE", "m" + i));
        }

        highlight(session, "SERIES", addToCollection(session, "SERIES", "s1")).andExpect(status().isCreated());
    }

    // --- other rules ---

    @Test
    void sameItemTwiceInOneListIsRefusedButOneItemMayBeInTwoLists_BR07() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long item = addToCollection(session, "MOVIE", "m1");
        highlightId(session, "MOVIE", item);

        highlight(session, "MOVIE", item).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_HIGHLIGHTED"));
        highlight(session, null, item).andExpect(status().isCreated());
        highlight(session, null, item).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_HIGHLIGHTED"));
    }

    @Test
    void aCategoryListRefusesAnItemOfAnotherCategory() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long book = addToCollection(session, "BOOK", "b1");

        highlight(session, "MOVIE", book).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CATEGORY_MISMATCH"));
    }

    @Test
    void aHiddenItemCannotBeHighlighted() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long item = addToCollection(session, "MOVIE", "m1");
        send(patch("/api/collection/" + item), session, "{\"hidden\":true}").andExpect(status().isOk());

        highlight(session, "MOVIE", item).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ITEM_HIDDEN"));
    }

    @Test
    void anItemThatIsNotInTheCollectionIsNotFound() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        highlight(session, "MOVIE", 987654).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_IN_COLLECTION"));
    }

    // --- favorites (FR-11) ---

    @Test
    void favoritesMixCategoriesAndKeepTheirOwnOrderAndLimit_FR11() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long movie = addToCollection(session, "MOVIE", "m1");
        long series = addToCollection(session, "SERIES", "s1");
        long book = addToCollection(session, "BOOK", "b1");
        long movie2 = addToCollection(session, "MOVIE", "m2");
        long movie3 = addToCollection(session, "MOVIE", "m3");
        long a = highlightId(session, null, movie);
        long b = highlightId(session, null, series);
        long c = highlightId(session, null, book);
        highlightId(session, null, movie2);

        highlight(session, null, movie3).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("HIGHLIGHT_LIMIT_REACHED"));
        assertThat(titlesOf(session, null)).containsExactly("Title m1", "Title s1", "Title b1", "Title m2");
        // a favorites list does not count against the movie list
        highlight(session, "MOVIE", movie3).andExpect(status().isCreated());

        long d = jdbc.queryForObject("select id from highlights where position = 3 and list_id = "
                + "(select id from highlight_lists where category is null)", Long.class);
        reorder(session, null, d, c, b, a).andExpect(status().isOk()).andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.items[0].id").value(d));
        assertThat(titlesOf(session, null)).containsExactly("Title m2", "Title b1", "Title s1", "Title m1");
    }

    @Test
    void thereIsOnlyOneFavoritesListPerProfile_nullsAreNotDistinct() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        highlightId(session, null, addToCollection(session, "MOVIE", "m1"));
        long profileId = jdbc.queryForObject("select profile_id from highlight_lists", Long.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("insert into highlight_lists (profile_id, category) values (?, null)", profileId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("insert into highlight_lists (profile_id, category) values (?, 'MOVIE')", profileId);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("insert into highlight_lists (profile_id, category) values (?, 'MOVIE')", profileId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- reorder ---

    @Test
    void reorderPersistsTheNewOrder() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long one = highlightId(session, "MOVIE", addToCollection(session, "MOVIE", "m1"));
        long two = highlightId(session, "MOVIE", addToCollection(session, "MOVIE", "m2"));
        long three = highlightId(session, "MOVIE", addToCollection(session, "MOVIE", "m3"));

        reorder(session, "MOVIE", three, one, two).andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("MOVIE"))
                .andExpect(jsonPath("$.items[0].position").value(0)).andExpect(jsonPath("$.items[0].id").value(three));

        assertThat(titlesOf(session, "MOVIE")).containsExactly("Title m3", "Title m1", "Title m2");
        List<Integer> positions = jdbc.queryForList("select position from highlights order by position", Integer.class);
        assertThat(positions).containsExactly(0, 1, 2);
    }

    @Test
    void reorderRefusesAnythingButTheExactCurrentSet() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long one = highlightId(session, "MOVIE", addToCollection(session, "MOVIE", "m1"));
        long two = highlightId(session, "MOVIE", addToCollection(session, "MOVIE", "m2"));

        reorder(session, "MOVIE", one).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_ORDER"));
        reorder(session, "MOVIE", one, two, 999999).andExpect(status().isBadRequest());
        reorder(session, "MOVIE", one, one).andExpect(status().isBadRequest());
        reorder(session, "SERIES", one, two).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_ORDER"));
        reorder(session, "MOVIE").andExpect(status().isBadRequest());
        assertThat(titlesOf(session, "MOVIE")).containsExactly("Title m1", "Title m2");
    }

    @Test
    void reorderingAListThatDoesNotExistYetOnlyAcceptsTheEmptyOrder() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        reorder(session, "MOVIE").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        reorder(session, "MOVIE", 1).andExpect(status().isBadRequest());
    }

    @Test
    void inputsAreValidated() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        send(post("/api/highlights"), session, "{\"category\":\"PODCAST\",\"collectionItemId\":1}").andExpect(status().isBadRequest());
        send(post("/api/highlights"), session, "{\"category\":\"MOVIE\"}").andExpect(status().isBadRequest());
        send(post("/api/highlights"), session, "{\"category\":\"MOVIE\",\"collectionItemId\":-1}").andExpect(status().isBadRequest());
        send(put("/api/highlights/order"), session, "{\"category\":\"MOVIE\"}").andExpect(status().isBadRequest());
        send(put("/api/highlights/order"), session, "{\"category\":\"MOVIE\",\"ids\":[null]}").andExpect(status().isBadRequest());
        send(put("/api/highlights/order"), session, "{\"category\":\"MOVIE\",\"ids\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,"
                + "21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,"
                + "61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101]}")
                .andExpect(status().isBadRequest());
    }

    // --- the collection and the highlights ---

    @Test
    void hidingACollectionItemRemovesItFromEveryListAndClosesTheGap() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long one = addToCollection(session, "MOVIE", "m1");
        long two = addToCollection(session, "MOVIE", "m2");
        long three = addToCollection(session, "MOVIE", "m3");
        for (long item : new long[] {one, two, three}) {
            highlightId(session, "MOVIE", item);
        }
        highlightId(session, null, two);

        send(patch("/api/collection/" + two), session, "{\"hidden\":true}").andExpect(status().isOk());

        assertThat(titlesOf(session, "MOVIE")).containsExactly("Title m1", "Title m3");
        assertThat(titlesOf(session, null)).isEmpty();
        assertThat(jdbc.queryForList("select position from highlights order by position", Integer.class)).containsExactly(0, 1);
        // showing it again does not bring the highlight back
        send(patch("/api/collection/" + two), session, "{\"hidden\":false}").andExpect(status().isOk());
        assertThat(titlesOf(session, "MOVIE")).containsExactly("Title m1", "Title m3");
    }

    @Test
    void deletingACollectionItemRemovesItsHighlights() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long one = addToCollection(session, "MOVIE", "m1");
        long two = addToCollection(session, "MOVIE", "m2");
        highlightId(session, "MOVIE", one);
        highlightId(session, "MOVIE", two);
        highlightId(session, null, one);

        send(delete("/api/collection/" + one), session, "").andExpect(status().isNoContent());

        assertThat(titlesOf(session, "MOVIE")).containsExactly("Title m2");
        assertThat(titlesOf(session, null)).isEmpty();
        assertThat(countRows("highlights")).isEqualTo(1);
        assertThat(jdbc.queryForList("select position from highlights", Integer.class)).containsExactly(0);
    }

    @Test
    void deletingTheAccountRemovesListsAndHighlights() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        highlightId(session, "MOVIE", addToCollection(session, "MOVIE", "m1"));

        jdbc.update("delete from users");

        assertThat(countRows("highlight_lists")).isZero();
        assertThat(countRows("highlights")).isZero();
    }

    // --- authorization ---

    @Test
    void anonymousVisitorsAreRejectedOnEveryEndpoint() throws Exception {
        mvc.perform(get("/api/highlights")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/highlights").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"MOVIE\",\"collectionItemId\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/highlights/1").with(RealCsrf.token(mvc))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/highlights/order").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"MOVIE\",\"ids\":[]}")).andExpect(status().isUnauthorized());
    }

    @Test
    void changesWithoutTheCsrfTokenAreRejected() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long item = addToCollection(session, "MOVIE", "m1");

        mvc.perform(post("/api/highlights").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"MOVIE\",\"collectionItemId\":%d}".formatted(item))).andExpect(status().isForbidden());
        mvc.perform(put("/api/highlights/order").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"MOVIE\",\"ids\":[]}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/highlights/1").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void anotherUsersItemAndHighlightLookLikeMissingOnes() throws Exception {
        Cookie owner = signUp("a@example.com", "leandro");
        Cookie other = signUp("b@example.com", "maria");
        long ownersItem = addToCollection(owner, "MOVIE", "m1");
        long ownersHighlight = highlightId(owner, "MOVIE", ownersItem);
        long othersItem = addToCollection(other, "MOVIE", "m2");

        highlight(other, "MOVIE", ownersItem).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_IN_COLLECTION"));
        send(delete("/api/highlights/" + ownersHighlight), other, "").andExpect(status().isNotFound());
        reorder(other, "MOVIE", ownersHighlight).andExpect(status().isBadRequest());
        // the other user's overview never shows the owner's lists
        assertThat(titlesOf(other, "MOVIE")).isEmpty();
        assertThat(titlesOf(owner, "MOVIE")).containsExactly("Title m1");
        // and the other user's own item is theirs only
        highlight(owner, "MOVIE", othersItem).andExpect(status().isNotFound());
        assertThat(countRows("highlights")).isEqualTo(1);
    }
}
