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
import com.meuquarto.media.domain.MediaKey;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.media.domain.UsernameProblem;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/** The Last.fm leaf against Last.fm-shaped answers from a local stub (no real network, no real key). */
class LastFmProviderTest {

    /** A fresh random key per run: nothing in the repository looks like a credential. */
    private static final String KEY = UUID.randomUUID().toString().replace("-", "");
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final long DAY = 86_400L;
    private static final long NEWEST = NOW.getEpochSecond() - 3600;

    private static LastFmStub stub;

    @BeforeAll
    static void startStub() {
        stub = new LastFmStub(KEY);
    }

    @AfterAll
    static void stopStub() {
        stub.close();
    }

    @BeforeEach
    void resetStub() {
        stub.reset();
    }

    private LastFmProvider provider() {
        return provider(KEY, Duration.ofSeconds(2), Duration.ZERO);
    }

    private LastFmProvider provider(String key, Duration timeout, Duration spacing) {
        return new LastFmProvider(HttpCatalogProvider.newClient(), stub.uri(), key, CLOCK, timeout, spacing,
                LastFmProvider.MIN_SCROBBLES);
    }

    @SafeVarargs
    private static List<Map<String, Object>> all(List<Map<String, Object>>... groups) {
        List<Map<String, Object>> tracks = new ArrayList<>();
        for (List<Map<String, Object>> group : groups) {
            tracks.addAll(group);
        }
        return tracks;
    }

    private static List<ActivityEntry> entries(LastFmProvider provider) {
        return provider.fetchActivity("ana_l").entries();
    }

    // ---- identity ----

    @Test
    void itIsTheLastFmMusicProviderAndImplementsTheInterfaceDirectly() {
        LastFmProvider real = new LastFmProvider(KEY, CLOCK);
        ActivityProvider asInterface = real; // callers only ever hold the base type

        assertThat(asInterface.id()).isEqualTo(ProviderId.LASTFM);
        assertThat(asInterface.categories()).containsExactly(MediaCategory.MUSIC);
        assertThat(LastFmProvider.ENDPOINT).isEqualTo(URI.create("https://ws.audioscrobbler.com/2.0/"));
        assertThat(real.getClass().getSuperclass()).isEqualTo(Object.class); // nothing inherited: implements the interface only
        assertThat(java.lang.reflect.Modifier.isFinal(real.getClass().getModifiers())).isTrue();
    }

    // ---- BR-10: albums with enough scrobbles, never individual plays ----

    @Test
    void anAlbumWithEnoughScrobblesBecomesOneLovedEntryAtItsNewestScrobble() {
        stub.answer = LastFmStub.pageOf(LastFmStub.plays("Radiohead", "OK Computer", 10, NEWEST, LastFmStub.COVER));

        List<ActivityEntry> entries = entries(provider());

        assertThat(entries).hasSize(1);
        ActivityEntry entry = entries.getFirst();
        assertThat(entry.type()).isEqualTo(ActivityType.LOVED);
        assertThat(entry.occurredAt()).isEqualTo(Instant.ofEpochSecond(NEWEST));
        assertThat(entry.work().category()).isEqualTo(MediaCategory.MUSIC);
        assertThat(entry.work().provider()).isEqualTo("LASTFM");
        assertThat(entry.work().title()).isEqualTo("OK Computer - Radiohead");
        assertThat(entry.work().adult()).isFalse();
        assertThat(entry.work().imageUrl()).isEqualTo(LastFmStub.COVER);
    }

    @Test
    void theThresholdIsAtLeastNSoNineScrobblesAddNothing() {
        stub.answer = LastFmStub.pageOf(all(
                LastFmStub.plays("Radiohead", "OK Computer", 9, NEWEST, LastFmStub.COVER),
                LastFmStub.plays("Björk", "Homogenic", 10, NEWEST - DAY, LastFmStub.COVER)));

        assertThat(entries(provider())).extracting(e -> e.work().title()).containsExactly("Homogenic - Björk");
    }

    @Test
    void scrobblesOfDifferentTracksOfTheSameAlbumAddUp() {
        List<Map<String, Object>> tracks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> track = LastFmStub.scrobble("Radiohead", "Kid A", NEWEST - i * 60L, LastFmStub.COVER);
            track.put("name", "Track " + i);
            tracks.add(track);
        }
        stub.answer = LastFmStub.pageOf(tracks);

        assertThat(entries(provider())).hasSize(1);
    }

    @Test
    void aSingleScrobbleNeverAddsAnythingByItself() {
        stub.answer = LastFmStub.page(LastFmStub.scrobble("Radiohead", "Kid A", NEWEST, LastFmStub.COVER));

        assertThat(entries(provider())).isEmpty();
    }

    @Test
    void tracksWithoutAnAlbumOrArtistAreSkippedNotCounted() {
        List<Map<String, Object>> tracks = all(
                LastFmStub.plays("Radiohead", "", 12, NEWEST, LastFmStub.COVER), // singles: no album
                LastFmStub.plays("", "Orphan", 12, NEWEST, LastFmStub.COVER), // no artist
                LastFmStub.plays("Radiohead", "Kid A", 10, NEWEST - DAY, LastFmStub.COVER));
        stub.answer = LastFmStub.pageOf(tracks);

        assertThat(entries(provider())).extracting(e -> e.work().title()).containsExactly("Kid A - Radiohead");
    }

    @Test
    void entriesComeNewestFirst() {
        stub.answer = LastFmStub.pageOf(all(
                LastFmStub.plays("A", "Old", 10, NEWEST - 5 * DAY, null),
                LastFmStub.plays("B", "New", 10, NEWEST, null)));

        assertThat(entries(provider())).extracting(e -> e.work().title()).containsExactly("New - B", "Old - A");
    }

    // ---- Agora: what is playing now ----

    @Test
    void theTrackPlayingNowIsAnInProgressEntryTimedAtTheFetch() {
        stub.answer = LastFmStub.pageOf(all(
                List.of(LastFmStub.nowPlaying("Radiohead", "In Rainbows", LastFmStub.COVER)),
                LastFmStub.plays("Björk", "Homogenic", 10, NEWEST, LastFmStub.COVER)));

        List<ActivityEntry> entries = entries(provider());

        assertThat(entries).extracting(ActivityEntry::type).containsExactly(ActivityType.IN_PROGRESS, ActivityType.LOVED);
        assertThat(entries.getFirst().occurredAt()).isEqualTo(NOW);
        assertThat(entries.getFirst().work().title()).isEqualTo("In Rainbows - Radiohead");
    }

    @Test
    void aSingleNowPlayingTrackAnswerAsAnObjectIsReadToo() {
        // Last.fm turns a one-element list into a bare object in its JSON
        stub.forcedBody = "{\"recenttracks\":{\"track\":" + toJson(LastFmStub.nowPlaying("Radiohead", "In Rainbows", null))
                + "}}";

        assertThat(entries(provider())).extracting(ActivityEntry::type).containsExactly(ActivityType.IN_PROGRESS);
    }

    @Test
    void nowPlayingWithoutAnAlbumAddsNothing() {
        stub.answer = LastFmStub.page(LastFmStub.nowPlaying("Radiohead", "", null));

        assertThat(entries(provider())).isEmpty();
    }

    @Test
    void anAlbumPlayingNowThatIsAlsoPlayedALotIsBothInProgressAndLoved() {
        stub.answer = LastFmStub.pageOf(all(
                List.of(LastFmStub.nowPlaying("Radiohead", "Kid A", LastFmStub.COVER)),
                LastFmStub.plays("Radiohead", "Kid A", 10, NEWEST, LastFmStub.COVER)));

        List<ActivityEntry> entries = entries(provider());

        assertThat(entries).extracting(ActivityEntry::type).containsExactlyInAnyOrder(ActivityType.IN_PROGRESS, ActivityType.LOVED);
        assertThat(entries.get(0).work().key()).isEqualTo(entries.get(1).work().key());
    }

    @Test
    void theNowPlayingTrackDoesNotCountAsAScrobble() {
        stub.answer = LastFmStub.pageOf(all(
                List.of(LastFmStub.nowPlaying("Radiohead", "Kid A", null)),
                LastFmStub.plays("Radiohead", "Kid A", 9, NEWEST, null)));

        assertThat(entries(provider())).extracting(ActivityEntry::type).containsExactly(ActivityType.IN_PROGRESS);
    }

    // ---- ADR 0005: a provisional row with a deterministic key ----

    @Test
    void theWorkIsAProvisionalLastFmRowKeyedByANormalizedArtistAndAlbumHash() {
        stub.answer = LastFmStub.pageOf(LastFmStub.plays("Radiohead", "OK Computer", 10, NEWEST, null));

        MediaKey key = entries(provider()).getFirst().work().key();

        assertThat(key.category()).isEqualTo(MediaCategory.MUSIC);
        assertThat(key.provider()).isEqualTo("LASTFM");
        assertThat(key.externalId()).matches("[0-9a-f]{40}").isEqualTo(LastFmProvider.keyOf("radiohead", "ok computer"));
    }

    @Test
    void theKeyIgnoresOnlyCaseSpacingAndUnicodeFormNothingElse() {
        String key = LastFmProvider.keyOf("Radiohead", "OK Computer");

        assertThat(LastFmProvider.keyOf("  RADIOHEAD ", "ok   computer")).isEqualTo(key);
        assertThat(LastFmProvider.keyOf("Beyoncé", "Lemonade")).isEqualTo(LastFmProvider.keyOf("Beyoncé", "Lemonade")); // NFKC
        assertThat(LastFmProvider.keyOf("Radiohead", "OK Computer (Deluxe)")).isNotEqualTo(key); // no fuzzy matching
        assertThat(LastFmProvider.keyOf("Radiohead", "OK Computer ")).isEqualTo(key);
        assertThat(LastFmProvider.keyOf("Ra", "diohead\nOK Computer")).isNotEqualTo(key); // the separator cannot be forged
    }

    @Test
    void theSameAlbumSpelledWithAnMbidOnSomeScrobblesIsStillOneAlbum() {
        List<Map<String, Object>> tracks = LastFmStub.plays("Radiohead", "OK Computer", 10, NEWEST, null);
        tracks.get(0).put("album", Map.of("mbid", "0b6b4ba0-d36f-47bd-b4ea-6a5b91842d29", "#text", "OK Computer"));
        stub.answer = LastFmStub.pageOf(tracks);

        assertThat(entries(provider())).hasSize(1);
    }

    // ---- covers: Last.fm hosts only, no placeholder ----

    @Test
    void theLargestUsableCoverIsKeptAndThePlaceholderStarIsDropped() {
        stub.answer = LastFmStub.pageOf(all(
                LastFmStub.plays("A", "With cover", 10, NEWEST, LastFmStub.COVER),
                LastFmStub.plays("B", "Star", 10, NEWEST - DAY, LastFmStub.PLACEHOLDER),
                LastFmStub.plays("C", "Empty", 10, NEWEST - 2 * DAY, null)));

        List<ActivityEntry> entries = entries(provider());

        assertThat(entries.get(0).work().imageUrl()).isEqualTo(LastFmStub.COVER);
        assertThat(entries.get(1).work().imageUrl()).isNull();
        assertThat(entries.get(2).work().imageUrl()).isNull();
    }

    @Test
    void aLaterScrobbleCanSupplyTheCoverTheFirstOneLacked() {
        List<Map<String, Object>> tracks = LastFmStub.plays("A", "Late cover", 10, NEWEST, null);
        tracks.set(9, LastFmStub.scrobble("A", "Late cover", NEWEST - 30 * DAY, LastFmStub.COVER));
        stub.answer = LastFmStub.pageOf(tracks);

        assertThat(entries(provider()).getFirst().work().imageUrl()).isEqualTo(LastFmStub.COVER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example/i/u/300x300/abc.jpg",
            "http://lastfm.freetls.fastly.net/i/u/300x300/abc.jpg",
            "//lastfm.freetls.fastly.net/i/u/300x300/abc.jpg",
            "https://lastfm.freetls.fastly.net.evil.example/i/u/300x300/abc.jpg",
            "https://evil.example/lastfm.freetls.fastly.net/i/u/300x300/abc.jpg",
            "https://lastfm.freetls.fastly.net@evil.example/i/u/300x300/abc.jpg",
            "https://evil.example@lastfm.freetls.fastly.net/i/u/300x300/abc.jpg",
            "https://lastfm.freetls.fastly.net:8443/i/u/300x300/abc.jpg",
            "https://lastfm.freetls.fastly.net/i/u/300x300/abc.jpg?x=1",
            "https://lastfm.freetls.fastly.net/other/abc.jpg",
            "https://lastfm.freetls.fastly.net/i/u/../../secret",
            "https://127.0.0.1/i/u/300x300/abc.jpg",
            "javascript:alert(1)",
            "data:image/png;base64,AAAA",
            "file:///etc/passwd",
            "not a url"})
    void aCoverOutsideTheAllowlistIsNeverKept(String src) {
        stub.answer = LastFmStub.pageOf(LastFmStub.plays("A", "Album", 10, NEWEST, src));

        assertThat(entries(provider()).getFirst().work().imageUrl()).isNull();
    }

    @Test
    void bothOfficialImageHostsAreAccepted() {
        String legacy = "https://lastfm-img2.akamaized.net/i/u/300x300/0a1b2c3d4e5f60718293a4b5c6d7e8f9.jpg";
        stub.answer = LastFmStub.pageOf(LastFmStub.plays("A", "Album", 10, NEWEST, legacy));

        assertThat(entries(provider()).getFirst().work().imageUrl()).isEqualTo(legacy);
    }

    // ---- hostile content and bounds ----

    @Test
    void markupStaysPlainTextAndHugeNamesAreCut() {
        String album = "<script>alert(1)</script>" + "x".repeat(5000);
        stub.answer = LastFmStub.pageOf(LastFmStub.plays("<b>Band</b>", album, 10, NEWEST, null));

        String title = entries(provider()).getFirst().work().title();

        assertThat(title).startsWith("<script>alert(1)</script>").hasSizeLessThanOrEqualTo(300); // data; the UI escapes it
    }

    @Test
    void aMalformedTrackIsSkippedWithoutHidingTheOthers() {
        List<Map<String, Object>> tracks = LastFmStub.plays("A", "Good", 10, NEWEST, null);
        Map<String, Object> broken = new java.util.LinkedHashMap<>();
        broken.put("artist", "not an object");
        broken.put("album", 42);
        broken.put("date", "yesterday");
        tracks.add(broken);
        stub.answer = LastFmStub.pageOf(tracks);

        assertThat(entries(provider())).extracting(e -> e.work().title()).containsExactly("Good - A");
    }

    @Test
    void anEmptyListeningHistoryIsAnEmptySnapshotNotAnError() {
        stub.answer = LastFmStub.page();

        assertThat(entries(provider())).isEmpty();
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS);
    }

    // ---- the request ----

    @Test
    void theSyncIsOneGetOfTheNewestTwoHundredScrobblesAndTheCheckAsksForOne() {
        provider().fetchActivity("ana_l");
        provider().checkAccountExists("ana_l");

        assertThat(stub.requests).hasSize(2);
        assertThat(stub.requests.get(0)).contains("method=user.getrecenttracks", "user=ana_l", "format=json", "limit=200")
                .contains("api_key=" + KEY);
        assertThat(stub.requests.get(1)).contains("method=user.getrecenttracks", "user=ana_l", "limit=1");
    }

    @Test
    void theRequestsAreSpacedToStayUnderTheRateLimit() {
        LastFmProvider spaced = provider(KEY, Duration.ofSeconds(2), Duration.ofMillis(150));
        long start = System.nanoTime();

        for (int i = 0; i < 4; i++) {
            spaced.checkAccountExists("ana_l");
        }

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(3 * 150 - 20));
    }

    // ---- account check and failures ----

    @Test
    void errorSixAndSeventeenMeanNoPublicAccountAndEverythingElseMeansUnavailable() {
        stub.knownUsers = List.of();
        assertThat(provider().checkAccountExists("nobody")).isEqualTo(AccountCheck.NOT_FOUND); // error 6 with a 404

        stub.knownUsers = List.of("ana_l");
        stub.privateUsers = List.of("ana_l");
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.NOT_FOUND); // error 17: private listening

        stub.reset();
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS);
    }

    @Test
    void aUserWhoVanishedOrWentPrivateMakesTheFetchThrowAccountNotFound() {
        stub.knownUsers = List.of();
        assertThatThrownBy(() -> provider().fetchActivity("ana_l"))
                .isInstanceOfSatisfying(LinkedAccountException.class,
                        e -> assertThat(e.problem()).isEqualTo(LinkedAccountException.Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER));

        stub.knownUsers = List.of("ana_l");
        stub.privateUsers = List.of("ana_l");
        assertThatThrownBy(() -> provider().fetchActivity("ana_l"))
                .isInstanceOfSatisfying(LinkedAccountException.class,
                        e -> assertThat(e.problem()).isEqualTo(LinkedAccountException.Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER));
    }

    @Test
    void anInvalidKeyIsUnavailableNotNotFoundAndTheFetchFails() {
        LastFmProvider wrongKey = provider(UUID.randomUUID().toString().replace("-", ""), Duration.ofSeconds(2), Duration.ZERO);

        assertThat(wrongKey.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> wrongKey.fetchActivity("ana_l"))
                .isInstanceOfSatisfying(LinkedAccountException.class,
                        e -> assertThat(e.problem()).isEqualTo(LinkedAccountException.Problem.PROVIDER_UNAVAILABLE));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 429, 500, 503})
    void anyErrorStatusIsUnavailableAndTheFetchThrowsProviderUnavailable(int code) {
        stub.status = code;

        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider().fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
    }

    @Test
    void lastFmRateLimitErrorTwentyNineIsUnavailable() {
        stub.forcedBody = LastFmStub.error(29, "Rate limit exceeded");

        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider().fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
    }

    @Test
    void anUnreadableOrUnexpectedBodyIsUnavailable() {
        stub.forcedBody = "<html>bad gateway</html>";
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider().fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);

        stub.forcedBody = "{\"something\":\"else\"}";
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    @Test
    void anOversizeAnswerIsDropped() {
        stub.forcedBody = "{\"recenttracks\":{\"track\":[]},\"pad\":\"" + "a".repeat(LastFmProvider.MAX_BODY_BYTES) + "\"}";

        assertThatThrownBy(() -> provider().fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
    }

    @Test
    void aSlowServerTimesOut() {
        stub.delayMillis = 2500;
        LastFmProvider quick = provider(KEY, Duration.ofMillis(300), Duration.ZERO);

        assertThatThrownBy(() -> quick.fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
        assertThat(quick.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    @Test
    void aRedirectIsNotFollowed() {
        stub.status = 302;

        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(stub.requests).hasSize(1);
    }

    // ---- the key ----

    @Test
    void withoutAKeyNothingIsSentAndTheProviderIsUnavailable() {
        for (String missing : new String[] {null, "", "   "}) {
            LastFmProvider keyless = provider(missing, Duration.ofSeconds(2), Duration.ZERO);

            assertThat(keyless.isConfigured()).isFalse();
            assertThat(keyless.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
            assertThatThrownBy(() -> keyless.fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
        }
        assertThat(stub.requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc&user=evil", "abc def", "key/../x", "k#frag", "k\r\nHost: evil", "short", "ключ-ключ-ключ"})
    void aKeyThatIsNotPlainAlphanumericIsNeverPutInAUrl(String badKey) {
        LastFmProvider odd = provider(badKey, Duration.ofSeconds(2), Duration.ZERO);

        assertThat(odd.isConfigured()).isFalse();
        assertThat(odd.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(stub.requests).isEmpty();
    }

    @Test
    void theKeyNeverReachesTheLogsOnAnyKindOfFailure() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            LastFmProvider wrongKey = provider(UUID.randomUUID().toString().replace("-", ""), Duration.ofSeconds(2), Duration.ZERO);
            wrongKey.checkAccountExists("ana_l"); // error 10 answered by Last.fm
            stub.status = 500;
            provider().checkAccountExists("ana_l");
            assertThatThrownBy(() -> provider().fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
            stub.status = 0;
            stub.forcedBody = "not json";
            assertThatThrownBy(() -> provider().fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
            stub.reset();
            stub.delayMillis = 2500;
            assertThatThrownBy(() -> provider(KEY, Duration.ofMillis(200), Duration.ZERO).fetchActivity("ana_l"))
                    .isInstanceOf(LinkedAccountException.class);
            // a connection refused: the stub's own port, closed
            LastFmStub gone = new LastFmStub(KEY);
            URI closedUri = gone.uri();
            gone.close();
            assertThatThrownBy(() -> new LastFmProvider(HttpCatalogProvider.newClient(), closedUri, KEY, CLOCK,
                    Duration.ofSeconds(1), Duration.ZERO, 10).fetchActivity("ana_l"))
                    .isInstanceOf(LinkedAccountException.class)
                    .satisfies(e -> assertThat(String.valueOf(e.getMessage()) + e.getCause()).doesNotContain(KEY));
        } finally {
            root.detachAppender(logs);
        }

        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent event : logs.list) {
            String everything = event.getFormattedMessage() + event.getThrowableProxy();
            assertThat(everything).doesNotContain(KEY).doesNotContain("api_key").doesNotContain("audioscrobbler");
        }
    }

    // ---- username rules ----

    @Test
    void validLastFmNamesPass() {
        for (String name : List.of("ab", "ana_l", "Ana-Souza", "x1234567890_-ab", "A1")) {
            assertThat(provider().validateUsernameFormat(name)).as(name).isEmpty();
        }
    }

    @Test
    void theLengthAndCharacterRulesReportTheProblem() {
        assertThat(provider().validateUsernameFormat("a")).contains(UsernameProblem.TOO_SHORT);
        assertThat(provider().validateUsernameFormat("a".repeat(16))).contains(UsernameProblem.TOO_LONG);
        assertThat(provider().validateUsernameFormat("1abc")).contains(UsernameProblem.INVALID_CHARACTERS); // letter first
        assertThat(provider().validateUsernameFormat("_abc")).contains(UsernameProblem.INVALID_CHARACTERS);
        assertThat(provider().validateUsernameFormat("ana l")).contains(UsernameProblem.INVALID_CHARACTERS);
    }

    @Test
    void theNameIsTrimmedButKeepsItsCase() {
        assertThat(provider().normalizeUsername("  Ana_L ")).isEqualTo("Ana_L");
        assertThat(provider().normalizeUsername(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../admin", "ana/rss", "ana?x=1", "ana&api_key=x", "ana#f", "ana@evil.com", "an a", "ana%2e", "ａｎａ",
            "ana\r\nHost: evil.com", "http://evil.example", "ana\u0000", "ana.l", "ana;", "", "a"})
    void aMaliciousNameIsRefusedByTheRulesAndNothingIsSent(String name) {
        LastFmProvider provider = provider();

        assertThat(provider.validateUsernameFormat(provider.normalizeUsername(name))).isPresent();
        assertThat(provider.checkAccountExists(name)).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider.fetchActivity(name)).isInstanceOf(LinkedAccountException.class);
        assertThat(stub.requests).isEmpty();
    }

    @AfterEach
    void nothingLeaksBetweenTests() {
        assertThat(stub.requests).noneMatch(query -> query.contains("evil"));
    }

    private static String toJson(Object value) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value);
    }
}
