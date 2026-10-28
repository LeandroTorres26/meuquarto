package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.shared.domain.MediaCategory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The template method of the abstract class, exercised through a tiny subclass and a local stub server. */
class HttpCatalogProviderTest {

    /** Smallest possible leaf: the body of the response is the title. */
    private static final class EchoCatalog extends HttpCatalogProvider {

        final String base;
        boolean available = true;

        EchoCatalog(String base, Clock clock, Duration ttl, Duration timeout) {
            super(newClient(), clock, ttl, timeout);
            this.base = base;
        }

        @Override
        public String id() {
            return "ECHO";
        }

        @Override
        public boolean serves(MediaCategory category) {
            return category == MediaCategory.BOOK;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        protected URI searchUri(MediaCategory category, String query) {
            return URI.create(base + "/search?q=" + query);
        }

        @Override
        protected URI fetchUri(MediaCategory category, String externalId) {
            return URI.create(base + "/item/" + externalId);
        }

        @Override
        protected List<CatalogEntry> parseSearch(MediaCategory category, String body) {
            if (body.equals("boom")) {
                throw new IllegalStateException("cannot parse");
            }
            return List.of(new CatalogEntry(category, id(), "1", body, null, null, false));
        }

        @Override
        protected Optional<CatalogEntry> parseFetch(MediaCategory category, String body) {
            return Optional.of(new CatalogEntry(category, id(), "1", body, null, null, false));
        }

        @Override
        protected void authorize(HttpRequest.Builder request) {
            request.header("X-Test", "yes");
        }
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>("hello");
    private final AtomicReference<String> sentHeader = new AtomicReference<>();
    private volatile long delayMillis = 0;
    private final MutableClock clock = new MutableClock();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            sentHeader.set(exchange.getRequestHeaders().getFirst("X-Test"));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException clientGaveUp) {
                // the timeout test closes the connection first
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

    private EchoCatalog catalog() {
        return new EchoCatalog("http://127.0.0.1:" + server.getAddress().getPort(), clock,
                Duration.ofMinutes(10), Duration.ofMillis(400));
    }

    @Test
    void searchCallsTheLeafStepsAndReturnsTheParsedResult() {
        List<CatalogEntry> results = catalog().search(MediaCategory.BOOK, "dune");

        assertThat(results).extracting(CatalogEntry::title).containsExactly("hello");
        assertThat(sentHeader.get()).isEqualTo("yes"); // the authorize hook ran
    }

    @Test
    void secondIdenticalCallIsServedFromTheCacheUntilTheTtlExpires() {
        EchoCatalog catalog = catalog();

        catalog.search(MediaCategory.BOOK, "dune");
        catalog.search(MediaCategory.BOOK, "dune");
        assertThat(hits).hasValue(1);

        catalog.search(MediaCategory.BOOK, "other");
        assertThat(hits).hasValue(2);

        clock.now = clock.now.plus(Duration.ofMinutes(11));
        catalog.search(MediaCategory.BOOK, "dune");
        assertThat(hits).hasValue(3);
    }

    @Test
    void failuresBecomeEmptyAnswersAndAreNotCached() {
        EchoCatalog catalog = catalog();
        status.set(500);

        assertThat(catalog.search(MediaCategory.BOOK, "dune")).isEmpty();
        assertThat(catalog.fetch(MediaCategory.BOOK, "1")).isEmpty();

        status.set(200);
        assertThat(catalog.search(MediaCategory.BOOK, "dune")).hasSize(1);
    }

    @Test
    void slowResponseTimesOutIntoAnEmptyAnswer() {
        delayMillis = 1500;

        assertThat(catalog().search(MediaCategory.BOOK, "dune")).isEmpty();
    }

    @Test
    void unreadableBodyIsAnEmptyAnswer() {
        body.set("boom");

        assertThat(catalog().search(MediaCategory.BOOK, "dune")).isEmpty();
    }

    @Test
    void unreachableServerIsAnEmptyAnswer() {
        EchoCatalog catalog = catalog();
        server.stop(0);

        assertThat(catalog.search(MediaCategory.BOOK, "dune")).isEmpty();
    }

    @Test
    void oversizedBodyIsRejected() {
        body.set("x".repeat(HttpCatalogProvider.MAX_BODY_BYTES + 10));

        assertThat(catalog().search(MediaCategory.BOOK, "dune")).isEmpty();
    }

    @Test
    void unavailableOrForeignCategoryNeverTouchesTheNetwork() {
        EchoCatalog catalog = catalog();
        catalog.available = false;
        assertThat(catalog.search(MediaCategory.BOOK, "dune")).isEmpty();
        assertThat(catalog.fetch(MediaCategory.BOOK, "1")).isEmpty();

        catalog.available = true;
        assertThat(catalog.search(MediaCategory.MOVIE, "dune")).isEmpty();
        assertThat(hits).hasValue(0);
    }
}
