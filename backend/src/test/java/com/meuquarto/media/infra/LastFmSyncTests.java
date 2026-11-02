package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * The real {@link LastFmProvider} through the whole stack against a real PostgreSQL: link an account,
 * run a manual sync and check the collection and the "Agora" block (BR-04, BR-10, FR-15). Only the origin
 * and the key are replaced, by a local stub server; the stub is a test-only constructor, never configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LastFmSyncTests.Stubs.class})
class LastFmSyncTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    /** Random per run: nothing in the repository looks like a credential. */
    private static final String KEY = UUID.randomUUID().toString().replace("-", "");
    private static final LastFmStub STUB = new LastFmStub(KEY);
    private static final long NEWEST = System.currentTimeMillis() / 1000 - 3600;

    @TestConfiguration
    static class Stubs {

        /** First in the registry, ahead of the production bean that points at the real host (and has no key). */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ActivityProvider stubbedLastFm() {
            return new LastFmProvider(HttpCatalogProvider.newClient(), STUB.uri(), KEY, Clock.systemUTC(),
                    Duration.ofSeconds(2), Duration.ZERO, LastFmProvider.MIN_SCROBBLES);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetStub() {
        STUB.reset();
        STUB.answer = LastFmStub.pageOf(all(
                List.of(LastFmStub.nowPlaying("Radiohead", "In Rainbows", LastFmStub.COVER)),
                LastFmStub.plays("Björk", "Homogenic", 11, NEWEST, LastFmStub.COVER),
                LastFmStub.plays("Daft Punk", "Discovery", 10, NEWEST - 3 * 86_400L, LastFmStub.PLACEHOLDER),
                LastFmStub.plays("Nirvana", "Nevermind", 4, NEWEST - 5 * 86_400L, LastFmStub.COVER),
                LastFmStub.plays("Radiohead", "", 20, NEWEST, null)));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @SafeVarargs
    private static List<java.util.Map<String, Object>> all(List<java.util.Map<String, Object>>... groups) {
        List<java.util.Map<String, Object>> tracks = new java.util.ArrayList<>();
        for (List<java.util.Map<String, Object>> group : groups) {
            tracks.addAll(group);
        }
        return tracks;
    }

    @Test
    void aManualSyncAddsTheAlbumsPlayedEnoughAndWhatIsPlayingNowButNeverIndividualPlays() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        STUB.requests.clear(); // linking asked for one scrobble, to check that the account exists

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.itemsCount").value(3));

        assertThat(jdbc.queryForList(
                "select title from media_items where category = 'MUSIC' and canonical_provider = 'LASTFM' order by title",
                String.class)).containsExactly("Discovery - Daft Punk", "Homogenic - Björk", "In Rainbows - Radiohead");
        assertThat(jdbc.queryForObject("select count(*) from collection_items", Integer.class)).isEqualTo(3); // not 40 plays
        assertThat(jdbc.queryForList("select source from collection_item_sources", String.class)).hasSize(3).containsOnly("LASTFM");
        assertThat(jdbc.queryForObject("select count(*) from synced_entries where activity_type = 'LOVED'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from synced_entries where activity_type = 'IN_PROGRESS'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select image_url from media_items where title like 'Homogenic%'", String.class)).isEqualTo(LastFmStub.COVER);
        assertThat(jdbc.queryForObject("select image_url from media_items where title like 'Discovery%'", String.class)).isNull(); // star placeholder
        assertThat(STUB.requests).hasSize(1);
        assertThat(STUB.requests.getFirst()).contains("limit=200", "user=ana_l");
    }

    @Test
    void theNowPlayingAlbumIsWhatAgoraShowsForMusic() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        sync(session, link(session, "ana_l")).andExpect(status().isOk());

        mvc.perform(get("/api/public/profiles/ana")).andExpect(status().isOk())
                .andExpect(jsonPath("$.now[?(@.category == 'MUSIC')].title").value("In Rainbows - Radiohead"))
                .andExpect(jsonPath("$.now[?(@.category == 'MUSIC')].source").value("LASTFM"));
    }

    @Test
    void syncingAgainWithoutNewPlayingAddsNoNewAlbumsOrCollectionItems() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        STUB.answer = LastFmStub.pageOf(LastFmStub.plays("Björk", "Homogenic", 11, NEWEST, LastFmStub.COVER));
        sync(session, account).andExpect(status().isOk());
        int items = count("collection_items");
        int entries = count("synced_entries");
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '1 hour'");

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(count("collection_items")).isEqualTo(items);
        assertThat(count("synced_entries")).isEqualTo(entries);
        assertThat(jdbc.queryForObject("select count(*) from media_items where canonical_provider = 'LASTFM' and title like 'Homogenic%'", Integer.class)).isEqualTo(1);
    }

    @Test
    void theSyncCooldownAppliesToLastFmToo() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        sync(session, account).andExpect(status().isOk());

        sync(session, account).andExpect(status().isTooManyRequests());
    }

    @Test
    void whenLastFmIsDownTheSyncFailsForThatAccountOnlyAndTheOtherSyncs() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long good = link(ana, "ana_l");
        long profileId = jdbc.queryForObject("select id from profiles where slug = 'bia'", Long.class);
        jdbc.update("insert into linked_accounts (profile_id, provider, username) values (?, 'LASTFM', 'bia_down')", profileId);
        long down = jdbc.queryForObject("select id from linked_accounts where username = 'bia_down'", Long.class);

        STUB.status = 500; // Last.fm is down for this call only
        sync(bia, down).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));
        STUB.status = 0;
        sync(ana, good).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(jdbc.queryForObject("select count(*) from collection_items where profile_id = ?", Integer.class,
                jdbc.queryForObject("select id from profiles where slug = 'bia'", Long.class))).isZero();
    }

    @Test
    void aUserWhoVanishedOrWentPrivateFailsTheSyncWithTheExplainableCode() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        STUB.privateUsers = List.of("ana_l");

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));
    }

    @Test
    void linkingAnUnknownOrPrivateUserIsRefused() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        link(session, "nobody_here", status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));
        STUB.privateUsers = List.of("ana_l");
        link(session, "ana_l", status().isBadRequest());

        assertThat(count("linked_accounts")).isZero();
    }

    @Test
    void whenLastFmCannotAnswerLinkingSaysUnavailableAndStoresNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        STUB.status = 500;

        link(session, "ana_l", status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(count("linked_accounts")).isZero();
    }

    @Test
    void aMaliciousNameIsRefusedBeforeAnyRequestToLastFm() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        link(session, "../../etc/passwd", status().isBadRequest());
        link(session, "ana&api_key=x", status().isBadRequest());
        link(session, "ana?method=user.getinfo", status().isBadRequest());
        link(session, "1ana", status().isBadRequest());
        link(session, "a", status().isBadRequest());
        link(session, "a".repeat(16), status().isBadRequest());

        assertThat(count("linked_accounts")).isZero();
        assertThat(STUB.requests).isEmpty();
    }

    @Test
    void anonymousAndOtherUsersCannotLinkOrSyncSomeoneElsesLastFmAccount() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long account = link(ana, "ana_l");

        mvc.perform(post("/api/linked-accounts").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"LASTFM\",\"username\":\"ana_l\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/linked-accounts/" + account + "/sync").with(RealCsrf.token(mvc)))
                .andExpect(status().isUnauthorized());
        sync(bia, account).andExpect(status().isNotFound());
        assertThat(count("collection_items")).isZero();
    }

    // --- helpers ---

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private long link(Cookie session, String username) throws Exception {
        String body = link(session, username, status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions link(Cookie session, String username, ResultMatcher expected) throws Exception {
        return mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"LASTFM\",\"username\":\"%s\"}".formatted(username)))
                .andExpect(expected);
    }

    private ResultActions sync(Cookie session, long accountId) throws Exception {
        return mvc.perform(post("/api/linked-accounts/" + accountId + "/sync").cookie(session).with(RealCsrf.token(mvc)));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
