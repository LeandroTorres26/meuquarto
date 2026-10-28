package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The real {@link AniListProvider} through the whole stack against a real PostgreSQL: the same object is
 * the catalog behind the search endpoint and the source behind link and sync (BR-04, BR-10). Only the
 * endpoint is replaced, by a local stub server; the stub is a test-only constructor, never configuration.
 * The class sits in the {@code infra} package to reach that constructor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AniListSyncTests.Stubs.class})
class AniListSyncTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    private static final AniListStub STUB = new AniListStub();

    @TestConfiguration
    static class Stubs {

        /** First in both registries, ahead of the production bean that points at the real host. */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        AniListProvider stubbedAniList() {
            return new AniListProvider(HttpCatalogProvider.newClient(), Clock.systemUTC(), Duration.ofMinutes(10),
                    STUB.uri(), Duration.ofSeconds(2));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshStub() {
        STUB.reset();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
        jdbc.update("delete from media_items where canonical_provider = 'ANILIST'");
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @Test
    void aManualSyncFillsTheCollectionFromTheListsWithTheAniListCanonicalKey() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "Ana_L"); // normalized to lowercase on the way in

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.itemsCount").value(8)); // planning, dropped, adult and custom-list entries skipped

        assertThat(jdbc.queryForObject("select username from linked_accounts", String.class)).isEqualTo("ana_l");
        assertThat(jdbc.queryForList("select source from collection_item_sources", String.class))
                .hasSize(8).containsOnly("ANILIST");
        assertThat(jdbc.queryForList(
                "select category || ':' || canonical_provider || ':' || external_id from media_items order by external_id",
                String.class)).containsExactlyInAnyOrder("ANIME:ANILIST:100", "ANIME:ANILIST:101", "ANIME:ANILIST:139630",
                "ANIME:ANILIST:154587", "ANIME:ANILIST:200", "ANIME:ANILIST:300", "MANGA:ANILIST:30002",
                "MANGA:ANILIST:30013");
        assertThat(jdbc.queryForObject("select count(*) from synced_entries where activity_type = 'FINISHED'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from synced_entries where activity_type = 'IN_PROGRESS'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select title from media_items where external_id = '154587'", String.class))
                .isEqualTo("Sousou no Frieren"); // romaji
        assertThat(jdbc.queryForObject("select image_url from media_items where external_id = '154587'", String.class))
                .startsWith("https://s4.anilist.co/");
        assertThat(jdbc.queryForObject("select image_url from media_items where external_id = '100'", String.class)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from media_items where external_id in ('400','500','666','777')",
                Integer.class)).isZero();
    }

    @Test
    void br04_anAnimeAddedByHandAfterTheSyncIsTheSameItem() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        sync(session, account).andExpect(status().isOk());
        int itemsBefore = count("collection_items");
        int mediaBefore = count("media_items");

        mvc.perform(post("/api/collection").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ANIME\",\"externalId\":\"154587\"}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(count("collection_items")).isEqualTo(itemsBefore);
        assertThat(count("media_items")).isEqualTo(mediaBefore);
        assertThat(jdbc.queryForList("""
                select s.source from collection_item_sources s join collection_items i on i.id = s.collection_item_id
                join media_items m on m.id = i.media_item_id where m.external_id = '154587' order by s.source""", String.class))
                .contains("ANILIST");
    }

    @Test
    void br04_anAnimeAddedByHandBeforeTheSyncIsNotDuplicatedBySyncing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        mvc.perform(post("/api/collection").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ANIME\",\"externalId\":\"154587\"}"))
                .andExpect(status().is2xxSuccessful());
        long account = link(session, "ana_l");

        sync(session, account).andExpect(status().isOk());

        assertThat(count("collection_items")).isEqualTo(8); // Frieren once, plus the other seven
        assertThat(jdbc.queryForObject("select count(*) from media_items where external_id = '154587'", Integer.class)).isEqualTo(1);
    }

    @Test
    void searchEndpointServesAnimeAndMangaWithoutAnyKey() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "ANIME").param("q", "bebop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.results.length()").value(4)) // the adult and the broken ones are gone
                .andExpect(jsonPath("$.results[0].provider").value("ANILIST"))
                .andExpect(jsonPath("$.results[0].category").value("ANIME"))
                .andExpect(jsonPath("$.results[0].externalId").value("1"))
                .andExpect(jsonPath("$.results[0].title").value("Cowboy Bebop"));
        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MANGA").param("q", "berserk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.results[0].externalId").value("30002"));
    }

    @Test
    void syncingAgainChangesNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        sync(session, account).andExpect(status().isOk());
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '1 hour'");

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(count("collection_items")).isEqualTo(8);
        assertThat(count("synced_entries")).isEqualTo(8);
    }

    @Test
    void whenAniListIsRateLimitingTheSyncFailsForThatAccountOnly() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        STUB.status = 429;

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(count("collection_items")).isZero();
    }

    @Test
    void linkingAnAccountThatDoesNotExistIsRefused() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"ANILIST\",\"username\":\"nobody_here\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));
        assertThat(count("linked_accounts")).isZero();
    }

    @Test
    void aMaliciousUsernameIsRefusedBeforeAnyRequestToAniList() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"ANILIST\",\"username\":\"x\\\"){ id }\"}"))
                .andExpect(status().isBadRequest());
        assertThat(count("linked_accounts")).isZero();
        assertThat(STUB.requests).isEmpty();
    }

    // --- helpers ---

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private long link(Cookie session, String username) throws Exception {
        String body = mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"ANILIST\",\"username\":\"%s\"}".formatted(username)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions sync(Cookie session, long accountId) throws Exception {
        return mvc.perform(post("/api/linked-accounts/" + accountId + "/sync").cookie(session).with(RealCsrf.token(mvc)));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
