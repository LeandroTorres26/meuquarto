package com.meuquarto.media.infra;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.shared.domain.MediaCategory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base of every catalog that talks HTTP (ADR 0018, hierarchy 2). It is abstract because it knows the
 * algorithm but not the platform: {@link #search} and {@link #fetch} are the template method (check
 * availability, build the request, call with timeouts, cache, survive failures) and the abstract
 * steps below are what each leaf (TMDB, AniList...) fills in.
 * <p>
 * Failures never reach the caller: a timeout, a bad status, a huge body or broken JSON become an
 * empty answer plus one warning. The warning names the provider and the kind of failure only, never
 * the URL or any header, so no secret can end up in a log.
 */
public abstract class HttpCatalogProvider implements CatalogProvider {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    static final int MAX_CACHE_ENTRIES = 500;

    private static final Logger log = LoggerFactory.getLogger(HttpCatalogProvider.class);

    private record Cached(String body, Instant expiresAt) {}

    private final HttpClient client;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Duration requestTimeout;
    private final Map<String, Cached> cache = new HashMap<>();

    protected HttpCatalogProvider(HttpClient client, Clock clock, Duration cacheTtl) {
        this(client, clock, cacheTtl, REQUEST_TIMEOUT);
    }

    /** Overload with a shorter timeout, so tests do not wait five seconds. */
    protected HttpCatalogProvider(HttpClient client, Clock clock, Duration cacheTtl, Duration requestTimeout) {
        this.client = client;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
        this.requestTimeout = requestTimeout;
    }

    /** A client with the connect timeout and no redirects (a catalog never needs to send us elsewhere). */
    public static HttpClient newClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    // ---- the template methods: the same steps for every platform ----

    @Override
    public final List<CatalogEntry> search(MediaCategory category, String query) {
        if (!isAvailable() || !serves(category)) {
            return List.of();
        }
        return load(() -> searchUri(category, query)).map(body -> parse(() -> parseSearch(category, body), List.<CatalogEntry>of()))
                .orElse(List.of());
    }

    @Override
    public final Optional<CatalogEntry> fetch(MediaCategory category, String externalId) {
        if (!isAvailable() || !serves(category)) {
            return Optional.empty();
        }
        return load(() -> fetchUri(category, externalId))
                .flatMap(body -> parse(() -> parseFetch(category, body), Optional.<CatalogEntry>empty()));
    }

    private interface Step<T> {
        T run();
    }

    /** Runs a leaf step; any exception it throws becomes the fallback plus one warning. */
    private <T> T parse(Step<T> step, T fallback) {
        try {
            return step.run();
        } catch (RuntimeException e) {
            failed("unreadable response (" + e.getClass().getSimpleName() + ")");
            return fallback;
        }
    }

    /** Body of a successful GET, from the cache or the network; empty on any failure. */
    private Optional<String> load(Step<URI> uriStep) {
        URI uri;
        try {
            uri = uriStep.run();
        } catch (RuntimeException e) {
            return failed("bad request (" + e.getClass().getSimpleName() + ")");
        }
        String key = uri.toString();
        Optional<String> hit = cached(key);
        if (hit.isPresent()) {
            return hit;
        }
        try {
            Optional<String> body = download(uri);
            body.ifPresent(text -> store(key, text));
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed("interrupted");
        } catch (IOException | RuntimeException e) {
            return failed(e.getClass().getSimpleName());
        }
    }

    private Optional<String> download(URI uri) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "application/json");
        authorize(request);
        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                return failed("HTTP " + response.statusCode());
            }
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                return failed("response too large");
            }
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private <T> Optional<T> failed(String reason) {
        log.warn("Catalog {} failed: {}", id(), reason);
        return Optional.empty();
    }

    private synchronized Optional<String> cached(String key) {
        Cached entry = cache.get(key);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.body());
    }

    private synchronized void store(String key, String body) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            Instant now = clock.instant();
            cache.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
        }
        cache.put(key, new Cached(body, clock.instant().plus(cacheTtl)));
    }

    // ---- the steps each platform fills in ----

    /** Where to ask for a text search. Must percent-encode the query. */
    protected abstract URI searchUri(MediaCategory category, String query);

    /** Where to ask for one work. Must reject ids that are not valid for the platform. */
    protected abstract URI fetchUri(MediaCategory category, String externalId);

    /** Turns a search response into normalized entries. May throw; the template turns that into "no results". */
    protected abstract List<CatalogEntry> parseSearch(MediaCategory category, String body);

    /** Turns a detail response into one entry. May throw; the template turns that into "not found". */
    protected abstract Optional<CatalogEntry> parseFetch(MediaCategory category, String body);

    /** Optional step with a default that does nothing: platforms that need a key override it. */
    protected void authorize(HttpRequest.Builder request) {
        // public catalog: nothing to add
    }
}
