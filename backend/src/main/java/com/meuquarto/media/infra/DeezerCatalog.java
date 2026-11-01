package com.meuquarto.media.infra;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.shared.domain.MediaCategory;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deezer, the canonical catalog of music (ADR 0005). The public API needs no key and no login, so it is
 * always available. The work is the <em>album</em>; the external id is the Deezer album id.
 * <p>
 * Choices: the title is {@code "Album - Artist"}, because an album name alone ("Greatest Hits") says
 * nothing in a result list; the album search does not carry a release date and we do not make one extra
 * call per result, so the year is null in a search and comes from {@code release_date} in a detail
 * ({@link #fetch}); the cover is {@code cover_medium}, accepted only as an https image on a Deezer CDN
 * host; {@code explicit_lyrics} only says the lyrics have strong language, which is not adult content
 * (BR-08 is about adult-only titles), so {@code adult} is always false. Deezer reports errors with
 * status 200 and an {@code error} object; that is treated as a failure like any other.
 */
public final class DeezerCatalog extends HttpCatalogProvider {

    public static final String ID = "DEEZER";
    static final String BASE_URL = "https://api.deezer.com";
    /** The only hosts a cover image may come from (Deezer's image CDN). */
    static final Set<String> IMAGE_HOSTS =
            Set.of("cdn-images.dzcdn.net", "e-cdns-images.dzcdn.net", "cdnt-images.dzcdn.net");

    private static final int MAX_RESULTS = 20;
    private static final int MAX_QUERY = 100;
    private static final int MAX_TITLE = 300;
    private static final int MAX_IMAGE_URL = 500;
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2200;
    private static final Pattern NUMERIC_ID = Pattern.compile("[0-9]{1,12}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;

    /** What production uses: the real Deezer API, answers cached for ten minutes. */
    public DeezerCatalog(Clock clock) {
        this(HttpCatalogProvider.newClient(), clock, Duration.ofMinutes(10), BASE_URL, REQUEST_TIMEOUT);
    }

    /** For tests only (a local stub server); the address is never read from configuration or from a user. */
    DeezerCatalog(HttpClient client, Clock clock, Duration cacheTtl, String baseUrl, Duration timeout) {
        super(client, clock, cacheTtl, timeout);
        this.baseUrl = baseUrl;
    }

    @Override
    public String catalogName() {
        return ID;
    }

    @Override
    public boolean serves(MediaCategory category) {
        return category == MediaCategory.MUSIC;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    protected URI searchUri(MediaCategory category, String query) {
        if (query == null || query.isBlank() || query.codePointCount(0, query.length()) > MAX_QUERY) {
            throw new IllegalArgumentException("Invalid search text");
        }
        return URI.create(baseUrl + "/search/album?limit=" + MAX_RESULTS
                + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    @Override
    protected URI fetchUri(MediaCategory category, String externalId) {
        if (externalId == null || !NUMERIC_ID.matcher(externalId).matches()) {
            throw new IllegalArgumentException("Not a Deezer album id");
        }
        return URI.create(baseUrl + "/album/" + externalId);
    }

    @Override
    protected List<CatalogEntry> parseSearch(MediaCategory category, String body) {
        JsonNode root = JSON.readTree(body);
        failIfError(root);
        List<CatalogEntry> entries = new ArrayList<>();
        for (JsonNode album : root.path("data")) {
            toEntry(category, album).ifPresent(entries::add);
            if (entries.size() == MAX_RESULTS) {
                break;
            }
        }
        return entries;
    }

    @Override
    protected Optional<CatalogEntry> parseFetch(MediaCategory category, String body) {
        JsonNode root = JSON.readTree(body);
        failIfError(root);
        return toEntry(category, root);
    }

    private static void failIfError(JsonNode root) {
        if (root.has("error")) {
            throw new IllegalStateException("Deezer answered with an error");
        }
    }

    private static Optional<CatalogEntry> toEntry(MediaCategory category, JsonNode album) {
        long id = album.path("id").asLong(0);
        String name = album.path("title").asString("").strip();
        if (id <= 0 || name.isEmpty()) {
            return Optional.empty();
        }
        String artist = album.path("artist").path("name").asString("").strip();
        String title = artist.isEmpty() ? name : name + " - " + artist;
        if (title.length() > MAX_TITLE) {
            title = title.substring(0, MAX_TITLE);
        }
        return Optional.of(new CatalogEntry(category, ID, Long.toString(id), title,
                year(album.path("release_date").asString("")), cover(album.path("cover_medium").asString("")), false));
    }

    /** "2001-03-07" gives 2001; a missing or odd date gives null. */
    private static Integer year(String date) {
        if (date.length() >= 4 && date.substring(0, 4).chars().allMatch(Character::isDigit)) {
            int year = Integer.parseInt(date.substring(0, 4));
            return year >= MIN_YEAR && year <= MAX_YEAR ? Integer.valueOf(year) : null;
        }
        return null;
    }

    /** Only an https image on a Deezer CDN host, without user info, port or fragment; otherwise no cover. */
    private static String cover(String raw) {
        if (raw.isEmpty() || raw.length() > MAX_IMAGE_URL) {
            return null;
        }
        try {
            URI uri = new URI(raw);
            boolean allowed = "https".equals(uri.getScheme()) && uri.getHost() != null
                    && IMAGE_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && uri.getRawUserInfo() == null && uri.getPort() == -1 && uri.getRawFragment() == null;
            return allowed ? uri.toString() : null;
        } catch (URISyntaxException notAUrl) {
            return null;
        }
    }
}
