package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivitySnapshot;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.media.domain.UsernameProblem;
import com.meuquarto.media.domain.UsernameRules;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.shared.domain.ValidationRule;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;

/** The template methods of the abstract class, through a tiny leaf and a local stub server (no real network). */
class RssActivityProviderTest {

    private static final String NS = "https://example.test/ns";

    /** Smallest possible leaf: a feed at /u/{name}/rss with an id in the x namespace and a pubDate. */
    private static final class SampleRss extends RssActivityProvider {

        private final int port;
        private final UnaryOperator<String> path;

        SampleRss(int port, Duration timeout, UnaryOperator<String> path) {
            super(HttpCatalogProvider.newClient(), timeout);
            this.port = port;
            this.path = path;
        }

        @Override
        public ProviderId id() {
            return ProviderId.LETTERBOXD;
        }

        @Override
        public Set<MediaCategory> categories() {
            return Set.of(MediaCategory.MOVIE);
        }

        @Override
        public List<ValidationRule<String, UsernameProblem>> usernameRules() {
            return UsernameRules.of(2, 15, "[a-z0-9_]+");
        }

        @Override
        protected URI baseUri() {
            return URI.create("http://127.0.0.1:" + port);
        }

        @Override
        protected String feedPath(String encodedUsername) {
            return path.apply(encodedUsername);
        }

        @Override
        protected Optional<ActivityEntry> toEntry(Element item) {
            var ids = item.getElementsByTagNameNS(NS, "id");
            if (ids.getLength() == 0) {
                return Optional.empty();
            }
            String id = ids.item(0).getTextContent().trim();
            String title = item.getElementsByTagName("title").item(0).getTextContent().trim();
            String date = item.getElementsByTagName("pubDate").item(0).getTextContent().trim();
            long numeric = Long.parseLong(id); // throws on a broken item: the template skips just that one
            CatalogEntry work = new CatalogEntry(MediaCategory.MOVIE, "TMDB", Long.toString(numeric), title, null, null, false);
            return Optional.of(new ActivityEntry(work, ActivityType.FINISHED,
                    ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()));
        }
    }

    private HttpServer server;
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile byte[] body = new byte[0];
    private volatile long delayMillis = 0;
    private volatile String redirectTo = null;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()));
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                if (redirectTo != null) {
                    exchange.getResponseHeaders().add("Location", redirectTo);
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
                    if (body.length > 0) {
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(body);
                        }
                    }
                }
            } catch (IOException clientGaveUp) {
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

    private ActivityProvider provider() {
        return provider(Duration.ofSeconds(2), name -> "/u/" + name + "/rss");
    }

    private ActivityProvider provider(Duration timeout, UnaryOperator<String> path) {
        return new SampleRss(server.getAddress().getPort(), timeout, path);
    }

    private void serve(String xml) {
        body = xml.getBytes(StandardCharsets.UTF_8);
    }

    private void serveFixture() throws IOException {
        body = Files.readAllBytes(Path.of("src/test/resources/fixtures/sample-feed.xml"));
    }

    @Test
    void fetchBuildsEntriesFromTheFixtureAndSkipsWhatItCannotMap() throws IOException {
        serveFixture();

        ActivitySnapshot snapshot = provider().fetchActivity("leandro");

        assertThat(snapshot.entries()).extracting(entry -> entry.work().title())
                .containsExactly("The Matrix, 1999", "Alien, 1979");
        assertThat(snapshot.entries().getFirst().work().externalId()).isEqualTo("603");
        assertThat(snapshot.entries().getFirst().type()).isEqualTo(ActivityType.FINISHED);
        assertThat(snapshot.entries().getFirst().occurredAt()).hasToString("2026-10-05T20:00:00Z");
        assertThat(requestedPaths).containsExactly("/u/leandro/rss");
    }

    @Test
    void checkReportsExistsNotFoundAndUnavailable() throws IOException {
        serveFixture();
        assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.EXISTS);

        status = 404;
        body = new byte[0];
        assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.NOT_FOUND);

        status = 500;
        assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);

        status = 200;
        serve("this is not xml");
        assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    @Test
    void aProviderFailureOnFetchIsOneKindOfException() {
        status = 503;

        assertThatThrownBy(() -> provider().fetchActivity("leandro"))
                .isInstanceOfSatisfying(LinkedAccountException.class,
                        e -> assertThat(e.problem()).isEqualTo(Problem.PROVIDER_UNAVAILABLE));
    }

    @Test
    void aSlowPlatformTimesOut() throws IOException {
        serveFixture();
        delayMillis = 1500;
        ActivityProvider impatient = provider(Duration.ofMillis(200), name -> "/u/" + name + "/rss");

        assertThat(impatient.checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> impatient.fetchActivity("leandro")).isInstanceOf(LinkedAccountException.class);
    }

    @Test
    void redirectsAreNotFollowed() {
        redirectTo = "/somewhere-else";

        assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(requestedPaths).containsExactly("/u/leandro/rss");
    }

    @Test
    void aBodyOverTheCapIsDropped() {
        body = ("<rss><channel>" + "<item><title>x</title></item>".repeat(100_000) + "</channel></rss>")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(body.length).isGreaterThan(RssActivityProvider.MAX_BODY_BYTES);

        assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider().fetchActivity("leandro")).isInstanceOf(LinkedAccountException.class);
    }

    @Test
    void onlyTheFirstItemsAreMapped() {
        StringBuilder xml = new StringBuilder("<rss xmlns:x=\"" + NS + "\"><channel>");
        for (int i = 0; i < RssActivityProvider.MAX_ITEMS + 50; i++) {
            xml.append("<item><title>t").append(i).append("</title><x:id>").append(i + 1)
                    .append("</x:id><pubDate>Mon, 05 Oct 2026 20:00:00 +0000</pubDate></item>");
        }
        serve(xml.append("</channel></rss>").toString());

        assertThat(provider().fetchActivity("leandro").entries()).hasSize(RssActivityProvider.MAX_ITEMS);
    }

    // ---- XXE: a DOCTYPE is refused outright ----

    @Test
    void anExternalEntityIsNeverResolved() throws IOException {
        HttpServer victim = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        List<String> victimHits = new CopyOnWriteArrayList<>();
        victim.createContext("/", exchange -> {
            victimHits.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        victim.start();
        try {
            serve("<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY xxe SYSTEM \"http://127.0.0.1:"
                    + victim.getAddress().getPort() + "/secret\">]><rss><channel><item><title>&xxe;</title></item></channel></rss>");

            assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);
            assertThatThrownBy(() -> provider().fetchActivity("leandro")).isInstanceOf(LinkedAccountException.class);
            assertThat(victimHits).isEmpty();
        } finally {
            victim.stop(0);
        }
    }

    @Test
    void aFileEntityAndAnExternalDtdAreRefused() {
        serve("<?xml version=\"1.0\"?><!DOCTYPE rss SYSTEM \"http://127.0.0.1:1/evil.dtd\"><rss><channel/></rss>");
        assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);

        serve("<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY f SYSTEM \"file:///etc/passwd\">]><rss>&f;</rss>");
        assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    @Test
    void entityExpansionBombsAreRefused() {
        serve("<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">"
                + "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">]><rss><channel><item><title>&c;</title></item></channel></rss>");

        assertThat(provider().checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    // ---- SSRF and injection through the username ----

    @ParameterizedTest
    @ValueSource(strings = {"../admin", "a?b=c", "a#f", "x@evil.com", "a/b", "a b", "a%2e%2e", "ＬＥＡＮ", "café",
            "a\r\nHost: evil.com", "this_username_is_far_too_long_to_be_real", ""})
    void anInvalidUsernameNeverReachesTheNetwork(String name) throws IOException {
        serveFixture();

        assertThat(provider().checkAccountExists(name)).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider().fetchActivity(name)).isInstanceOf(LinkedAccountException.class);
        assertThat(requestedPaths).isEmpty();
    }

    @Test
    void aNullUsernameIsRefusedToo() {
        assertThat(provider().checkAccountExists(null)).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(requestedPaths).isEmpty();
    }

    @Test
    void aLeafWhosePathWouldMoveTheRequestToAnotherHostIsRefused() throws IOException {
        serveFixture();
        ActivityProvider sneaky = provider(Duration.ofSeconds(2), name -> "@evil.example/" + name);

        assertThat(sneaky.checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> sneaky.fetchActivity("leandro")).isInstanceOf(LinkedAccountException.class);
        assertThat(requestedPaths).isEmpty();

        ActivityProvider withQuery = provider(Duration.ofSeconds(2), name -> "/u/" + name + "?x=1");
        assertThat(withQuery.checkAccountExists("leandro")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(requestedPaths).isEmpty();
    }
}
