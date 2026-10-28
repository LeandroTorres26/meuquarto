package com.meuquarto.media.infra;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.shared.domain.MediaCategory;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TMDB, the canonical catalog of films and series (ADR 0005). Sends the "API Read Access Token" as a
 * Bearer token, so the secret travels in a header and never in a URL. Without a token it reports
 * itself unavailable and the template answers with empty results. Adult titles are dropped (BR-08);
 * posters are hotlinked from the TMDB image CDN (ADR 0006).
 */
public final class TmdbCatalog extends HttpCatalogProvider {

    public static final String ID = "TMDB";
    static final String IMAGE_BASE = "https://image.tmdb.org/t/p/w342";
    private static final int MAX_RESULTS = 20;
    private static final Pattern NUMERIC_ID = Pattern.compile("[0-9]{1,10}");
    private static final Pattern POSTER_PATH = Pattern.compile("/[A-Za-z0-9._-]{1,100}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final String token;

    public TmdbCatalog(HttpClient client, Clock clock, Duration cacheTtl, String baseUrl, String token) {
        super(client, clock, cacheTtl);
        this.baseUrl = baseUrl;
        this.token = token == null ? "" : token.strip();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean serves(MediaCategory category) {
        return category == MediaCategory.MOVIE || category == MediaCategory.SERIES;
    }

    @Override
    public boolean isAvailable() {
        return !token.isEmpty();
    }

    @Override
    protected URI searchUri(MediaCategory category, String query) {
        return URI.create(baseUrl + "/search/" + kind(category) + "?language=pt-BR&include_adult=false&page=1&query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    @Override
    protected URI fetchUri(MediaCategory category, String externalId) {
        if (externalId == null || !NUMERIC_ID.matcher(externalId).matches()) {
            throw new IllegalArgumentException("Not a TMDB id");
        }
        return URI.create(baseUrl + "/" + kind(category) + "/" + externalId + "?language=pt-BR");
    }

    @Override
    protected void authorize(HttpRequest.Builder request) {
        request.header("Authorization", "Bearer " + token);
    }

    @Override
    protected List<CatalogEntry> parseSearch(MediaCategory category, String body) {
        List<CatalogEntry> entries = new ArrayList<>();
        for (JsonNode node : JSON.readTree(body).path("results")) {
            toEntry(category, node).filter(entry -> !entry.adult()).ifPresent(entries::add);
            if (entries.size() == MAX_RESULTS) {
                break;
            }
        }
        return entries;
    }

    @Override
    protected Optional<CatalogEntry> parseFetch(MediaCategory category, String body) {
        return toEntry(category, JSON.readTree(body));
    }

    private static String kind(MediaCategory category) {
        return category == MediaCategory.MOVIE ? "movie" : "tv";
    }

    private static Optional<CatalogEntry> toEntry(MediaCategory category, JsonNode node) {
        long id = node.path("id").asLong(0);
        boolean movie = category == MediaCategory.MOVIE;
        String title = node.path(movie ? "title" : "name").asString("").strip();
        if (id <= 0 || title.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CatalogEntry(category, ID, Long.toString(id), title,
                year(node.path(movie ? "release_date" : "first_air_date").asString("")),
                poster(node.path("poster_path").asString("")),
                node.path("adult").asBoolean(false)));
    }

    /** "2010-07-15" gives 2010; anything else gives null. */
    private static Integer year(String date) {
        if (date.length() >= 4 && date.substring(0, 4).chars().allMatch(Character::isDigit)) {
            return Integer.valueOf(date.substring(0, 4));
        }
        return null;
    }

    /** Only a plain "/file.jpg" path is accepted, so the URL always stays on the TMDB image host. */
    private static String poster(String path) {
        return POSTER_PATH.matcher(path).matches() ? IMAGE_BASE + path : null;
    }
}
