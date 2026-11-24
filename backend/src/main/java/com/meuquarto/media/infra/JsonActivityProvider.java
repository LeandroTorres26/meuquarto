package com.meuquarto.media.infra;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivitySnapshot;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the platforms with a JSON API and a project key share (ADR 0018, hierarchy 1): Last.fm and
 * MyAnimeList. It is the JSON counterpart of {@link RssActivityProvider}. It is abstract because it knows
 * how to ask safely (key and name checked, timeout, size limit, spaced requests, failures turned into one
 * answer) but not what to ask: each leaf says that in {@link #check} and {@link #readEntries}.
 * <p>
 * The key is never logged, and neither is the request URL (Last.fm carries the key in it): a failure logs
 * the platform and the HTTP status or the class of the exception.
 */
public abstract class JsonActivityProvider implements ActivityProvider {

    static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(JsonActivityProvider.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** What came back: the HTTP status and the body (a missing node when it is not JSON). */
    protected record Reply(int status, JsonNode root) {}

    private final HttpClient client;
    private final Duration requestTimeout;
    private final Duration spacing;
    private final Object gate = new Object();
    private long nextRequestAtNanos;

    protected JsonActivityProvider(HttpClient client, Duration requestTimeout, Duration spacing) {
        this.client = client;
        this.requestTimeout = requestTimeout;
        this.spacing = spacing;
    }

    // ---- the template methods: the same steps for every JSON platform ----

    @Override
    public final AccountCheck checkAccountExists(String username) {
        try {
            requireUsable(username);
            return check(username);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        } catch (IOException | RuntimeException e) {
            return unavailable(e.getClass().getSimpleName());
        }
    }

    @Override
    public final ActivitySnapshot fetchActivity(String username) {
        try {
            requireUsable(username);
            return new ActivitySnapshot(newestFirst(readEntries(username)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed("interrupted");
        } catch (LinkedAccountException e) {
            throw e; // the leaf already said what went wrong
        } catch (IOException | RuntimeException e) {
            throw failed(e.getClass().getSimpleName());
        }
    }

    private List<ActivityEntry> newestFirst(List<ActivityEntry> entries) {
        List<ActivityEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(ActivityEntry::occurredAt).reversed());
        return sorted.size() > maxEntries() ? sorted.subList(0, maxEntries()) : sorted;
    }

    /** Nothing is sent without a usable key or with a name the rules refuse. */
    private void requireUsable(String username) {
        if (!isAvailable()) {
            throw new IllegalStateException("not configured");
        }
        if (username == null || validateUsernameFormat(username).isPresent()) {
            throw new IllegalArgumentException("invalid username");
        }
    }

    // ---- what each platform fills in ----

    /** False when the project's key is missing or malformed. Abstract here: every JSON platform needs a key. */
    @Override
    public abstract boolean isAvailable();

    /** Asks the platform whether the account exists and is public. The name and the key were already checked. */
    protected abstract AccountCheck check(String username) throws IOException, InterruptedException;

    /** Reads the activity, in any order. The name and the key were already checked. */
    protected abstract List<ActivityEntry> readEntries(String username) throws IOException, InterruptedException;

    /** How many entries one sync keeps (the newest). */
    protected abstract int maxEntries();

    /** Hook: adds the key as a header. Default: nothing, for a platform that takes it in the URL. */
    protected void authorize(HttpRequest.Builder request) {}

    // ---- what the platforms use ----

    /** One GET. {@code uri} holds only validated or encoded values; its host is fixed by the leaf. */
    protected final Reply get(URI uri) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "meuquarto/1.0")
                .GET();
        authorize(request);
        waitForSlot();
        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IOException("response too large");
            }
            return new Reply(response.statusCode(), parseOrMissing(new String(bytes, StandardCharsets.UTF_8)));
        }
    }

    private static JsonNode parseOrMissing(String body) {
        try {
            return JSON.readTree(body);
        } catch (RuntimeException notJson) {
            return JSON.missingNode();
        }
    }

    /** Keeps the whole process under the platform's rate limit: each request waits for its turn. */
    private void waitForSlot() throws InterruptedException {
        long wait;
        synchronized (gate) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextRequestAtNanos);
            nextRequestAtNanos = slot + spacing.toNanos();
            wait = slot - now;
        }
        if (wait > 0) {
            TimeUnit.NANOSECONDS.sleep(wait);
        }
    }

    protected final AccountCheck unavailable(String reason) {
        log.warn("Activity {} check failed: {}", id(), reason);
        return AccountCheck.UNAVAILABLE;
    }

    protected final LinkedAccountException failed(String reason) {
        return failed(reason, Problem.PROVIDER_UNAVAILABLE);
    }

    protected final LinkedAccountException failed(String reason, Problem problem) {
        log.warn("Activity {} fetch failed: {}", id(), reason);
        return new LinkedAccountException(problem);
    }
}
