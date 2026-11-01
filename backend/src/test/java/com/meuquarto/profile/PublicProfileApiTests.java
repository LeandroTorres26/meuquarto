package com.meuquarto.profile;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The public read API (S4, S17, S18) against a real PostgreSQL. Each test uses its own slug because
 * the read model is cached by slug for the life of the context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PublicProfileApiTests.Fakes.class})
class PublicProfileApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";

    /**
     * Calls here are counted: public reads must never reach a catalog. Ids look like {@code name_1999};
     * the year is optional. The id is TMDB so that movies get a link.
     */
    private static final class CountingCatalog implements CatalogProvider {

        static final AtomicInteger CALLS = new AtomicInteger();
        private static final Pattern ID = Pattern.compile("([a-z0-9-]+?)(?:_(\\d{4}))?");

        @Override
        public String catalogName() {
            return "TMDB";
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
            CALLS.incrementAndGet();
            return List.of();
        }

        @Override
        public Optional<CatalogEntry> fetch(MediaCategory category, String externalId) {
            CALLS.incrementAndGet();
            Matcher m = ID.matcher(externalId);
            if (!m.matches()) {
                return Optional.empty();
            }
            Integer year = m.group(2) == null ? null : Integer.valueOf(m.group(2));
            return Optional.of(new CatalogEntry(category, "TMDB", externalId, m.group(1), year,
                    "https://img.example/" + externalId + ".jpg", false));
        }
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        CatalogRegistry fakeRegistry() {
            return new CatalogRegistry(List.of(new CountingCatalog()));
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

    private Cookie signUp(String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s@example.com\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(slug, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private long add(Cookie session, String category, String externalId) throws Exception {
        String body = send(post("/api/collection"), session,
                "{\"category\":\"%s\",\"externalId\":\"%s\"}".formatted(category, externalId))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void highlight(Cookie session, String category, long itemId) throws Exception {
        send(post("/api/highlights"), session, "{\"category\":%s,\"collectionItemId\":%d}"
                .formatted(category == null ? "null" : "\"" + category + "\"", itemId)).andExpect(status().isCreated());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie session, String body) throws Exception {
        return mvc.perform(request.cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void markAdult(String externalId) {
        jdbc.update("update media_items set adult = true where external_id = ?", externalId);
    }

    private String profileJson(String slug) throws Exception {
        return mvc.perform(get("/api/public/profiles/" + slug)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions listing(String slug, String query) throws Exception {
        return mvc.perform(get("/api/public/profiles/" + slug + "/collection?" + query));
    }

    private ResultActions search(String slug, String text, String sort) throws Exception {
        return mvc.perform(get("/api/public/profiles/" + slug + "/collection")
                .param("category", "MOVIE").param("q", text).param("sort", sort));
    }

    private static List<String> titles(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.items[*].title");
    }

    // --- S4: the read model ---

    @Test
    void homeShowsFavoritesThenCategoriesWithHighlightsInTheOwnersOrderAndNotes() throws Exception {
        Cookie owner = signUp("homeowner");
        long heat = add(owner, "MOVIE", "heat_1995");
        long alien = add(owner, "MOVIE", "alien_1979");
        long ran = add(owner, "MOVIE", "ran_1985");
        long dune = add(owner, "BOOK", "dune_1965");
        send(patch("/api/collection/" + alien), owner, "{\"note\":\"  melhor que o segundo  \"}").andExpect(status().isOk());
        // highlighted out of collection order: ran, then heat; alien is not highlighted
        highlight(owner, "MOVIE", ran);
        highlight(owner, "MOVIE", heat);
        highlight(owner, null, dune);
        highlight(owner, null, alien);

        mvc.perform(get("/api/public/profiles/HomeOwner")) // case-insensitive, like the sign-up
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("homeowner"))
                .andExpect(jsonPath("$.displayName").value("homeowner"))
                .andExpect(jsonPath("$.favorites.length()").value(2))
                .andExpect(jsonPath("$.favorites[0].title").value("dune"))
                .andExpect(jsonPath("$.favorites[0].category").value("BOOK"))
                .andExpect(jsonPath("$.favorites[0].year").value(1965))
                .andExpect(jsonPath("$.favorites[0].imageUrl").value("https://img.example/dune_1965.jpg"))
                .andExpect(jsonPath("$.favorites[1].note").value("melhor que o segundo"))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].category").value("MOVIE"))
                .andExpect(jsonPath("$.categories[0].total").value(3))
                .andExpect(jsonPath("$.categories[0].mostRecent").value(false))
                .andExpect(jsonPath("$.categories[0].items[0].ref").value(ran))
                .andExpect(jsonPath("$.categories[0].items[0].title").value("ran"))
                .andExpect(jsonPath("$.categories[0].items[1].title").value("heat"))
                .andExpect(jsonPath("$.categories[0].items.length()").value(2))
                // BOOK has one item and no book highlight (dune is only in favorites): falls back to recent
                .andExpect(jsonPath("$.categories[1].category").value("BOOK"))
                .andExpect(jsonPath("$.categories[1].mostRecent").value(true))
                .andExpect(jsonPath("$.categories[1].items[0].title").value("dune"));
    }

    @Test
    void categoryWithoutHighlightsShowsTheFourMostRecentVisibleItems() throws Exception {
        Cookie owner = signUp("recentfallback");
        for (String id : List.of("a1", "a2", "a3", "a4", "a5", "a6")) {
            add(owner, "SERIES", id);
        }
        // adding a1 again makes it the most recent (BR-07: same item, fresh activity)
        add(owner, "SERIES", "a1");

        String json = profileJson("recentfallback");
        assertThat(JsonPath.<List<String>>read(json, "$.categories[0].items[*].title"))
                .containsExactly("a1", "a6", "a5", "a4");
        assertThat(JsonPath.<Boolean>read(json, "$.categories[0].mostRecent")).isTrue();
        assertThat(JsonPath.<Integer>read(json, "$.categories[0].total")).isEqualTo(6);
        assertThat(JsonPath.<List<Object>>read(json, "$.favorites")).isEmpty();
    }

    @Test
    void emptyProfileIsStillFoundAndShowsNothing() throws Exception {
        signUp("emptyroom");
        mvc.perform(get("/api/public/profiles/emptyroom")).andExpect(status().isOk())
                .andExpect(jsonPath("$.favorites.length()").value(0))
                .andExpect(jsonPath("$.categories.length()").value(0));
    }

    @Test
    void hiddenAndAdultItemsAreNeverShownNorCounted() throws Exception {
        Cookie owner = signUp("filtered");
        long shown = add(owner, "MOVIE", "shown_2001");
        long hidden = add(owner, "MOVIE", "hidden_2002");
        add(owner, "MOVIE", "adult_2003");
        highlight(owner, "MOVIE", hidden);
        highlight(owner, "MOVIE", shown);
        send(patch("/api/collection/" + hidden), owner, "{\"hidden\":true}").andExpect(status().isOk());
        markAdult("adult_2003");

        String json = profileJson("filtered");
        assertThat(JsonPath.<List<String>>read(json, "$.categories[0].items[*].title")).containsExactly("shown");
        assertThat(JsonPath.<Integer>read(json, "$.categories[0].total")).isEqualTo(1);
        assertThat(json).doesNotContain("hidden").doesNotContain("adult");
    }

    @Test
    void theHomeNeverExposesTheAccount() throws Exception {
        Cookie owner = signUp("privacy");
        add(owner, "MOVIE", "heat_1995");

        String json = profileJson("privacy");
        assertThat(json).doesNotContain("@").doesNotContain("userId").doesNotContain("email")
                .doesNotContain("hidden").doesNotContain("sources");
    }

    // --- 404s ---

    @Test
    void unknownReservedInvalidAndOversizedSlugsAllAnswerTheSameNotFound() throws Exception {
        for (String slug : List.of("nobody-here", "admin", "api", "ab", "bad_slug", "x".repeat(200), "ação")) {
            mvc.perform(get("/api/public/profiles/" + slug)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PROFILE_NOT_FOUND"));
        }
        mvc.perform(get("/api/public/profiles/nobody-here/items/1")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROFILE_NOT_FOUND"));
        listing("nobody-here", "category=MOVIE").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROFILE_NOT_FOUND"));
    }

    // --- S17: item details ---

    @Test
    void itemDetailShowsNoteOriginAndTheCatalogLink() throws Exception {
        Cookie owner = signUp("detail");
        long id = add(owner, "MOVIE", "heat_1995");
        long book = add(owner, "BOOK", "dune_1965");
        send(patch("/api/collection/" + id), owner, "{\"note\":\"visto no cinema\"}").andExpect(status().isOk());

        mvc.perform(get("/api/public/profiles/detail/items/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.item.ref").value(id))
                .andExpect(jsonPath("$.item.title").value("heat"))
                .andExpect(jsonPath("$.item.note").value("visto no cinema"))
                .andExpect(jsonPath("$.origin.length()").value(1))
                .andExpect(jsonPath("$.origin[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.origin[0].firstSeenAt").exists())
                .andExpect(jsonPath("$.links[0].provider").value("TMDB"))
                .andExpect(jsonPath("$.links[0].url").value("https://www.themoviedb.org/movie/heat_1995"));
        // TMDB has no book pages: no link rather than a made-up one
        mvc.perform(get("/api/public/profiles/detail/items/" + book)).andExpect(status().isOk())
                .andExpect(jsonPath("$.links.length()").value(0));
    }

    @Test
    void itemDetailIsNotFoundForHiddenAdultOtherProfileAndJunkRefs() throws Exception {
        Cookie owner = signUp("detailowner");
        Cookie other = signUp("detailother");
        long hidden = add(owner, "MOVIE", "hidden_2002");
        long adult = add(owner, "MOVIE", "adult_2003");
        long mine = add(owner, "MOVIE", "heat_1995");
        long theirs = add(other, "MOVIE", "ran_1985");
        send(patch("/api/collection/" + hidden), owner, "{\"hidden\":true}").andExpect(status().isOk());
        markAdult("adult_2003");

        for (String ref : List.of(Long.toString(hidden), Long.toString(adult), Long.toString(theirs), "999999999",
                "abc", "-1", "1.5", "99999999999999999999")) {
            mvc.perform(get("/api/public/profiles/detailowner/items/" + ref)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ITEM_NOT_FOUND"));
        }
        mvc.perform(get("/api/public/profiles/detailowner/items/" + mine)).andExpect(status().isOk());
        // the same ref under the right profile works, under the wrong one it does not exist
        mvc.perform(get("/api/public/profiles/detailother/items/" + theirs)).andExpect(status().isOk());
        mvc.perform(get("/api/public/profiles/detailother/items/" + mine)).andExpect(status().isNotFound());
    }

    // --- S18: category listing ---

    @Test
    void listingPaginatesOverOneHundredItemsWithoutRepeatsOrGaps() throws Exception {
        Cookie owner = signUp("bigshelf");
        for (int i = 0; i < 105; i++) {
            add(owner, "MOVIE", "film%03d_%d".formatted(i, 1900 + i));
        }
        add(owner, "BOOK", "other-category");

        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int page = 0; page < 5; page++) {
            String json = listing("bigshelf", "category=MOVIE&size=24&page=" + page).andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(105)).andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(24)).andReturn().getResponse().getContentAsString();
            List<Integer> refs = JsonPath.read(json, "$.items[*].ref");
            assertThat(refs).hasSize(page < 4 ? 24 : 9);
            refs.forEach(ref -> assertThat(seen.add(ref.longValue())).isTrue());
        }
        assertThat(seen).hasSize(105);
        // default sort is most recent first: the last one added comes first
        listing("bigshelf", "category=MOVIE").andExpect(jsonPath("$.items[0].title").value("film104"));
        // past the end: an empty page, same total
        listing("bigshelf", "category=MOVIE&page=9").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.total").value(105));
    }

    @Test
    void listingSortsByTitleAndYearAndSearchesByTitle() throws Exception {
        Cookie owner = signUp("sorting");
        add(owner, "MOVIE", "beta_1990");
        add(owner, "MOVIE", "alpha_2010");
        add(owner, "MOVIE", "gamma");
        add(owner, "MOVIE", "alphabet_2000");

        assertThat(titles(listing("sorting", "category=MOVIE&sort=recent"))).containsExactly("alphabet", "gamma", "alpha", "beta");
        assertThat(titles(listing("sorting", "category=MOVIE&sort=title"))).containsExactly("alpha", "alphabet", "beta", "gamma");
        // newest first, no year last
        assertThat(titles(listing("sorting", "category=MOVIE&sort=year"))).containsExactly("alpha", "alphabet", "beta", "gamma");
        assertThat(titles(search("sorting", " ALPHA ", "title"))).containsExactly("alpha", "alphabet");
        search("sorting", " ALPHA ", "title").andExpect(jsonPath("$.total").value(2));
        listing("sorting", "category=MOVIE&q=zzz").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.total").value(0));
        // LIKE wildcards in the text are plain characters
        search("sorting", "%", "recent").andExpect(jsonPath("$.total").value(0));
        search("sorting", "_lpha", "recent").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void listingFlagsHighlightsAndHidesHiddenAndAdultItems() throws Exception {
        Cookie owner = signUp("flags");
        long a = add(owner, "MOVIE", "a-film");
        add(owner, "MOVIE", "b-film");
        long hidden = add(owner, "MOVIE", "c-film");
        add(owner, "MOVIE", "d-film");
        add(owner, "BOOK", "a-book");
        highlight(owner, "MOVIE", a);
        send(patch("/api/collection/" + hidden), owner, "{\"hidden\":true}").andExpect(status().isOk());
        markAdult("d-film");

        listing("flags", "category=MOVIE&sort=title").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].title").value("a-film"))
                .andExpect(jsonPath("$.items[0].highlighted").value(true))
                .andExpect(jsonPath("$.items[1].title").value("b-film"))
                .andExpect(jsonPath("$.items[1].highlighted").value(false));
        // a favorite of another category does not mark the listing; and an empty category is just empty
        listing("flags", "category=GAME").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void listingRejectsBadParametersWithoutTouchingTheData() throws Exception {
        signUp("params");
        listing("params", "category=MOVIE&size=49").andExpect(status().isBadRequest());
        listing("params", "category=MOVIE&size=0").andExpect(status().isBadRequest());
        listing("params", "category=MOVIE&page=-1").andExpect(status().isBadRequest());
        listing("params", "category=MOVIE&sort=random").andExpect(status().isBadRequest());
        listing("params", "category=MOVIE&q=" + "x".repeat(101)).andExpect(status().isBadRequest());
        listing("params", "category=NOPE").andExpect(status().isBadRequest());
        listing("params", "size=10").andExpect(status().isBadRequest());
        listing("params", "category=MOVIE&size=48&q=" + "x".repeat(100)).andExpect(status().isOk());
    }

    // --- cache (NFR-02) ---

    @Test
    void homeIsServedFromTheCacheUntilTheOwnerChangesSomething() throws Exception {
        Cookie owner = signUp("cached");
        long id = add(owner, "MOVIE", "heat_1995");
        send(patch("/api/collection/" + id), owner, "{\"note\":\"first\"}").andExpect(status().isOk());
        assertThat(JsonPath.<String>read(profileJson("cached"), "$.categories[0].items[0].note")).isEqualTo("first");

        // behind the application's back: only a cache hit can still answer "first"
        jdbc.update("update collection_items set note = 'sneaky' where id = ?", id);
        assertThat(JsonPath.<String>read(profileJson("cached"), "$.categories[0].items[0].note")).isEqualTo("first");

        // an owner's change goes through the use case, which evicts
        send(patch("/api/collection/" + id), owner, "{\"note\":\"second\"}").andExpect(status().isOk());
        assertThat(JsonPath.<String>read(profileJson("cached"), "$.categories[0].items[0].note")).isEqualTo("second");

        // so do highlights, adding a work and hiding one
        long other = add(owner, "MOVIE", "ran_1985");
        assertThat(JsonPath.<Integer>read(profileJson("cached"), "$.categories[0].total")).isEqualTo(2);
        highlight(owner, "MOVIE", other);
        assertThat(JsonPath.<List<String>>read(profileJson("cached"), "$.categories[0].items[*].title")).containsExactly("ran");
        send(patch("/api/collection/" + other), owner, "{\"hidden\":true}").andExpect(status().isOk());
        String json = profileJson("cached");
        assertThat(JsonPath.<Integer>read(json, "$.categories[0].total")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(json, "$.categories[0].items[*].title")).containsExactly("heat");
    }

    // --- no external calls, no session ---

    @Test
    void publicReadsNeverCallACatalogProvider() throws Exception {
        Cookie owner = signUp("offline");
        long id = add(owner, "MOVIE", "heat_1995");
        highlight(owner, "MOVIE", id);
        CountingCatalog.CALLS.set(0);

        profileJson("offline");
        mvc.perform(get("/api/public/profiles/offline/items/" + id)).andExpect(status().isOk());
        listing("offline", "category=MOVIE&q=he&sort=title").andExpect(status().isOk());

        assertThat(CountingCatalog.CALLS.get()).isZero();
    }

    @Test
    void anonymousReadsCreateNoSession() throws Exception {
        Cookie owner = signUp("nosession");
        long id = add(owner, "MOVIE", "heat_1995");
        long before = jdbc.queryForObject("select count(*) from spring_session", Long.class);

        for (String path : List.of("/api/public/profiles/nosession", "/api/public/profiles/nosession/items/" + id,
                "/api/public/profiles/nosession/collection?category=MOVIE", "/api/public/profiles/missing")) {
            // The CSRF token cookie may appear (it is not a session); a SESSION cookie never does.
            mvc.perform(get(path)).andExpect(result ->
                    assertThat(result.getResponse().getHeaders("Set-Cookie")).noneMatch(c -> c.startsWith("SESSION")));
        }
        assertThat(jdbc.queryForObject("select count(*) from spring_session", Long.class)).isEqualTo(before);
    }

    @Test
    void publicRoutesAreReadOnly() throws Exception {
        signUp("readonly");
        mvc.perform(post("/api/public/profiles/readonly").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/public/profiles/readonly").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void responsesKeepTheSecurityHeaders() throws Exception {
        signUp("headers");
        mvc.perform(get("/api/public/profiles/headers")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }
}
