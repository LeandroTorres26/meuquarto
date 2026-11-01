package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.MediaKey;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.media.domain.UsernameProblem;
import com.meuquarto.shared.domain.MediaCategory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The Letterboxd leaf: feed mapping from a fixture shaped like the real RSS, and its username rules. No real network. */
class LetterboxdProviderTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/letterboxd/diary.xml");

    private HttpServer server;
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile byte[] body = new byte[0];

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getRawPath());
            try {
                exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ActivityProvider provider() {
        URI stub = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new LetterboxdProvider(HttpCatalogProvider.newClient(), stub, Duration.ofSeconds(2));
    }

    private void serveFixture() throws IOException {
        body = Files.readAllBytes(FIXTURE);
    }

    private void serve(String xml) {
        body = xml.getBytes(StandardCharsets.UTF_8);
    }

    private static String feed(String items) {
        return "<rss version=\"2.0\" xmlns:letterboxd=\"https://letterboxd.com\" xmlns:tmdb=\"https://themoviedb.org\">"
                + "<channel>" + items + "</channel></rss>";
    }

    // ---- identity ----

    @Test
    void itIsTheLetterboxdMovieProviderPointingAtTheRealHost() {
        LetterboxdProvider real = new LetterboxdProvider();

        assertThat(real.id()).isEqualTo(ProviderId.LETTERBOXD);
        assertThat(real.categories()).containsExactly(MediaCategory.MOVIE);
        assertThat(real.baseUri()).isEqualTo(URI.create("https://letterboxd.com"));
        assertThat(real.feedPath("ana_l")).isEqualTo("/ana_l/rss/");
    }

    // ---- mapping ----

    @Test
    void everyDiaryEntryWithATmdbMovieBecomesAnActivityAndTheRestIsSkipped() throws IOException {
        serveFixture();

        List<ActivityEntry> entries = provider().fetchActivity("ana_l").entries();

        assertThat(requestedPaths).containsExactly("/ana_l/rss/");
        assertThat(entries).extracting(e -> e.work().externalId()).containsExactly("603", "603", "348", "496243", "777");
        // skipped: no TMDB id, a list, a series (tvId), a non-numeric id
        assertThat(entries).extracting(e -> e.work().title()).doesNotContain("Home Video", "Best of the nineties",
                "Chernobyl", "Broken");
    }

    @Test
    void theWorkIsTheCanonicalTmdbMovieSoItMatchesAManualAddition() throws IOException {
        serveFixture();

        ActivityEntry matrix = provider().fetchActivity("ana_l").entries().getFirst();

        assertThat(matrix.work().key()).isEqualTo(new MediaKey(MediaCategory.MOVIE, "TMDB", "603"));
        assertThat(matrix.work().title()).isEqualTo("The Matrix");
        assertThat(matrix.work().year()).isEqualTo(1999);
        assertThat(matrix.work().adult()).isFalse();
        assertThat(matrix.type()).isEqualTo(ActivityType.FINISHED);
    }

    @Test
    void theWatchedDateIsTheTimeOfTheActivityAndARewatchIsAnotherActivity() throws IOException {
        serveFixture();

        List<ActivityEntry> entries = provider().fetchActivity("ana_l").entries();

        assertThat(entries.get(0).occurredAt()).hasToString("2026-10-10T00:00:00Z");
        assertThat(entries.get(1).occurredAt()).hasToString("2026-10-19T00:00:00Z"); // same film, rewatch
        assertThat(entries.get(0).work().key()).isEqualTo(entries.get(1).work().key());
    }

    @Test
    void withoutAWatchedDateThePublicationTimeIsUsed() throws IOException {
        serveFixture();

        ActivityEntry alien = provider().fetchActivity("ana_l").entries().get(2);

        assertThat(alien.work().externalId()).isEqualTo("348");
        assertThat(alien.occurredAt()).hasToString("2026-10-07T20:00:00Z");
    }

    @Test
    void anEntryWithNoUsableDateAtAllIsSkipped() {
        serve(feed("""
                <item><guid>letterboxd-watch-1</guid><letterboxd:filmTitle>No date</letterboxd:filmTitle>
                <tmdb:movieId>10</tmdb:movieId></item>
                <item><guid>letterboxd-watch-2</guid><pubDate>not a date</pubDate><letterboxd:watchedDate>2026-13-45</letterboxd:watchedDate>
                <letterboxd:filmTitle>Bad dates</letterboxd:filmTitle><tmdb:movieId>11</tmdb:movieId></item>
                <item><guid>letterboxd-watch-3</guid><letterboxd:watchedDate>2026-01-02</letterboxd:watchedDate>
                <letterboxd:filmTitle>Good</letterboxd:filmTitle><tmdb:movieId>12</tmdb:movieId></item>
                """));

        assertThat(provider().fetchActivity("ana_l").entries()).extracting(e -> e.work().title()).containsExactly("Good");
    }

    @Test
    void theNamespacePrefixDoesNotMatterOnlyTheElementName() {
        serve("<rss xmlns:lb=\"https://letterboxd.com/other\" xmlns:t=\"https://example.test/tmdb\"><channel>"
                + "<item><guid>letterboxd-review-9</guid><lb:watchedDate>2026-03-04</lb:watchedDate>"
                + "<lb:filmTitle>Prefix</lb:filmTitle><t:movieId>42</t:movieId></item></channel></rss>");

        assertThat(provider().fetchActivity("ana_l").entries()).extracting(e -> e.work().externalId()).containsExactly("42");
    }

    @Test
    void aMissingFilmTitleOrIdSkipsOnlyThatItemAndTheSyncGoesOn() {
        serve(feed("""
                <item><guid>letterboxd-watch-1</guid><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate><tmdb:movieId>1</tmdb:movieId></item>
                <item><guid>letterboxd-watch-2</guid><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate><letterboxd:filmTitle>No id</letterboxd:filmTitle></item>
                <item><guid>letterboxd-watch-3</guid><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate>
                <letterboxd:filmTitle>Zero</letterboxd:filmTitle><tmdb:movieId>0</tmdb:movieId></item>
                <item><guid>letterboxd-watch-4</guid><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate>
                <letterboxd:filmTitle>Huge</letterboxd:filmTitle><tmdb:movieId>99999999999999999999</tmdb:movieId></item>
                <item><letterboxd:filmTitle>No guid</letterboxd:filmTitle><tmdb:movieId>5</tmdb:movieId><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate></item>
                <item><guid>letterboxd-watch-5</guid><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate>
                <letterboxd:filmTitle>Fine</letterboxd:filmTitle><tmdb:movieId>00007</tmdb:movieId></item>
                """));

        List<ActivityEntry> entries = provider().fetchActivity("ana_l").entries();

        assertThat(entries).extracting(e -> e.work().title()).containsExactly("Fine");
        assertThat(entries.getFirst().work().externalId()).isEqualTo("7"); // normalized, no leading zeros
    }

    @Test
    void anEmptyFeedIsAnEmptySnapshotNotAnError() {
        serve(feed(""));

        assertThat(provider().fetchActivity("ana_l").entries()).isEmpty();
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS);
    }

    // ---- images: a fixed allowlist of hosts ----

    @Test
    void theLetterboxdPosterIsKeptWhenItIsHttpsOnTheirCdn() throws IOException {
        serveFixture();

        assertThat(provider().fetchActivity("ana_l").entries().getFirst().work().imageUrl())
                .isEqualTo("https://a.ltrbxd.com/resized/film-poster/5/1/5/1/51-matrix-0-230-0-345-crop.jpg?v=abc123");
    }

    @Test
    void anImageFromAnotherHostOrWithoutHttpsOrMissingGivesNoImage() throws IOException {
        serveFixture();

        List<ActivityEntry> entries = provider().fetchActivity("ana_l").entries();

        assertThat(entries.get(2).work().imageUrl()).isNull(); // Alien: no <img>
        assertThat(entries.get(3).work().imageUrl()).isNull(); // Parasite: evil.example
        assertThat(entries.get(4).work().imageUrl()).isNull(); // hostile: http, not https
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example/p.jpg",
            "http://a.ltrbxd.com/p.jpg",
            "//a.ltrbxd.com/p.jpg",
            "https://a.ltrbxd.com.evil.example/p.jpg",
            "https://evil.example/a.ltrbxd.com/p.jpg",
            "https://a.ltrbxd.com@evil.example/p.jpg",
            "https://evil.example@a.ltrbxd.com/p.jpg",
            "https://a.ltrbxd.com:8443/p.jpg",
            "https://127.0.0.1/p.jpg",
            "https://localhost/p.jpg",
            "javascript:alert(1)",
            "data:image/png;base64,AAAA",
            "file:///etc/passwd",
            "not a url"})
    void aPosterOutsideTheAllowlistIsNeverKept(String src) {
        serve(feed("<item><guid>letterboxd-watch-1</guid><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate>"
                + "<letterboxd:filmTitle>Film</letterboxd:filmTitle><tmdb:movieId>5</tmdb:movieId>"
                + "<description><![CDATA[<img src=\"" + src + "\"/>]]></description></item>"));

        assertThat(provider().fetchActivity("ana_l").entries().getFirst().work().imageUrl()).isNull();
    }

    @Test
    void theTmdbImageHostIsAlsoAllowedAndTheFirstImageWins() {
        serve(feed("<item><guid>letterboxd-watch-1</guid><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate>"
                + "<letterboxd:filmTitle>Film</letterboxd:filmTitle><tmdb:movieId>5</tmdb:movieId>"
                + "<description><![CDATA[<IMG alt='x' SRC='https://image.tmdb.org/t/p/w342/a.jpg'><img src=\"https://a.ltrbxd.com/b.jpg\">]]>"
                + "</description></item>"));

        assertThat(provider().fetchActivity("ana_l").entries().getFirst().work().imageUrl())
                .isEqualTo("https://image.tmdb.org/t/p/w342/a.jpg");
    }

    // ---- escaping and bounds ----

    @Test
    void markupInATitleStaysPlainTextAndTheBadYearIsDropped() throws IOException {
        serveFixture();

        ActivityEntry hostile = provider().fetchActivity("ana_l").entries().get(4);

        assertThat(hostile.work().title()).isEqualTo("<script>alert(1)</script><b>Bold</b>"); // data, escaped by the UI
        assertThat(hostile.work().year()).isNull(); // "20x4"
    }

    @Test
    void aHugeTitleIsCut() {
        serve(feed("<item><guid>letterboxd-watch-1</guid><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate>"
                + "<letterboxd:filmTitle>" + "x".repeat(5000) + "</letterboxd:filmTitle><tmdb:movieId>5</tmdb:movieId></item>"));

        assertThat(provider().fetchActivity("ana_l").entries().getFirst().work().title()).hasSize(300);
    }

    @Test
    void anAbsurdYearIsDropped() {
        serve(feed("<item><guid>letterboxd-watch-1</guid><letterboxd:watchedDate>2026-01-01</letterboxd:watchedDate>"
                + "<letterboxd:filmTitle>Old</letterboxd:filmTitle><letterboxd:filmYear>0001</letterboxd:filmYear>"
                + "<tmdb:movieId>5</tmdb:movieId></item>"));

        assertThat(provider().fetchActivity("ana_l").entries().getFirst().work().year()).isNull();
    }

    // ---- account check (inherited template) ----

    @Test
    void theAccountCheckIsDecidedByTheFeed() throws IOException {
        serveFixture();
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS);

        status = 404;
        body = new byte[0];
        assertThat(provider().checkAccountExists("ghost_user")).isEqualTo(AccountCheck.NOT_FOUND);

        status = 503;
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider().fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
    }

    // ---- username rules ----

    @Test
    void usernamesAreTrimmedAndLowerCased() {
        assertThat(provider().normalizeUsername("  Ana_L ")).isEqualTo("ana_l");
        assertThat(provider().normalizeUsername(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "ana_l", "a_1_b_2_c_3_d_4", "user123", "_ab", "123456789012345"})
    void validUsernamesPass(String name) {
        assertThat(provider().validateUsernameFormat(name)).isEmpty();
    }

    @Test
    void theLengthAndCharacterRulesReportTheProblem() {
        assertThat(provider().validateUsernameFormat("a")).contains(UsernameProblem.TOO_SHORT);
        assertThat(provider().validateUsernameFormat("a".repeat(16))).contains(UsernameProblem.TOO_LONG);
        assertThat(provider().validateUsernameFormat("ana-l")).contains(UsernameProblem.INVALID_CHARACTERS);
        assertThat(provider().validateUsernameFormat("Ana_L")).contains(UsernameProblem.INVALID_CHARACTERS); // normalize first
    }

    @ParameterizedTest
    @ValueSource(strings = {"../admin", "ana/rss", "a?b=c", "a#f", "x@evil.com", "a b", "a%2e%2e", "ＬＥＡＮ", "café",
            "a\r\nHost: evil.com", "ana.l", "http://evil.example", "ana\u0000", "this_name_is_far_too_long", ""})
    void aMaliciousUsernameNeverReachesTheNetwork(String name) throws IOException {
        serveFixture();

        assertThat(provider().validateUsernameFormat(name)).isPresent();
        assertThat(provider().checkAccountExists(name)).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider().fetchActivity(name)).isInstanceOf(LinkedAccountException.class);
        assertThat(requestedPaths).isEmpty();
    }

    @Test
    void theRequestAlwaysGoesToTheFixedPath() throws IOException {
        serveFixture();

        provider().fetchActivity("ana_l");
        provider().checkAccountExists("bob_2");

        assertThat(requestedPaths).containsExactly("/ana_l/rss/", "/bob_2/rss/");
    }
}
