package com.meuquarto.media.infra;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.shared.domain.MediaCategory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * IGDB, the canonical catalog of games (ADR 0005). IGDB belongs to Twitch: a Twitch app (client id and
 * secret) buys an <em>app access token</em> (client credentials) that is sent, together with the
 * client id, on every query. The token is kept in memory until a minute before it expires and is
 * renewed once when IGDB answers 401. Without the id and the secret the catalog reports itself
 * unavailable. Neither the secret nor the token is ever put in a URL or a log.
 * <p>
 * Queries are POSTs in Apicalypse (IGDB's query language). The user's text is the only variable part:
 * it is checked, stripped of control characters and written between quotes with {@code "} and
 * {@code \} escaped (see {@link #quote}), so it cannot close the string and add clauses. Everything
 * else is a constant of this class. IGDB allows 4 requests per second; {@link #throttle} spaces
 * calls 250 ms apart, and the template's ten-minute cache means repeated searches never get there.
 * <p>
 * Choices: the work is the main game ({@code version_parent} and {@code parent_game} empty, so
 * editions, DLC and expansions are not separate items); the cover is built by us from
 * {@code cover.image_id} on IGDB's image host, never taken from the response; theme 42 ("Erotic")
 * means adult (BR-08).
 */
public final class IgdbCatalog extends HttpCatalogProvider {

    public static final String ID = "IGDB";
    static final String API_URL = "https://api.igdb.com/v4/games";
    static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    static final String IMAGE_BASE = "https://images.igdb.com/igdb/image/upload/t_cover_big/";
    /** IGDB's theme id for erotic games. */
    static final int EROTIC_THEME = 42;

    private static final String FIELDS = "fields name,first_release_date,cover.image_id,themes;";
    private static final String MAIN_GAMES = "version_parent = null & parent_game = null";
    private static final int MAX_RESULTS = 20;
    private static final int MAX_QUERY = 100;
    private static final int MAX_TITLE = 300;
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2200;
    private static final long TOKEN_MARGIN_SECONDS = 60;
    private static final int MAX_TOKEN_BODY = 16 * 1024;
    private static final Pattern NUMERIC_ID = Pattern.compile("[0-9]{1,12}");
    private static final Pattern IMAGE_ID = Pattern.compile("[A-Za-z0-9]{1,40}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9._~+/=-]{1,500}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient client;
    private final Clock clock;
    private final Duration requestTimeout;
    private final Duration minInterval;
    private final String apiUrl;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.MIN;
    private Instant lastCall = Instant.MIN;

    /** What production uses: the real hosts, answers cached for ten minutes, 4 requests per second. */
    public IgdbCatalog(Clock clock, String clientId, String clientSecret) {
        this(HttpCatalogProvider.newClient(), clock, Duration.ofMinutes(10), API_URL, TOKEN_URL, clientId,
                clientSecret, REQUEST_TIMEOUT, Duration.ofMillis(250));
    }

    /** For tests only (local stub servers); the addresses are never read from configuration or from a user. */
    IgdbCatalog(HttpClient client, Clock clock, Duration cacheTtl, String apiUrl, String tokenUrl, String clientId,
            String clientSecret, Duration timeout, Duration minInterval) {
        super(client, clock, cacheTtl, timeout);
        this.client = client;
        this.clock = clock;
        this.requestTimeout = timeout;
        this.minInterval = minInterval;
        this.apiUrl = apiUrl;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId == null ? "" : clientId.strip();
        this.clientSecret = clientSecret == null ? "" : clientSecret.strip();
    }

    @Override
    public String catalogName() {
        return ID;
    }

    @Override
    public boolean serves(MediaCategory category) {
        return category == MediaCategory.GAME;
    }

    @Override
    public boolean isAvailable() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    // ---- the request: everything is a POST to one endpoint, the body is the query ----

    @Override
    protected URI searchUri(MediaCategory category, String query) {
        return URI.create(apiUrl);
    }

    @Override
    protected String searchBody(MediaCategory category, String query) {
        return "search " + quote(query) + "; " + FIELDS + " where " + MAIN_GAMES + "; limit " + MAX_RESULTS + ";";
    }

    @Override
    protected URI fetchUri(MediaCategory category, String externalId) {
        checkId(externalId);
        return URI.create(apiUrl);
    }

    @Override
    protected String fetchBody(MediaCategory category, String externalId) {
        checkId(externalId);
        return FIELDS + " where id = " + externalId + "; limit 1;";
    }

    private static void checkId(String externalId) {
        if (externalId == null || !NUMERIC_ID.matcher(externalId).matches()) {
            throw new IllegalArgumentException("Not an IGDB id");
        }
    }

    /**
     * Writes the user's text as an Apicalypse string. It must be 1 to 100 characters; control
     * characters (newlines included) are removed, then {@code \} and {@code "} are escaped, in that
     * order, so nothing in the text can end the string or start a clause.
     */
    static String quote(String text) {
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > MAX_QUERY) {
            throw new IllegalArgumentException("Invalid search text");
        }
        StringBuilder clean = new StringBuilder();
        text.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(clean::appendCodePoint);
        String escaped = clean.toString().strip().replace("\\", "\\\\").replace("\"", "\\\"");
        if (escaped.isEmpty()) {
            throw new IllegalArgumentException("Invalid search text");
        }
        return "\"" + escaped + "\"";
    }

    // ---- authorization: Twitch app token, cached, renewed on 401 ----

    @Override
    protected void authorize(HttpRequest.Builder request) {
        throttle();
        request.setHeader("Client-ID", clientId)
                .setHeader("Authorization", "Bearer " + token())
                .setHeader("Content-Type", "text/plain");
    }

    /** IGDB said the token is no good: forget it so the next {@link #authorize} buys a new one. */
    @Override
    protected synchronized boolean onUnauthorized() {
        accessToken = null;
        return true;
    }

    private synchronized String token() {
        if (accessToken == null || !clock.instant().isBefore(tokenExpiresAt)) {
            requestToken();
        }
        return accessToken;
    }

    /** Spaces IGDB calls at least {@link #minInterval} apart (its limit is 4 requests per second). */
    private synchronized void throttle() {
        Instant now = clock.instant();
        Instant next = lastCall.equals(Instant.MIN) ? now : lastCall.plus(minInterval);
        if (next.isAfter(now)) {
            try {
                Thread.sleep(Duration.between(now, next).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted");
            }
        }
        lastCall = clock.instant();
    }

    /** The secret travels in the POST form, never in the URL. Any failure is a message without secrets. */
    private void requestToken() {
        String form = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&grant_type=client_credentials";
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Twitch token request answered " + response.statusCode());
                }
                byte[] bytes = in.readNBytes(MAX_TOKEN_BODY + 1);
                if (bytes.length > MAX_TOKEN_BODY) {
                    throw new IllegalStateException("Twitch token response too large");
                }
                JsonNode json = JSON.readTree(new String(bytes, StandardCharsets.UTF_8));
                String value = json.path("access_token").asString("");
                long seconds = json.path("expires_in").asLong(0);
                if (!TOKEN.matcher(value).matches() || seconds <= 0) {
                    throw new IllegalStateException("Twitch token response unreadable");
                }
                accessToken = value;
                tokenExpiresAt = clock.instant().plusSeconds(Math.max(seconds - TOKEN_MARGIN_SECONDS, 0));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        } catch (IOException e) {
            throw new IllegalStateException("Twitch token request failed (" + e.getClass().getSimpleName() + ")");
        }
    }

    // ---- the answer ----

    @Override
    protected List<CatalogEntry> parseSearch(MediaCategory category, String body) {
        List<CatalogEntry> entries = new ArrayList<>();
        for (JsonNode node : JSON.readTree(body)) {
            toEntry(category, node).filter(entry -> !entry.adult()).ifPresent(entries::add);
            if (entries.size() == MAX_RESULTS) {
                break;
            }
        }
        return entries;
    }

    @Override
    protected Optional<CatalogEntry> parseFetch(MediaCategory category, String body) {
        for (JsonNode node : JSON.readTree(body)) {
            return toEntry(category, node);
        }
        return Optional.empty();
    }

    private static Optional<CatalogEntry> toEntry(MediaCategory category, JsonNode node) {
        long id = node.path("id").asLong(0);
        String title = node.path("name").asString("").strip();
        if (id <= 0 || title.isEmpty()) {
            return Optional.empty();
        }
        if (title.length() > MAX_TITLE) {
            title = title.substring(0, MAX_TITLE);
        }
        return Optional.of(new CatalogEntry(category, ID, Long.toString(id), title,
                year(node.path("first_release_date").asLong(0)), cover(node.path("cover").path("image_id").asString("")),
                isAdult(node.path("themes"))));
    }

    /** Epoch seconds to the UTC year; missing or absurd gives null. */
    private static Integer year(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        int year = Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).getYear();
        return year >= MIN_YEAR && year <= MAX_YEAR ? Integer.valueOf(year) : null;
    }

    /** We build the URL from a validated image id, so it always stays on IGDB's image host. */
    private static String cover(String imageId) {
        return IMAGE_ID.matcher(imageId).matches() ? IMAGE_BASE + imageId + ".jpg" : null;
    }

    private static boolean isAdult(JsonNode themes) {
        for (JsonNode theme : themes) {
            if (theme.asInt(0) == EROTIC_THEME) {
                return true;
            }
        }
        return false;
    }
}
