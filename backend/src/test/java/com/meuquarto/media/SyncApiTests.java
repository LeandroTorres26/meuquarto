package com.meuquarto.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.media.application.SyncScheduler;
import com.meuquarto.media.application.SyncService;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.application.MediaSnapshots;
import com.meuquarto.media.domain.ActivitySnapshot;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.profile.application.ProfileCollection;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The sync engine against a real PostgreSQL (#29): manual sync with its BR-03 cooldown, idempotency,
 * hidden items, adult works, failures with backoff, the scheduler's batch and unlinking (FR-16). The
 * platform is a fake provider returning fixed snapshots; nothing touches the network or the real clock
 * of the scheduler (its method is called directly).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SyncApiTests.FakeProviders.class})
class SyncApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    private static final Instant WATCHED = Instant.parse("2026-01-10T20:00:00Z");

    @TestConfiguration
    static class FakeProviders {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE) // ahead of the production Letterboxd bean, which shares the id
        ActivityProvider fakeActivityProvider() {
            return new FakeActivityProvider();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    @Qualifier("fakeActivityProvider")
    ActivityProvider provider;

    @Autowired
    SyncScheduler scheduler;

    @Autowired
    SyncService sync;

    @Autowired
    ProfileCollection collection;

    @Autowired
    MediaSnapshots snapshots;

    private FakeActivityProvider fake;

    @BeforeEach
    void resetFake() {
        fake = (FakeActivityProvider) provider;
        fake.reset();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    // --- sync results ---

    @Test
    void theFirstSyncAddsVisibleItemsWithTheirSourceAndRecordsTheRun() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        fake.activity.put("ana_l", snapshot(entry("603", "Matrix", false), entry("604", "Reloaded", false)));

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.itemsCount").value(2))
                .andExpect(jsonPath("$.error").doesNotExist());

        assertThat(jdbc.queryForList("select hidden from collection_items")).hasSize(2)
                .allSatisfy(row -> assertThat(row.get("hidden")).isEqualTo(false));
        assertThat(jdbc.queryForList("select source from collection_item_sources", String.class))
                .containsOnly("LETTERBOXD");
        assertThat(count("synced_entries")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status || ':' || items_count from sync_runs", String.class))
                .isEqualTo("SUCCESS:2");
        Map<String, Object> row = jdbc.queryForMap(
                "select status, last_error, last_synced_at, consecutive_failures, next_attempt_at from linked_accounts");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("last_synced_at")).isNotNull();
        assertThat(row.get("last_error")).isNull();
        assertThat(row.get("consecutive_failures")).isEqualTo(0);
        assertThat(row.get("next_attempt_at")).isNull();
    }

    @Test
    void aSyncKeepsTheMetadataOfAWorkTheCatalogAlreadyOwns() throws Exception {
        snapshots.snapshot(new CatalogEntry(MediaCategory.MOVIE, "FAKE", "9001", "Catalog title", 1999, null, false));
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        fake.activity.put("ana_l", snapshot(entry("9001", "Feed title", false)));

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(jdbc.queryForObject("select title from media_items where external_id = '9001'", String.class))
                .isEqualTo("Catalog title");
    }

    @Test
    void syncingTwiceChangesNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        fake.activity.put("ana_l", snapshot(entry("603", "Matrix", false), entry("604", "Reloaded", false)));
        sync(session, account).andExpect(status().isOk());
        List<Map<String, Object>> itemsBefore = jdbc.queryForList(
                "select id, media_item_id, hidden, added_at, last_activity_at from collection_items order by id");
        List<Map<String, Object>> sourcesBefore = jdbc.queryForList(
                "select collection_item_id, source, linked_account_id, first_seen_at from collection_item_sources order by 1");
        List<Map<String, Object>> entriesBefore = jdbc.queryForList(
                "select id, media_item_id, activity_type, occurred_at from synced_entries order by id");
        endCooldown();

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(jdbc.queryForList(
                "select id, media_item_id, hidden, added_at, last_activity_at from collection_items order by id"))
                .isEqualTo(itemsBefore);
        assertThat(jdbc.queryForList(
                "select collection_item_id, source, linked_account_id, first_seen_at from collection_item_sources order by 1"))
                .isEqualTo(sourcesBefore);
        assertThat(jdbc.queryForList(
                "select id, media_item_id, activity_type, occurred_at from synced_entries order by id"))
                .isEqualTo(entriesBefore);
        assertThat(count("sync_runs")).isEqualTo(2);
    }

    @Test
    void anItemTheOwnerHidAndANoteTheOwnerWroteSurviveTheNextSync() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long userId = userId("a@example.com");
        long account = linkLetterboxd(session, "ana_l");
        fake.activity.put("ana_l", snapshot(entry("603", "Matrix", false), entry("604", "Reloaded", false)));
        sync(session, account).andExpect(status().isOk());
        collection.update(userId, itemOf("603"), null, true);
        collection.update(userId, itemOf("604"), "Muito bom", null);
        endCooldown();

        sync(session, account).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select hidden from collection_items where id = ?", Boolean.class, itemOf("603")))
                .isTrue();
        assertThat(jdbc.queryForObject("select note from collection_items where id = ?", String.class, itemOf("604")))
                .isEqualTo("Muito bom");
    }

    @Test
    void newActivityOnALaterSyncIsAddedAndKnownActivityIsNot() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        fake.activity.put("ana_l", snapshot(entry("603", "Matrix", false)));
        sync(session, account).andExpect(status().isOk());
        fake.activity.put("ana_l", snapshot(entry("603", "Matrix", false), entry("604", "Reloaded", false)));
        endCooldown();

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.itemsCount").value(2));

        assertThat(count("collection_items")).isEqualTo(2);
        assertThat(count("synced_entries")).isEqualTo(2);
    }

    @Test
    void adultWorksAreSkipped() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        fake.activity.put("ana_l", snapshot(entry("603", "Matrix", false), entry("999", "Adult one", true)));

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.itemsCount").value(1));

        assertThat(count("collection_items")).isEqualTo(1);
        assertThat(count("synced_entries")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from media_items where external_id = '999'", Integer.class))
                .isZero();
    }

    @Test
    void aWorkInACategoryThePlatformCannotFeedIsSkipped() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        CatalogEntry book = new CatalogEntry(MediaCategory.BOOK, "FAKE", "b1", "A book", 2001, null, false);
        fake.activity.put("ana_l", new ActivitySnapshot(List.of(new ActivityEntry(book, ActivityType.FINISHED, WATCHED))));

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.itemsCount").value(0));

        assertThat(count("collection_items")).isZero();
    }

    // --- failures and backoff ---

    @Test
    void aProviderFailureIsRecordedWithBackoffAndLeavesTheCollectionAlone() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        fake.activity.put("ana_l", snapshot(entry("603", "Matrix", false)));
        sync(session, account).andExpect(status().isOk());
        fake.failing.add("ana_l");
        endCooldown();

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.itemsCount").value(0))
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(count("collection_items")).isEqualTo(1); // the earlier item is untouched
        assertThat(jdbc.queryForObject("select status || ':' || error from sync_runs order by id desc limit 1",
                String.class)).isEqualTo("FAILED:PROVIDER_UNAVAILABLE");
        Map<String, Object> row = jdbc.queryForMap(
                "select status, last_error, consecutive_failures, next_attempt_at > now() + interval '29 minutes' as later from linked_accounts");
        assertThat(row.get("status")).isEqualTo("ERROR");
        assertThat(row.get("last_error")).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(row.get("consecutive_failures")).isEqualTo(1);
        assertThat(row.get("later")).isEqualTo(true);
        mvc.perform(get("/api/linked-accounts").cookie(session))
                .andExpect(jsonPath("$[0].status").value("ERROR"))
                .andExpect(jsonPath("$[0].lastError").value("PROVIDER_UNAVAILABLE"));
    }

    @Test
    void aFirstSyncThatFailsLeavesNothingBehind() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        fake.failing.add("ana_l");

        sync(session, account).andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(count("collection_items")).isZero();
        assertThat(count("synced_entries")).isZero();
        assertThat(count("sync_runs")).isEqualTo(1);
    }

    @Test
    void aSuccessAfterFailuresClearsTheErrorAndTheBackoff() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        fake.failing.add("ana_l");
        sync(session, account).andExpect(jsonPath("$.status").value("FAILED"));
        fake.failing.clear();
        endCooldown();

        sync(session, account).andExpect(jsonPath("$.status").value("SUCCESS"));

        Map<String, Object> row = jdbc.queryForMap(
                "select status, last_error, consecutive_failures, next_attempt_at from linked_accounts");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("last_error")).isNull();
        assertThat(row.get("consecutive_failures")).isEqualTo(0);
        assertThat(row.get("next_attempt_at")).isNull();
    }

    @Test
    void anAccountWhosePlatformHasNoProviderIsSkipped() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long profileId = profileOf("a@example.com");
        jdbc.update("insert into linked_accounts (profile_id, provider, username) values (?, 'STEAM', 'ana_s')", profileId);
        long account = jdbc.queryForObject("select id from linked_accounts", Long.class);

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("select status from sync_runs", String.class)).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("select status from linked_accounts", String.class)).isEqualTo("ACTIVE");
    }

    // --- BR-03 cooldown on the manual sync ---

    @Test
    void br03_aSecondManualSyncInsideFifteenMinutesAnswers429WithRetryAfter() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        sync(session, account).andExpect(status().isOk());
        int fetches = fake.fetched.size();

        String retryAfter = sync(session, account).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("SYNC_COOLDOWN"))
                .andExpect(header().exists("Retry-After"))
                .andReturn().getResponse().getHeader("Retry-After");

        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 900L);
        assertThat(fake.fetched).hasSize(fetches); // refused before any outbound request
        assertThat(count("sync_runs")).isEqualTo(1);
    }

    @Test
    void br03_afterTheCooldownTheManualSyncWorksAgain() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        sync(session, account).andExpect(status().isOk());
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '14 minutes'");
        sync(session, account).andExpect(status().isTooManyRequests());
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '16 minutes'");

        sync(session, account).andExpect(status().isOk());
    }

    @Test
    void br03_aFailedSyncAlsoStartsTheCooldown() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        fake.failing.add("ana_l");
        sync(session, account).andExpect(jsonPath("$.status").value("FAILED"));

        sync(session, account).andExpect(status().isTooManyRequests());
    }

    @Test
    void theAccountListShowsTheSyncStateAndWhenTheButtonWorksAgain() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");
        mvc.perform(get("/api/linked-accounts").cookie(session))
                .andExpect(jsonPath("$[0].lastSyncedAt").doesNotExist())
                .andExpect(jsonPath("$[0].nextManualSyncAt").doesNotExist());

        sync(session, account).andExpect(status().isOk());

        String body = mvc.perform(get("/api/linked-accounts").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].lastSyncedAt").isString())
                .andExpect(jsonPath("$[0].lastError").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Instant next = Instant.parse(JsonPath.read(body, "$[0].nextManualSyncAt"));
        assertThat(next).isAfter(Instant.now().plusSeconds(13 * 60)).isBefore(Instant.now().plusSeconds(16 * 60));
    }

    // --- scheduler ---

    @Test
    void theSchedulerPicksOnlyTheAccountsThatAreDue() throws Exception {
        long neverSynced = accountOf("a@example.com", "ana", "never");
        long stale = accountOf("b@example.com", "bia", "stale");
        long fresh = accountOf("c@example.com", "cai", "fresh");
        long backingOff = accountOf("d@example.com", "dia", "backing");
        long justClicked = accountOf("e@example.com", "eli", "clicked");
        jdbc.update("update linked_accounts set last_synced_at = now() - interval '7 hours' where id = ?", stale);
        jdbc.update("update linked_accounts set last_synced_at = now() - interval '1 hour' where id = ?", fresh);
        jdbc.update("update linked_accounts set next_attempt_at = now() + interval '1 hour', consecutive_failures = 1,"
                + " status = 'ERROR' where id = ?", backingOff);
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '5 minutes' where id = ?",
                justClicked);

        scheduler.runBatch();

        assertThat(fake.fetched).containsExactlyInAnyOrder("never", "stale");
        assertThat(jdbc.queryForObject("select last_synced_at is not null from linked_accounts where id = ?",
                Boolean.class, neverSynced)).isTrue();
        assertThat(count("sync_runs")).isEqualTo(2);

        fake.fetched.clear();
        scheduler.runBatch(); // everything just ran or is not due: nothing happens
        assertThat(fake.fetched).isEmpty();
    }

    @Test
    void anAccountBackingOffIsPickedAgainWhenItsTimeComes() throws Exception {
        long account = accountOf("a@example.com", "ana", "retry");
        jdbc.update("update linked_accounts set next_attempt_at = now() - interval '1 minute', consecutive_failures = 2,"
                + " status = 'ERROR' where id = ?", account);

        scheduler.runBatch();

        assertThat(fake.fetched).containsExactly("retry");
        assertThat(jdbc.queryForObject("select status from linked_accounts where id = ?", String.class, account))
                .isEqualTo("ACTIVE");
    }

    @Test
    void theBatchIsBoundedAndTakesTheOldestFirst() throws Exception {
        long oldest = accountOf("a@example.com", "ana", "oldest");
        long older = accountOf("b@example.com", "bia", "older");
        long old = accountOf("c@example.com", "cai", "old");
        jdbc.update("update linked_accounts set last_synced_at = now() - interval '30 hours' where id = ?", oldest);
        jdbc.update("update linked_accounts set last_synced_at = now() - interval '20 hours' where id = ?", older);
        jdbc.update("update linked_accounts set last_synced_at = now() - interval '10 hours' where id = ?", old);

        int synced = sync.syncDueAccounts(2);

        assertThat(synced).isEqualTo(2);
        assertThat(fake.fetched).containsExactly("oldest", "older");
    }

    @Test
    void oneFailingAccountDoesNotStopTheBatch() throws Exception {
        accountOf("a@example.com", "ana", "broken");
        accountOf("b@example.com", "bia", "fine");
        fake.failing.add("broken");
        fake.activity.put("fine", snapshot(entry("603", "Matrix", false)));

        scheduler.runBatch();

        assertThat(fake.fetched).containsExactlyInAnyOrder("broken", "fine");
        assertThat(jdbc.queryForObject("select status from linked_accounts where username = 'broken'", String.class))
                .isEqualTo("ERROR");
        assertThat(jdbc.queryForObject("select status from linked_accounts where username = 'fine'", String.class))
                .isEqualTo("ACTIVE");
        assertThat(count("collection_items")).isEqualTo(1);
    }

    // --- authorization ---

    @Test
    void anonymousCallsAreRefused() throws Exception {
        mvc.perform(post("/api/linked-accounts/1/sync").with(RealCsrf.token(mvc))).andExpect(status().isUnauthorized());
    }

    @Test
    void aSyncWithoutACsrfTokenIsRefused() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = linkLetterboxd(session, "ana_l");

        mvc.perform(post("/api/linked-accounts/" + account + "/sync").cookie(session)).andExpect(status().isForbidden());

        assertThat(fake.fetched).isEmpty();
    }

    @Test
    void anotherUsersAccountLooksMissingAndIsNeverFetched() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long anaAccount = linkLetterboxd(ana, "ana_l");

        sync(bia, anaAccount).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("LINKED_ACCOUNT_NOT_FOUND"));
        sync(bia, 987654321L).andExpect(status().isNotFound());

        assertThat(fake.fetched).isEmpty();
        assertThat(count("sync_runs")).isZero();
        assertThat(jdbc.queryForObject("select last_sync_started_at from linked_accounts", Instant.class)).isNull();
    }

    // --- FR-16 after a sync ---

    @Test
    void unlinkingAfterASyncRemovesTheSyncedOnlyItemsAndKeepsTheNotedOnesAsManual() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long userId = userId("a@example.com");
        long account = linkLetterboxd(session, "ana_l");
        fake.activity.put("ana_l", snapshot(entry("603", "Matrix", false), entry("604", "Reloaded", false)));
        sync(session, account).andExpect(status().isOk());
        collection.update(userId, itemOf("604"), "Quero rever", null);

        mvc.perform(delete("/api/linked-accounts/" + account).cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("select m.external_id from collection_items i join media_items m on m.id = i.media_item_id",
                String.class)).containsExactly("604");
        assertThat(jdbc.queryForList("select source from collection_item_sources", String.class)).containsExactly("MANUAL");
        assertThat(count("synced_entries")).isZero();
        assertThat(count("sync_runs")).isZero();
    }

    // --- helpers ---

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private long linkLetterboxd(Cookie session, String username) throws Exception {
        String body = mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"LETTERBOXD\",\"username\":\"%s\"}".formatted(username)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** A user with a Letterboxd account, inserted directly: the scheduler tests need many of them. */
    private long accountOf(String email, String slug, String username) throws Exception {
        signUp(email, slug);
        jdbc.update("insert into linked_accounts (profile_id, provider, username) values (?, 'LETTERBOXD', ?)",
                profileOf(email), username);
        return jdbc.queryForObject("select id from linked_accounts where username = ?", Long.class, username);
    }

    private ResultActions sync(Cookie session, long accountId) throws Exception {
        return mvc.perform(post("/api/linked-accounts/" + accountId + "/sync").cookie(session).with(RealCsrf.token(mvc)));
    }

    /** Pretends the last sync began long ago, so the next manual click is not held by BR-03. */
    private void endCooldown() {
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '1 hour'");
    }

    private static ActivitySnapshot snapshot(ActivityEntry... entries) {
        return new ActivitySnapshot(List.of(entries));
    }

    private static ActivityEntry entry(String externalId, String title, boolean adult) {
        return new ActivityEntry(new CatalogEntry(MediaCategory.MOVIE, "FAKE", externalId, title, 2000, null, adult),
                ActivityType.FINISHED, WATCHED);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private long userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private long profileOf(String email) {
        return jdbc.queryForObject("select p.id from profiles p join users u on u.id = p.user_id where u.email = ?",
                Long.class, email);
    }

    private long itemOf(String externalId) {
        return jdbc.queryForObject("""
                select i.id from collection_items i join media_items m on m.id = i.media_item_id
                where m.external_id = ?""", Long.class, externalId);
    }
}
