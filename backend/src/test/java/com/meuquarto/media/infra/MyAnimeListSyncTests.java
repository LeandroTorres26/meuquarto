package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real {@link MyAnimeListProvider} through the whole stack against a real PostgreSQL, together with the
 * real {@link AniListProvider}: link, sync, unlink, and an account linked on both platforms (ADR 0005, ADR 0023,
 * BR-04, BR-10, FR-16). Only the two origins and the client id are replaced, by local stub servers; the stubs
 * are test-only constructors, never configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MyAnimeListSyncTests.Stubs.class})
class MyAnimeListSyncTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    /** Random per run: nothing in the repository looks like a credential. */
    private static final String CLIENT_ID = UUID.randomUUID().toString().replace("-", "");
    private static final MyAnimeListStub MAL = new MyAnimeListStub(CLIENT_ID);
    private static final AniListStub ANI = new AniListStub();
    private static final String RECENT = "2026-10-30T12:00:00+00:00";

    @TestConfiguration
    static class Stubs {

        /** First in both registries, ahead of the production bean that points at the real host. */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        AniListProvider stubbedAniList() {
            return new AniListProvider(HttpCatalogProvider.newClient(), Clock.systemUTC(), Duration.ofMinutes(10),
                    ANI.uri(), Duration.ofSeconds(2));
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ActivityProvider stubbedMyAnimeList(@Qualifier("stubbedAniList") AniListProvider anilist) {
            return new MyAnimeListProvider(HttpCatalogProvider.newClient(), MAL.uri(), CLIENT_ID, anilist,
                    Duration.ofSeconds(2), Duration.ZERO);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshStubs() {
        MAL.reset();
        ANI.reset();
        // MAL 52991 and MAL 1 are works the AniList fixture list also has (154587 and 139630); MAL 9000 is new.
        MAL.user("ana_l", List.of(
                MyAnimeListStub.entry(52991, "watching", false, false, "2026-10-29", null, RECENT),
                MyAnimeListStub.entry(1, "completed", false, false, null, "2025-05-24", RECENT),
                MyAnimeListStub.entry(9000, "completed", false, false, null, "2025-01-10", RECENT),
                MyAnimeListStub.anime(2, "plan_to_watch", RECENT),
                MyAnimeListStub.anime(3, "dropped", RECENT),
                MyAnimeListStub.anime(404, "completed", RECENT)), // AniList has no work with this MAL id
                List.of(MyAnimeListStub.manga(21, "reading", RECENT)));
        ANI.malToAniList.put("ANIME", Map.of(52991L, 154587L, 1L, 139630L, 9000L, 9000L, 2L, 7002L, 3L, 7003L));
        ANI.malToAniList.put("MANGA", Map.of(21L, 30002L));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
        jdbc.update("delete from media_items where canonical_provider = 'ANILIST'");
    }

    @AfterAll
    static void stopStubs() {
        MAL.close();
        ANI.close();
    }

    @Test
    void aManualSyncBringsTheAniListWorksOfTheListsAndNothingFromMyAnimeList() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "MYANIMELIST", "ana_l");

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.itemsCount").value(4)); // plan, dropped and the unmatched entry are out

        assertThat(jdbc.queryForObject("select username from linked_accounts", String.class)).isEqualTo("ana_l");
        assertThat(jdbc.queryForList("select source from collection_item_sources", String.class))
                .hasSize(4).containsOnly("MYANIMELIST");
        assertThat(jdbc.queryForList(
                "select category || ':' || canonical_provider || ':' || external_id from media_items order by external_id",
                String.class)).containsExactlyInAnyOrder("ANIME:ANILIST:154587", "ANIME:ANILIST:139630",
                "ANIME:ANILIST:9000", "MANGA:ANILIST:30002");
        assertThat(jdbc.queryForObject("select count(*) from synced_entries where activity_type = 'FINISHED'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from synced_entries where activity_type = 'IN_PROGRESS'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select title from media_items where external_id = '9000'", String.class))
                .isEqualTo("Work 9000"); // AniList's title
        assertThat(jdbc.queryForObject("select count(*) from media_items where title like '%MAL title%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from media_items where canonical_provider <> 'ANILIST'", Integer.class))
                .isZero();
    }

    @Test
    void anAccountLinkedOnBothPlatformsAddsEachWorkOnce() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long aniList = link(session, "ANILIST", "ana_l");
        long myAnimeList = link(session, "MYANIMELIST", "ana_l");

        sync(session, aniList).andExpect(status().isOk()).andExpect(jsonPath("$.itemsCount").value(8));
        sync(session, myAnimeList).andExpect(status().isOk()).andExpect(jsonPath("$.itemsCount").value(4));

        // 8 from AniList plus only MAL 9000 (new); Frieren, Hero Academia and the manga were already there
        assertThat(count("collection_items")).isEqualTo(9);
        assertThat(jdbc.queryForObject("select count(*) from media_items where external_id = '154587'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select title from media_items where external_id = '154587'", String.class))
                .isEqualTo("Sousou no Frieren"); // MyAnimeList does not overwrite AniList's metadata
        assertThat(jdbc.queryForList("""
                select s.source from collection_item_sources s join collection_items i on i.id = s.collection_item_id
                join media_items m on m.id = i.media_item_id where m.external_id = '154587' order by s.source""", String.class))
                .containsExactly("ANILIST", "MYANIMELIST");
    }

    @Test
    void theOrderOfTheTwoSyncsDoesNotMatterAndTheCanonicalMetadataWins() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long aniList = link(session, "ANILIST", "ana_l");
        long myAnimeList = link(session, "MYANIMELIST", "ana_l");

        sync(session, myAnimeList).andExpect(status().isOk());
        sync(session, aniList).andExpect(status().isOk());

        assertThat(count("collection_items")).isEqualTo(9);
        assertThat(jdbc.queryForObject("select count(*) from media_items where external_id = '154587'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select title from media_items where external_id = '154587'", String.class))
                .isEqualTo("Sousou no Frieren"); // AniList is the canonical writer and refreshes the row
    }

    @Test
    void anAnimeAddedByHandIsTheSameItemAsTheOneTheSyncBrings() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        mvc.perform(post("/api/collection").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ANIME\",\"externalId\":\"154587\"}"))
                .andExpect(status().is2xxSuccessful());
        long account = link(session, "MYANIMELIST", "ana_l");

        sync(session, account).andExpect(status().isOk());

        assertThat(count("collection_items")).isEqualTo(4); // Frieren once, plus three more
        assertThat(jdbc.queryForObject("select count(*) from media_items where external_id = '154587'", Integer.class)).isEqualTo(1);
    }

    @Test
    void syncingAgainChangesNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "MYANIMELIST", "ana_l");
        sync(session, account).andExpect(status().isOk());
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '1 hour'");

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(count("collection_items")).isEqualTo(4);
        assertThat(count("synced_entries")).isEqualTo(4);
    }

    @Test
    void whatIsBeingWatchedFeedsAgora() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        sync(session, link(session, "MYANIMELIST", "ana_l")).andExpect(status().isOk());

        mvc.perform(get("/api/public/profiles/ana")).andExpect(status().isOk())
                .andExpect(jsonPath("$.now[?(@.category == 'ANIME')].title").value("Work 154587"))
                .andExpect(jsonPath("$.now[?(@.category == 'ANIME')].source").value("MYANIMELIST"));
    }

    @Test
    void unlinkingReleasesWhatOnlyMyAnimeListBrought() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long aniList = link(session, "ANILIST", "ana_l");
        long myAnimeList = link(session, "MYANIMELIST", "ana_l");
        sync(session, aniList).andExpect(status().isOk());
        sync(session, myAnimeList).andExpect(status().isOk());

        mvc.perform(delete("/api/linked-accounts/" + myAnimeList).cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());

        assertThat(count("collection_items")).isEqualTo(8); // MAL 9000 is gone; what AniList also brought stays
        assertThat(jdbc.queryForList("select distinct source from collection_item_sources", String.class))
                .containsExactly("ANILIST");
    }

    @Test
    void linkingKeepsTheCaseAsTypedAndMakesOneRequestToMyAnimeList() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        MAL.user("Ana-L_1", List.of(), List.of());

        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MYANIMELIST\",\"username\":\"  Ana-L_1 \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("MYANIMELIST"))
                .andExpect(jsonPath("$.username").value("Ana-L_1"))
                .andExpect(jsonPath("$.syncs").value(true))
                .andExpect(jsonPath("$.profileUrl").value("https://myanimelist.net/profile/Ana-L_1"));
        assertThat(MAL.listRequests()).hasSize(1);
        assertThat(ANI.requests).isEmpty();
    }

    @Test
    void aPrivateListAndAMissingUserAreBothRefusedAsNotFound() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        MAL.privateLists.add("ana_l");

        link(session, "MYANIMELIST", "ana_l", status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));
        link(session, "MYANIMELIST", "nobody_here", status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));

        assertThat(count("linked_accounts")).isZero();
    }

    @Test
    void whenMyAnimeListCannotAnswerLinkingSaysUnavailableAndStoresNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        MAL.status = 500;

        link(session, "MYANIMELIST", "ana_l", status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(count("linked_accounts")).isZero();
    }

    @Test
    void whenAnAccountGoesPrivateTheSyncFailsWithTheExplainableCodeAndKeepsWhatIsThere() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "MYANIMELIST", "ana_l");
        sync(session, account).andExpect(status().isOk());
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '1 hour'");
        MAL.privateLists.add("ana_l");

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));

        assertThat(count("collection_items")).isEqualTo(4);
    }

    @Test
    void whenAniListIsDownTheSyncFailsForThatAccountAndAppliesNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "MYANIMELIST", "ana_l");
        ANI.status = 429;

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(count("collection_items")).isZero();
    }

    @Test
    void aMaliciousNameIsRefusedBeforeAnyRequestToMyAnimeList() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        for (String hostile : new String[] {"../../etc/passwd", "ana?fields=x", "a", "a".repeat(17), "ana b", "ana.l",
                "x\\\"){ id }", "https://evil.example/x"}) {
            link(session, "MYANIMELIST", hostile, status().isBadRequest());
        }

        assertThat(count("linked_accounts")).isZero();
        assertThat(MAL.requests).isEmpty();
        assertThat(ANI.requests).isEmpty();
    }

    @Test
    void theClientIdNeverLeavesTheHeader() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        sync(session, link(session, "MYANIMELIST", "ana_l")).andExpect(status().isOk());

        assertThat(MAL.requests).isNotEmpty().allSatisfy(r -> {
            assertThat(r.clientIdHeader()).isEqualTo(CLIENT_ID);
            assertThat(r.path() + r.rawQuery()).doesNotContain(CLIENT_ID);
        });
        assertThat(ANI.requests).allSatisfy(r -> assertThat(r.path() + r.body()).doesNotContain(CLIENT_ID));
    }

    // --- authorization (the owner always comes from the session) ---

    @Test
    void anonymousCannotLinkSyncOrListProviders() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        long account = link(ana, "MYANIMELIST", "ana_l");

        mvc.perform(post("/api/linked-accounts").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MYANIMELIST\",\"username\":\"ana_l\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/linked-accounts/" + account + "/sync").with(RealCsrf.token(mvc)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/linked-accounts/providers")).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherUserCannotSyncOrUnlinkSomeoneElsesMyAnimeListAccount() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long account = link(ana, "MYANIMELIST", "ana_l");

        sync(bia, account).andExpect(status().isNotFound());
        mvc.perform(delete("/api/linked-accounts/" + account).cookie(bia).with(RealCsrf.token(mvc)))
                .andExpect(status().isNotFound());

        assertThat(count("linked_accounts")).isEqualTo(1);
        assertThat(count("collection_items")).isZero();
        assertThat(MAL.listRequests()).hasSize(1); // the link check only
    }

    @Test
    void theProvidersListSaysWhichSyncingPlatformsCanBeLinked() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        mvc.perform(get("/api/linked-accounts/providers").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'MYANIMELIST')].available").value(true))
                .andExpect(jsonPath("$[?(@.provider == 'ANILIST')].available").value(true))
                .andExpect(jsonPath("$[?(@.provider == 'BACKLOGGD')]").isEmpty()); // link-only: no provider to ask
    }

    // --- the migration (V16) ---

    @Test
    void v16_theProviderCheckAcceptsMyAnimeListAndStillRejectsUnknownNames() throws Exception {
        signUp("a@example.com", "ana");
        long profileId = jdbc.queryForObject("select id from profiles where slug = 'ana'", Long.class);

        jdbc.update("insert into linked_accounts (profile_id, provider, username) values (?, 'MYANIMELIST', 'ana_l')", profileId);

        assertThat(count("linked_accounts")).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "insert into linked_accounts (profile_id, provider, username) values (?, 'MYSPACE', 'ana_l')", profileId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- helpers ---

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private long link(Cookie session, String provider, String username) throws Exception {
        String body = link(session, provider, username, status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions link(Cookie session, String provider, String username, ResultMatcher expected) throws Exception {
        return mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"%s\",\"username\":\"%s\"}".formatted(provider, username)))
                .andExpect(expected);
    }

    private ResultActions sync(Cookie session, long accountId) throws Exception {
        return mvc.perform(post("/api/linked-accounts/" + accountId + "/sync").cookie(session).with(RealCsrf.token(mvc)));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
