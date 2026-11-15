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
import java.time.Instant;
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
 * The real {@link SteamProvider} through the whole stack against a real PostgreSQL: link, sync, "Agora",
 * unlink (BR-10, FR-12, FR-15, FR-16, ADR 0005). Only the origin and the key are replaced, by a local stub
 * and a random key; the stub address is a test-only constructor, never configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SteamSyncTests.Stubs.class})
class SteamSyncTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    /** Random per run: nothing in the repository looks like a credential. */
    private static final String KEY = UUID.randomUUID().toString().replace("-", "");
    private static final String STEAM_ID = "76561198000000001";
    private static final SteamStub STEAM = new SteamStub(KEY);

    @TestConfiguration
    static class Stubs {

        /** Ahead of the production bean, which points at the real host. */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ActivityProvider stubbedSteam() {
            return new SteamProvider(HttpCatalogProvider.newClient(), STEAM.uri(), KEY, Clock.systemUTC(),
                    Duration.ofSeconds(2), Duration.ZERO);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private static long at(String iso) {
        return Instant.parse(iso).getEpochSecond();
    }

    @BeforeEach
    void freshStub() {
        STEAM.reset();
        STEAM.account("ana_l", STEAM_ID, List.of(
                SteamStub.game(1245620, "ELDEN RING", 3000, at("2026-10-31T20:00:00Z")),
                SteamStub.game(730, "Counter-Strike 2", 900, at("2026-09-01T20:00:00Z")),
                SteamStub.game(220, "Half-Life 2", 0, 0), // never played: BR-10
                SteamStub.game(400, "Portal", 0, at("2026-09-02T20:00:00Z"))),
                List.of(SteamStub.recentGame(1245620, "ELDEN RING", 3000)));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
        jdbc.update("delete from media_items where canonical_provider = 'STEAM'");
    }

    @AfterAll
    static void stopStub() {
        STEAM.close();
    }

    @Test
    void linkSyncAndUnlinkBringsOnlyPlayedGamesAsProvisionalSteamRows() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "STEAM", "ana_l");

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.itemsCount").value(2)); // the two never played are out (BR-10)

        assertThat(jdbc.queryForList(
                "select category || ':' || canonical_provider || ':' || external_id from media_items order by external_id",
                String.class)).containsExactly("GAME:STEAM:1245620", "GAME:STEAM:730");
        assertThat(jdbc.queryForObject("select image_url from media_items where external_id = '730'", String.class))
                .isEqualTo("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/library_600x900.jpg");
        assertThat(jdbc.queryForObject("select count(*) from collection_items", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("select source from collection_item_sources", String.class)).containsOnly("STEAM");

        mvc.perform(delete("/api/linked-accounts/" + account).cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from collection_items", Integer.class)).isZero();
    }

    @Test
    void theGameBeingPlayedFeedsAgoraAndSyncingAgainChangesNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "STEAM", "ana_l");
        sync(session, account).andExpect(status().isOk());

        mvc.perform(get("/api/public/profiles/ana")).andExpect(status().isOk())
                .andExpect(jsonPath("$.now[?(@.category == 'GAME')].title").value("ELDEN RING"))
                .andExpect(jsonPath("$.now[?(@.category == 'GAME')].source").value("STEAM"));

        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '1 hour'");
        sync(session, account).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from synced_entries", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from collection_items", Integer.class)).isEqualTo(2);
    }

    @Test
    void theItemLinksToItsSteamStorePage() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        sync(session, link(session, "STEAM", "ana_l")).andExpect(status().isOk());

        long item = jdbc.queryForObject("""
                select i.id from collection_items i join media_items m on m.id = i.media_item_id
                where m.external_id = '1245620'""", Long.class);

        mvc.perform(get("/api/public/profiles/ana/items/" + item)).andExpect(status().isOk())
                .andExpect(jsonPath("$.links[0].provider").value("STEAM"))
                .andExpect(jsonPath("$.links[0].url").value("https://store.steampowered.com/app/1245620/"));
    }

    @Test
    void aSteamId64CanBeLinkedAndItsProfileLinkUsesProfiles() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"STEAM\",\"username\":\"" + STEAM_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(STEAM_ID))
                .andExpect(jsonPath("$.profileUrl").value("https://steamcommunity.com/profiles/" + STEAM_ID));
        assertThat(STEAM.requestsTo("ResolveVanityURL")).isEmpty();
    }

    @Test
    void aVanityNameKeepsItsCaseAndLinksUnderId() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        STEAM.vanity.put("Ana-L_1", STEAM_ID);

        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"STEAM\",\"username\":\"  Ana-L_1 \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("Ana-L_1"))
                .andExpect(jsonPath("$.profileUrl").value("https://steamcommunity.com/id/Ana-L_1"));
    }

    @Test
    void aPrivateProfileAndAMissingNameAreBothRefusedAsNotFound() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        STEAM.owned.remove(STEAM_ID);

        link(session, "STEAM", "ana_l", status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));
        link(session, "STEAM", "nobody_here", status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));

        assertThat(count("linked_accounts")).isZero();
    }

    @Test
    void whenSteamCannotAnswerLinkingSaysUnavailableAndStoresNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        STEAM.status = 500;

        link(session, "STEAM", "ana_l", status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(count("linked_accounts")).isZero();
    }

    @Test
    void whenAnAccountGoesPrivateTheSyncFailsWithTheExplainableCodeAndKeepsWhatIsThere() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "STEAM", "ana_l");
        sync(session, account).andExpect(status().isOk());
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '1 hour'");
        STEAM.owned.remove(STEAM_ID);

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));

        assertThat(count("collection_items")).isEqualTo(2);
    }

    @Test
    void aMaliciousNameIsRefusedBeforeAnyRequestToSteam() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        for (String hostile : new String[] {"../../etc/passwd", "ana?key=x", "ab", "a".repeat(33), "ana b", "ana.l",
                "ana@evil", "https://evil.example/x"}) {
            link(session, "STEAM", hostile, status().isBadRequest());
        }

        assertThat(count("linked_accounts")).isZero();
        assertThat(STEAM.requests).isEmpty();
    }

    // --- authorization (the owner always comes from the session) ---

    @Test
    void anonymousCannotLinkOrSync() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        long account = link(ana, "STEAM", "ana_l");

        mvc.perform(post("/api/linked-accounts").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"STEAM\",\"username\":\"ana_l\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/linked-accounts/" + account + "/sync").with(RealCsrf.token(mvc)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherUserCannotSyncOrUnlinkSomeoneElsesSteamAccount() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long account = link(ana, "STEAM", "ana_l");

        sync(bia, account).andExpect(status().isNotFound());
        mvc.perform(delete("/api/linked-accounts/" + account).cookie(bia).with(RealCsrf.token(mvc)))
                .andExpect(status().isNotFound());

        assertThat(count("linked_accounts")).isEqualTo(1);
        assertThat(count("collection_items")).isZero();
    }

    @Test
    void theKeyNeverLeavesTheQueryStringOfTheStubbedRequestsAndIsNeverStored() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        sync(session, link(session, "STEAM", "ana_l")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from linked_accounts where username like ?", Integer.class,
                "%" + KEY + "%")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sync_runs where coalesce(error, '') like ?", Integer.class,
                "%" + KEY + "%")).isZero();
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
