package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.shared.domain.MediaCategory;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * The Steam leaf against Steam-shaped answers from a local stub (no real network, no real key). BR-10 (an
 * unplayed game never enters), the account formats, "Agora", the covers and the safety limits of the provider.
 */
class SteamProviderTest {

    /** A fresh random key per run (32 hex digits, like Steam's): nothing in the repository looks like a credential. */
    private static final String KEY = UUID.randomUUID().toString().replace("-", "");
    private static final String STEAM_ID = "76561198000000001";
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);

    private static SteamStub steam;

    @BeforeAll
    static void startStub() {
        steam = new SteamStub(KEY);
    }

    @AfterAll
    static void stopStub() {
        steam.close();
    }

    @BeforeEach
    void resetStub() {
        steam.reset();
        steam.account("ana_l", STEAM_ID, List.of(), List.of());
    }

    private static SteamProvider provider() {
        return provider(KEY, Duration.ofSeconds(2), steam.uri());
    }

    private static SteamProvider provider(String key, Duration timeout, URI endpoint) {
        return new SteamProvider(HttpCatalogProvider.newClient(), endpoint, key, NOW, timeout, Duration.ZERO);
    }

    private static long at(String iso) {
        return Instant.parse(iso).getEpochSecond();
    }

    private static List<ActivityEntry> entries() {
        return provider().fetchActivity("ana_l").entries();
    }

    @SafeVarargs
    private static void games(Map<String, Object>... games) {
        steam.owned.put(STEAM_ID, List.of(games));
    }

    private static void assertProblem(Runnable call, Problem problem) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(LinkedAccountException.class,
                e -> assertThat(e.problem()).isEqualTo(problem));
    }

    // ---- identity ----

    @Test
    void itIsTheSteamProviderForGamesAndImplementsTheInterfaceDirectly() {
        SteamProvider real = provider();
        ActivityProvider asInterface = real; // callers only ever hold the base type

        assertThat(asInterface.id()).isEqualTo(ProviderId.STEAM);
        assertThat(asInterface.categories()).containsExactly(MediaCategory.GAME);
        assertThat(SteamProvider.ENDPOINT).isEqualTo(URI.create("https://api.steampowered.com/"));
        assertThat(real.getClass().getSuperclass()).isEqualTo(Object.class);
        assertThat(java.lang.reflect.Modifier.isFinal(real.getClass().getModifiers())).isTrue();
    }

    // ---- BR-10 ----

    @Test
    void br10_onlyGamesWithPlaytimeAboveZeroEnterAndUnplayedGamesNeverDo() {
        games(SteamStub.game(10, "Played", 90, at("2026-10-01T10:00:00Z")),
                SteamStub.game(20, "Never played", 0, 0),
                SteamStub.game(30, "Never played but has a date", 0, at("2026-10-02T10:00:00Z")),
                SteamStub.game(40, "One minute", 1, at("2026-09-01T10:00:00Z")));

        List<ActivityEntry> entries = entries();

        assertThat(entries).extracting(e -> e.work().externalId()).containsExactlyInAnyOrder("10", "40");
        assertThat(entries).extracting(ActivityEntry::type).containsOnly(ActivityType.OWNED);
    }

    @Test
    void br10_aProfileOfOnlyUnplayedGamesIsAnEmptySnapshotNotAnError() {
        games(SteamStub.game(20, "Never played", 0, 0), SteamStub.game(21, "Also never", 0, 0));

        assertThat(entries()).isEmpty();
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS);
    }

    // ---- the work and its time ----

    @Test
    void aGameIsAProvisionalSteamRowWithTheNameFromSteamAndACoverBuiltFromTheAppId() {
        games(SteamStub.game(1245620, "ELDEN RING", 3000, at("2026-10-20T10:00:00Z")));

        ActivityEntry entry = entries().getFirst();

        assertThat(entry.work().category()).isEqualTo(MediaCategory.GAME);
        assertThat(entry.work().provider()).isEqualTo("STEAM");
        assertThat(entry.work().externalId()).isEqualTo("1245620");
        assertThat(entry.work().title()).isEqualTo("ELDEN RING");
        assertThat(entry.work().year()).isNull();
        assertThat(entry.work().adult()).isFalse();
        assertThat(entry.work().imageUrl())
                .isEqualTo("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1245620/library_600x900.jpg");
        assertThat(entry.occurredAt()).isEqualTo(Instant.parse("2026-10-20T10:00:00Z"));
    }

    @Test
    void theTimeIsTheLastPlayAndAMissingOrImpossibleOneIsTheSameConstantEverySync() {
        games(SteamStub.game(1, "Real", 10, at("2026-10-01T10:00:00Z")),
                SteamStub.game(2, "No date", 10, 0),
                SteamStub.game(3, "Before Steam existed", 10, at("1999-01-01T00:00:00Z")),
                SteamStub.game(4, "In the future", 10, at("2027-06-01T00:00:00Z")));

        Map<String, Instant> byApp = new java.util.HashMap<>();
        entries().forEach(e -> byApp.put(e.work().externalId(), e.occurredAt()));

        assertThat(byApp.get("1")).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(byApp.get("2")).isEqualTo(SteamProvider.UNKNOWN_DATE);
        assertThat(byApp.get("3")).isEqualTo(SteamProvider.UNKNOWN_DATE);
        assertThat(byApp.get("4")).isEqualTo(SteamProvider.UNKNOWN_DATE);
        assertThat(entries()).extracting(ActivityEntry::occurredAt)
                .isEqualTo(entries().stream().map(ActivityEntry::occurredAt).toList()); // stable between syncs
    }

    @Test
    void entriesComeNewestFirstAndAreCappedAtTheMostRecentOnes() {
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 1; i <= SteamProvider.MAX_ENTRIES + 20; i++) {
            many.add(SteamStub.game(i, "Game " + i, 10, at("2026-10-01T00:00:00Z") + i)); // a higher id is newer
        }
        steam.owned.put(STEAM_ID, many);

        List<ActivityEntry> entries = entries();

        assertThat(entries).hasSize(SteamProvider.MAX_ENTRIES);
        assertThat(entries.getFirst().work().externalId()).isEqualTo(String.valueOf(SteamProvider.MAX_ENTRIES + 20));
        assertThat(entries).extracting(e -> e.work().externalId()).doesNotContain("1", "20").contains("21");
    }

    @Test
    void aMalformedGameIsSkippedWithoutHidingTheOthers() {
        steam.owned.put(STEAM_ID, List.of(SteamStub.game(1, "Fine", 10, at("2026-10-01T10:00:00Z")),
                Map.of("appid", "abc", "name", "No number", "playtime_forever", 10),
                Map.of("appid", 2, "playtime_forever", 10), // no name
                Map.of("appid", -5, "name", "Negative", "playtime_forever", 10),
                Map.of("appid", 99999999999L, "name", "Too big", "playtime_forever", 10),
                Map.of("appid", 3, "name", "   ", "playtime_forever", 10)));

        assertThat(entries()).extracting(e -> e.work().externalId()).containsExactly("1");
    }

    @Test
    void aTitleIsWhitespaceCollapsedAndBounded() {
        games(SteamStub.game(1, "  Two\n  words\t here  ", 10, 0), SteamStub.game(2, "x".repeat(1000), 10, 0));

        Map<String, String> titles = new java.util.HashMap<>();
        entries().forEach(e -> titles.put(e.work().externalId(), e.work().title()));

        assertThat(titles.get("1")).isEqualTo("Two words here");
        assertThat(titles.get("2")).hasSize(300);
    }

    // ---- Agora ----

    @Test
    void theMostRecentlyPlayedOfTheRecentGamesIsInProgressAndTheRestAreOwned() {
        games(SteamStub.game(10, "Old", 500, at("2026-06-01T10:00:00Z")),
                SteamStub.game(20, "Recent but older", 100, at("2026-10-25T10:00:00Z")),
                SteamStub.game(30, "Playing now", 100, at("2026-10-31T22:00:00Z")),
                SteamStub.game(40, "Never played", 0, 0));
        steam.recent.put(STEAM_ID, List.of(SteamStub.recentGame(20, "Recent but older", 100),
                SteamStub.recentGame(30, "Playing now", 100)));

        List<ActivityEntry> entries = entries();

        assertThat(entries).extracting(e -> e.work().externalId() + ":" + e.type())
                .containsExactly("30:IN_PROGRESS", "20:OWNED", "10:OWNED");
        assertThat(entries.getFirst().occurredAt()).isEqualTo(Instant.parse("2026-10-31T22:00:00Z"));
        assertThat(steam.requestsTo("GetRecentlyPlayedGames")).hasSize(1);
    }

    @Test
    void aRecentGameWithNoPlaytimeInTheOwnedListNeverBecomesAgora() {
        games(SteamStub.game(10, "Old", 500, at("2026-06-01T10:00:00Z")), SteamStub.game(20, "Just opened", 0, 0));
        steam.recent.put(STEAM_ID, List.of(SteamStub.recentGame(20, "Just opened", 0)));

        assertThat(entries()).extracting(e -> e.work().externalId() + ":" + e.type()).containsExactly("10:OWNED");
    }

    @Test
    void withNothingRecentEverythingIsOwned() {
        games(SteamStub.game(10, "Old", 500, at("2026-06-01T10:00:00Z")));

        assertThat(entries()).extracting(ActivityEntry::type).containsOnly(ActivityType.OWNED);
    }

    // ---- the account: vanity name or SteamID64 ----

    @Test
    void aVanityNameIsResolvedOnceAndThenItsGamesAreRead() {
        games(SteamStub.game(10, "Played", 90, at("2026-10-01T10:00:00Z")));

        entries();

        assertThat(steam.requestsTo("ResolveVanityURL")).hasSize(1);
        assertThat(steam.requestsTo("ResolveVanityURL").getFirst().rawQuery()).contains("vanityurl=ana_l");
        assertThat(steam.requestsTo("GetOwnedGames").getFirst().rawQuery())
                .contains("steamid=" + STEAM_ID, "include_appinfo=1", "include_played_free_games=1");
        assertThat(steam.requestsTo("GetRecentlyPlayedGames").getFirst().rawQuery()).contains("steamid=" + STEAM_ID);
    }

    @Test
    void aSteamId64IsUsedAsItIsWithoutResolving() {
        games(SteamStub.game(10, "Played", 90, at("2026-10-01T10:00:00Z")));

        List<ActivityEntry> entries = provider().fetchActivity(STEAM_ID).entries();

        assertThat(entries).hasSize(1);
        assertThat(steam.requestsTo("ResolveVanityURL")).isEmpty();
        assertThat(steam.requestsTo("GetOwnedGames").getFirst().rawQuery()).contains("steamid=" + STEAM_ID);
    }

    @Test
    void aNameSteamDoesNotKnowIsNotFoundAndItsGamesAreNeverAsked() {
        assertThat(provider().checkAccountExists("nobody_here")).isEqualTo(AccountCheck.NOT_FOUND);
        assertProblem(() -> provider().fetchActivity("nobody_here"), Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);

        assertThat(steam.requestsTo("GetOwnedGames")).isEmpty();
    }

    @Test
    void aPrivateProfileOrPrivateGameDetailsIsNotFoundWhenLinkingAndWhenSyncing() {
        steam.owned.remove(STEAM_ID); // Steam answers {"response":{}}

        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.NOT_FOUND);
        assertThat(provider().checkAccountExists(STEAM_ID)).isEqualTo(AccountCheck.NOT_FOUND);
        assertProblem(() -> entries(), Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);
        assertThat(steam.requestsTo("GetRecentlyPlayedGames")).isEmpty();
    }

    @Test
    void aPublicProfileWithNoGamesAtAllExists() {
        steam.owned.put(STEAM_ID, List.of());

        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS);
        assertThat(entries()).isEmpty();
    }

    @Test
    void anIdSteamSendsBackThatDoesNotLookLikeASteamIdIsNeverUsed() {
        steam.vanity.put("odd_one", "123/../456");

        assertThat(provider().checkAccountExists("odd_one")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertProblem(() -> provider().fetchActivity("odd_one"), Problem.PROVIDER_UNAVAILABLE);
        assertThat(steam.requestsTo("GetOwnedGames")).isEmpty();
    }

    // ---- username rules and hostile names ----

    @ParameterizedTest
    @ValueSource(strings = {"abc", "Ana_L", "ana-l", "a1b", "abcdefghijklmnopqrstuvwxyz012345", STEAM_ID, "76561198000000001"})
    void aVanityNameOrASteamId64IsValidAndCaseIsKept(String name) {
        SteamProvider provider = provider();

        assertThat(provider.validateUsernameFormat(provider.normalizeUsername("  " + name + " "))).isEmpty();
        assertThat(provider.normalizeUsername(name)).isEqualTo(name);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "a b", "a/b", "../x", "..", "../../etc/passwd", "a?x=y", "a#b", "a@b",
            "a.b", "a%2fb", "a\r\nX-Evil: 1", "a\u0000b", "café", "ＡＮＡ", "７６５６１１９８０００００００１",
            "٧٦٥٦١١٩٨٠٠٠٠٠٠٠١", "https://evil.example", "steamcommunity.com/id/x", "a\\b", "a;b", "a&key=x", "x\"){ id }"})
    void aHostileNameIsRefusedByTheFormatRulesAndNeverReachesTheNetwork(String name) {
        SteamProvider provider = provider();

        assertThat(provider.validateUsernameFormat(provider.normalizeUsername(name))).isPresent();
        assertThat(provider.checkAccountExists(name)).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider.fetchActivity(name)).isInstanceOf(RuntimeException.class);
        assertThat(steam.requests).isEmpty();
    }

    @Test
    void aNumberThatIsNotASteamId64IsSentAsAnEncodedVanityNameNeverAsAnId() {
        steam.vanity.put("123456789", STEAM_ID);

        assertThat(provider().checkAccountExists("123456789")).isEqualTo(AccountCheck.EXISTS);
        assertThat(steam.requestsTo("ResolveVanityURL").getFirst().rawQuery()).contains("vanityurl=123456789");
    }

    // ---- failures ----

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    void anyErrorStatusIsUnavailableAndTheFetchThrowsProviderUnavailable(int code) {
        steam.status = code;

        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(provider().checkAccountExists(STEAM_ID)).isEqualTo(AccountCheck.UNAVAILABLE);
        assertProblem(() -> entries(), Problem.PROVIDER_UNAVAILABLE);
        assertProblem(() -> provider().fetchActivity(STEAM_ID), Problem.PROVIDER_UNAVAILABLE);
    }

    @Test
    void aKeySteamDoesNotKnowIsUnavailableNotNotFound() {
        SteamProvider wrong = provider(UUID.randomUUID().toString().replace("-", ""), Duration.ofSeconds(2), steam.uri());

        assertThat(wrong.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE); // 403
        assertProblem(() -> wrong.fetchActivity("ana_l"), Problem.PROVIDER_UNAVAILABLE);
    }

    @Test
    void anUnreadableOrUnexpectedBodyIsUnavailable() {
        steam.forcedBody = "<html>bad gateway</html>";
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertProblem(() -> entries(), Problem.PROVIDER_UNAVAILABLE);

        steam.forcedBody = "{\"something\":\"else\"}";
        assertThat(provider().checkAccountExists(STEAM_ID)).isEqualTo(AccountCheck.UNAVAILABLE);
        assertProblem(() -> provider().fetchActivity(STEAM_ID), Problem.PROVIDER_UNAVAILABLE);
    }

    @Test
    void whenOnlyTheRecentlyPlayedCallFailsTheWholeFetchFailsSoNothingIsAppliedHalfWay() {
        games(SteamStub.game(10, "Played", 90, at("2026-10-01T10:00:00Z")));
        steam.failingMethod = "GetRecentlyPlayedGames";

        assertProblem(() -> entries(), Problem.PROVIDER_UNAVAILABLE);
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS); // linking does not need it
    }

    @Test
    void anOversizeAnswerIsDropped() {
        steam.forcedBody = "{\"response\":{\"games\":[],\"pad\":\"" + "a".repeat(SteamProvider.MAX_BODY_BYTES) + "\"}}";

        assertProblem(() -> provider().fetchActivity(STEAM_ID), Problem.PROVIDER_UNAVAILABLE);
        assertThat(provider().checkAccountExists(STEAM_ID)).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    @Test
    void aSlowServerTimesOut() {
        steam.delayMillis = 2500;
        SteamProvider quick = provider(KEY, Duration.ofMillis(300), steam.uri());

        assertProblem(() -> quick.fetchActivity("ana_l"), Problem.PROVIDER_UNAVAILABLE);
        assertThat(quick.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    @Test
    void aRedirectIsNotFollowed() {
        steam.status = 302;

        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(steam.requests).hasSize(1);
    }

    @Test
    void theRequestsAreSpacedToStayGentle() {
        SteamProvider spaced = new SteamProvider(HttpCatalogProvider.newClient(), steam.uri(), KEY, NOW,
                Duration.ofSeconds(2), Duration.ofMillis(150));
        long start = System.nanoTime();

        for (int i = 0; i < 4; i++) {
            spaced.checkAccountExists(STEAM_ID);
        }

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(3 * 150 - 20));
    }

    // ---- the key ----

    @Test
    void withoutAKeyNothingIsSentAndTheProviderIsUnavailable() {
        for (String missing : new String[] {null, "", "   "}) {
            SteamProvider keyless = provider(missing, Duration.ofSeconds(2), steam.uri());

            assertThat(keyless.isAvailable()).isFalse();
            assertThat(keyless.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
            assertProblem(() -> keyless.fetchActivity("ana_l"), Problem.PROVIDER_UNAVAILABLE);
        }
        assertThat(steam.requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc&steamid=evil", "abc def", "key/../x", "k#frag", "k\r\nHost: evil", "short",
            "ключ-ключ-ключ-ключ-ключ-ключ-ключ", "0123456789abcdef0123456789abcdeg", "0123456789abcdef0123456789abcdef0"})
    void aKeyThatIsNot32HexDigitsIsNeverSent(String badKey) {
        SteamProvider odd = provider(badKey, Duration.ofSeconds(2), steam.uri());

        assertThat(odd.isAvailable()).isFalse();
        assertThat(odd.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(steam.requests).isEmpty();
    }

    @Test
    void aConfiguredKeyMakesTheProviderAvailable() {
        assertThat(provider().isAvailable()).isTrue();
    }

    @Test
    void theKeyNeverReachesTheLogsOrAnExceptionOnAnyKindOfFailure() {
        ListAppender<ILoggingEvent> logs = captureLogs();
        List<Throwable> thrown = new ArrayList<>();
        try {
            SteamProvider wrong = provider(UUID.randomUUID().toString().replace("-", ""), Duration.ofSeconds(2), steam.uri());
            wrong.checkAccountExists("ana_l"); // 403
            thrown.add(catchLinked(() -> wrong.fetchActivity("ana_l")));
            steam.status = 500;
            provider().checkAccountExists("ana_l");
            thrown.add(catchLinked(() -> entries()));
            steam.status = 0;
            steam.forcedBody = "not json";
            thrown.add(catchLinked(() -> entries()));
            steam.forcedBody = null;
            steam.delayMillis = 2500;
            thrown.add(catchLinked(() -> provider(KEY, Duration.ofMillis(200), steam.uri()).fetchActivity("ana_l")));
            steam.delayMillis = 0;
            SteamStub gone = new SteamStub(KEY); // a refused connection: the stub's own port, closed
            URI closed = gone.uri();
            gone.close();
            thrown.add(catchLinked(() -> provider(KEY, Duration.ofSeconds(1), closed).fetchActivity("ana_l")));
            steam.owned.remove(STEAM_ID);
            thrown.add(catchLinked(() -> entries())); // private: the other problem code
        } finally {
            detach(logs);
        }

        assertThat(thrown).hasSize(6).allSatisfy(e -> {
            assertThat(String.valueOf(e.getMessage()) + e.getCause()).doesNotContain(KEY);
            assertThat(e.getCause()).isNull();
        });
        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent event : logs.list) {
            String everything = event.getFormattedMessage() + event.getThrowableProxy();
            assertThat(everything).doesNotContain(KEY).doesNotContain("api.steampowered.com").doesNotContain("key=");
        }
    }

    private static LinkedAccountException catchLinked(Runnable call) {
        try {
            call.run();
        } catch (LinkedAccountException expected) {
            return expected;
        }
        throw new AssertionError("expected a LinkedAccountException");
    }

    // ---- the cover and its host allow-list ----

    @Test
    void aCoverIsOnlyEverBuiltFromTheAppIdOnTheListedHost() {
        assertThat(SteamProvider.IMAGE_HOSTS).containsExactly("shared.akamai.steamstatic.com");
        assertThat(SteamProvider.cover(730))
                .isEqualTo("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/library_600x900.jpg");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "http://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/library_600x900.jpg",
            "https://evil.example/store_item_assets/steam/apps/730/library_600x900.jpg",
            "https://shared.akamai.steamstatic.com.evil.example/store_item_assets/steam/apps/730/library_600x900.jpg",
            "https://user@shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/library_600x900.jpg",
            "https://shared.akamai.steamstatic.com:8443/store_item_assets/steam/apps/730/library_600x900.jpg",
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/library_600x900.jpg?x=1",
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/library_600x900.jpg#f",
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/../730/library_600x900.jpg",
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/abc/library_600x900.jpg",
            "https://shared.akamai.steamstatic.com/other/path.jpg",
            "javascript:alert(1)", "//shared.akamai.steamstatic.com/x.jpg", "not a url"})
    void anyOtherImageGivesNoImage(String url) {
        assertThat(SteamProvider.allowedImage(url)).isNull();
    }

    // ---- helpers ----

    private static ListAppender<ILoggingEvent> captureLogs() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        return logs;
    }

    private static void detach(ListAppender<ILoggingEvent> logs) {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }
}
