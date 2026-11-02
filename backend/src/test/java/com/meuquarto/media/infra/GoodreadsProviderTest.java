package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
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

/** The Goodreads leaf against real-shaped fixtures and a local stub server (no real network). */
class GoodreadsProviderTest {

    private static final Path READ = Path.of("src/test/resources/fixtures/goodreads/read.xml");
    private static final Path READING = Path.of("src/test/resources/fixtures/goodreads/currently-reading.xml");

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile byte[] readBody = new byte[0];
    private volatile byte[] readingBody = new byte[0];
    private volatile int status = 200;
    private volatile long delayMillis = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            requests.add(exchange.getRequestURI().getRawPath() + "?" + query);
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] body = query != null && query.startsWith("shelf=read") ? readBody : readingBody;
                if (status != 200) {
                    exchange.sendResponseHeaders(status, -1);
                } else {
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            } catch (IOException | InterruptedException clientGaveUp) {
                // a timed-out client closes the connection while the stub is still answering
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

    private GoodreadsProvider provider() {
        return new GoodreadsProvider(HttpCatalogProvider.newClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(2));
    }

    private void serveFixtures() throws IOException {
        readBody = Files.readAllBytes(READ);
        readingBody = Files.readAllBytes(READING);
    }

    private static byte[] feed(String items) {
        return ("<rss version=\"2.0\"><channel>" + items + "</channel></rss>").getBytes(StandardCharsets.UTF_8);
    }

    // ---- identity ----

    @Test
    void itIsTheGoodreadsBookProviderPointingAtTheRealHost() {
        GoodreadsProvider real = new GoodreadsProvider();

        assertThat(real.id()).isEqualTo(ProviderId.GOODREADS);
        assertThat(real.categories()).containsExactly(MediaCategory.BOOK);
        assertThat(real.baseUri()).isEqualTo(URI.create("https://www.goodreads.com"));
        assertThat(real.feedPath("12345678")).isEqualTo("/review/list_rss/12345678");
        assertThat(real.feedQueries()).containsExactly("shelf=read&per_page=200", "shelf=currently-reading&per_page=200");
    }

    // ---- mapping ----

    @Test
    void bothShelvesAreReadAndTheBrokenItemsAreSkipped() throws IOException {
        serveFixtures();

        List<ActivityEntry> entries = provider().fetchActivity("12345678").entries();

        assertThat(requests).containsExactly("/review/list_rss/12345678?shelf=read&per_page=200",
                "/review/list_rss/12345678?shelf=currently-reading&per_page=200");
        assertThat(entries).extracting(e -> e.work().externalId())
                .containsExactly("234225", "5907", "777", "22328", "234225");
        assertThat(entries).extracting(e -> e.work().title()).doesNotContain("Mystery", "Broken", "Undated");
    }

    @Test
    void theReadShelfIsFinishedAtTheDateReadOrElseTheDateAdded() throws IOException {
        serveFixtures();

        List<ActivityEntry> entries = provider().fetchActivity("12345678").entries();

        assertThat(entries.get(0).type()).isEqualTo(ActivityType.FINISHED);
        assertThat(entries.get(0).occurredAt()).hasToString("2026-10-10T07:00:00Z"); // user_read_at, -0700
        assertThat(entries.get(1).type()).isEqualTo(ActivityType.FINISHED);
        assertThat(entries.get(1).occurredAt()).hasToString("2026-10-04T12:30:00Z"); // no read date: date added
    }

    @Test
    void theCurrentlyReadingShelfIsInProgressAtTheDateAdded() throws IOException {
        serveFixtures();

        List<ActivityEntry> entries = provider().fetchActivity("12345678").entries();

        assertThat(entries.get(3).type()).isEqualTo(ActivityType.IN_PROGRESS);
        assertThat(entries.get(3).occurredAt()).hasToString("2026-10-20T11:00:00Z");
        assertThat(entries.get(3).work().title()).isEqualTo("Neuromancer");
    }

    @Test
    void theWorkIsAProvisionalGoodreadsBookKeyedByTheBookIdNotAGuess() throws IOException {
        serveFixtures();

        ActivityEntry dune = provider().fetchActivity("12345678").entries().getFirst();

        assertThat(dune.work().key()).isEqualTo(new MediaKey(MediaCategory.BOOK, "GOODREADS", "234225"));
        assertThat(dune.work().title()).isEqualTo("Dune (Dune, #1)");
        assertThat(dune.work().year()).isEqualTo(1965);
        assertThat(dune.work().adult()).isFalse();
    }

    @Test
    void theSameBookOnBothShelvesIsTheSameWorkWithDifferentActivities() throws IOException {
        serveFixtures();

        List<ActivityEntry> entries = provider().fetchActivity("12345678").entries();

        assertThat(entries.get(0).work().key()).isEqualTo(entries.get(4).work().key());
        assertThat(entries.get(0).type()).isNotEqualTo(entries.get(4).type());
    }

    @Test
    void anEmptyShelfIsAnEmptySnapshotNotAnError() {
        readBody = feed("");
        readingBody = feed("");

        assertThat(provider().fetchActivity("12345678").entries()).isEmpty();
        assertThat(provider().checkAccountExists("12345678")).isEqualTo(AccountCheck.EXISTS);
    }

    // ---- images: a fixed allowlist of hosts ----

    @Test
    void theCoverComesOnlyFromGoodreadsImageHostsAndPlaceholdersAreNoCover() throws IOException {
        serveFixtures();

        List<ActivityEntry> entries = provider().fetchActivity("12345678").entries();

        assertThat(entries.get(0).work().imageUrl()).isEqualTo(
                "https://i.gr-assets.com/images/S/compressed.photo.goodreads.com/books/1555447414l/44767458._SX98_.jpg");
        assertThat(entries.get(1).work().imageUrl()).isNull(); // nophoto placeholder
        assertThat(entries.get(2).work().imageUrl()).isNull(); // evil host, http, look-alike
        assertThat(entries.get(3).work().imageUrl()).isEqualTo("https://images.gr-assets.com/books/1285/22328.jpg");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example/c.jpg",
            "http://i.gr-assets.com/c.jpg",
            "//i.gr-assets.com/c.jpg",
            "https://i.gr-assets.com.evil.example/c.jpg",
            "https://evil.example/i.gr-assets.com/c.jpg",
            "https://i.gr-assets.com@evil.example/c.jpg",
            "https://evil.example@i.gr-assets.com/c.jpg",
            "https://i.gr-assets.com:8443/c.jpg",
            "https://127.0.0.1/c.jpg",
            "javascript:alert(1)",
            "data:image/png;base64,AAAA",
            "file:///etc/passwd",
            "not a url"})
    void aCoverOutsideTheAllowlistIsNeverKept(String src) {
        readBody = feed("<item><title>Book</title><book_id>5</book_id><book_image_url>" + src.replace("&", "&amp;")
                + "</book_image_url><user_read_at>Fri, 02 Oct 2026 00:00:00 +0000</user_read_at></item>");
        readingBody = feed("");

        assertThat(provider().fetchActivity("12345678").entries().getFirst().work().imageUrl()).isNull();
    }

    // ---- escaping and bounds ----

    @Test
    void markupStaysPlainTextTheDescriptionIsNeverUsedAndTheBadYearIsDropped() throws IOException {
        serveFixtures();

        ActivityEntry hostile = provider().fetchActivity("12345678").entries().get(2);

        assertThat(hostile.work().title()).isEqualTo("<script>alert(1)</script><b>Bold</b>"); // data, escaped by the UI
        assertThat(hostile.work().year()).isNull(); // "20x4"
        assertThat(provider().fetchActivity("12345678").entries())
                .noneMatch(e -> e.work().title().contains("A desert planet"));
    }

    @Test
    void aHugeTitleIsCutAndAnAbsurdYearDropped() {
        readBody = feed("<item><title>" + "x".repeat(5000) + "</title><book_id>5</book_id><book_published>0001</book_published>"
                + "<user_read_at>Fri, 02 Oct 2026 00:00:00 +0000</user_read_at></item>");
        readingBody = feed("");

        ActivityEntry entry = provider().fetchActivity("12345678").entries().getFirst();

        assertThat(entry.work().title()).hasSize(300);
        assertThat(entry.work().year()).isNull();
    }

    @Test
    void aNestedBookElementCannotShadowTheItemFields() {
        readBody = feed("<item><book><title>Nested</title><book_id>1</book_id></book><title>Outer</title><book_id>9</book_id>"
                + "<user_read_at>Fri, 02 Oct 2026 00:00:00 +0000</user_read_at></item>");
        readingBody = feed("");

        ActivityEntry entry = provider().fetchActivity("12345678").entries().getFirst();

        assertThat(entry.work().title()).isEqualTo("Outer");
        assertThat(entry.work().externalId()).isEqualTo("9");
    }

    // ---- account check and failures (inherited template) ----

    @Test
    void theAccountCheckIsDecidedByTheFeed() throws IOException {
        serveFixtures();
        assertThat(provider().checkAccountExists("12345678")).isEqualTo(AccountCheck.EXISTS);

        status = 404;
        assertThat(provider().checkAccountExists("99999999")).isEqualTo(AccountCheck.NOT_FOUND);

        status = 503;
        assertThat(provider().checkAccountExists("12345678")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider().fetchActivity("12345678")).isInstanceOf(LinkedAccountException.class);
    }

    @Test
    void ifOneShelfFailsTheWholeFetchFailsSoNothingIsHalfApplied() throws IOException {
        serveFixtures();
        readingBody = "<not-xml".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> provider().fetchActivity("12345678")).isInstanceOf(LinkedAccountException.class);
    }

    @Test
    void aDoctypeFeedIsRefusedAgainstXxe() {
        readBody = ("<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<rss><channel><item><title>&x;</title><book_id>1</book_id></item></channel></rss>")
                .getBytes(StandardCharsets.UTF_8);
        readingBody = feed("");

        assertThatThrownBy(() -> provider().fetchActivity("12345678")).isInstanceOf(LinkedAccountException.class);
        assertThat(provider().checkAccountExists("12345678")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    @Test
    void anOversizeFeedIsDropped() {
        readBody = new byte[RssActivityProvider.MAX_BODY_BYTES + 10];
        java.util.Arrays.fill(readBody, (byte) 'a');
        readingBody = feed("");

        assertThatThrownBy(() -> provider().fetchActivity("12345678")).isInstanceOf(LinkedAccountException.class);
    }

    @Test
    void aSlowServerTimesOut() throws IOException {
        serveFixtures();
        delayMillis = 3000;
        GoodreadsProvider quick = new GoodreadsProvider(HttpCatalogProvider.newClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofMillis(300));

        assertThatThrownBy(() -> quick.fetchActivity("12345678")).isInstanceOf(LinkedAccountException.class);
        assertThat(quick.checkAccountExists("12345678")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    // ---- identifier rules ----

    @Test
    void theProfileAddressSuffixIsDroppedAndTheIdIsKept() {
        assertThat(provider().normalizeUsername("  12345678-ana-souza ")).isEqualTo("12345678");
        assertThat(provider().normalizeUsername("12345678")).isEqualTo("12345678");
        assertThat(provider().normalizeUsername(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "12345678", "123456789012"})
    void validIdsPass(String id) {
        assertThat(provider().validateUsernameFormat(id)).isEmpty();
    }

    @Test
    void theLengthAndCharacterRulesReportTheProblem() {
        assertThat(provider().validateUsernameFormat("")).contains(UsernameProblem.TOO_SHORT);
        assertThat(provider().validateUsernameFormat("1".repeat(13))).contains(UsernameProblem.TOO_LONG);
        assertThat(provider().validateUsernameFormat("ana")).contains(UsernameProblem.INVALID_CHARACTERS);
        assertThat(provider().validateUsernameFormat("12-ana")).contains(UsernameProblem.INVALID_CHARACTERS); // normalize first
    }

    @ParameterizedTest
    @ValueSource(strings = {"../admin", "123/rss", "1?shelf=x", "1#f", "1@evil.com", "1 2", "1%2e", "１２３", "-123",
            "123-", "123-a b", "123-é", "1\r\nHost: evil.com", "1.5", "http://evil.example", "1\u0000",
            "1234567890123", "ana", ""})
    void aMaliciousIdNeverReachesTheNetwork(String raw) throws IOException {
        serveFixtures();
        String name = provider().normalizeUsername(raw);

        assertThat(provider().validateUsernameFormat(name)).isPresent();
        assertThat(provider().checkAccountExists(name)).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider().fetchActivity(name)).isInstanceOf(LinkedAccountException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void theRequestAlwaysGoesToTheFixedPathAndHost() throws IOException {
        serveFixtures();

        provider().checkAccountExists("42");

        assertThat(requests).containsExactly("/review/list_rss/42?shelf=read&per_page=200");
    }
}
