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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Open Library, the canonical catalog of books (ADR 0005). Needs no key, so it is always available.
 * Open Library asks every client to identify itself, so {@link #authorize} adds a descriptive
 * {@code User-Agent} (project name and repository, no personal data).
 * <p>
 * Choices: the work is the Open Library <em>work</em> (not an edition), so the external id is the work key
 * without its path ({@code OL45804W}); the year is {@code first_publish_year} in a search and the year in
 * {@code first_publish_date} in a detail; the cover is built here from the numeric {@code cover_i} on
 * {@code covers.openlibrary.org}, so no URL from the response is ever used. Nothing in Open Library is
 * flagged adult, so {@code adult} is always false (BR-08 filters whatever a catalog flags; there is no
 * signal to map). The query goes only as an encoded parameter of a fixed host.
 */
public final class OpenLibraryCatalog extends HttpCatalogProvider {

    public static final String ID = "OPENLIBRARY";
    static final String BASE_URL = "https://openlibrary.org";
    static final String COVER_BASE = "https://covers.openlibrary.org/b/id/";
    static final String USER_AGENT =
            "meuquarto/1.0 (academic project, Fatec Baixada Santista; https://github.com/LeandroTorres26/meuquarto)";

    private static final int MAX_RESULTS = 20;
    private static final int MAX_QUERY = 100;
    private static final int MAX_TITLE = 300;
    private static final int MIN_YEAR = 1000;
    private static final int MAX_YEAR = 2200;
    private static final Pattern WORK_ID = Pattern.compile("OL[0-9]{1,12}W");
    private static final Pattern WORK_KEY = Pattern.compile("/works/(OL[0-9]{1,12}W)");
    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("(?<![0-9])([0-9]{4})(?![0-9])");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;

    /** What production uses: the real Open Library, answers cached for ten minutes. */
    public OpenLibraryCatalog(Clock clock) {
        this(HttpCatalogProvider.newClient(), clock, Duration.ofMinutes(10), BASE_URL, REQUEST_TIMEOUT);
    }

    /** For tests only (a local stub server); the address is never read from configuration or from a user. */
    OpenLibraryCatalog(HttpClient client, Clock clock, Duration cacheTtl, String baseUrl, Duration timeout) {
        super(client, clock, cacheTtl, timeout);
        this.baseUrl = baseUrl;
    }

    @Override
    public String catalogName() {
        return ID;
    }

    @Override
    public boolean serves(MediaCategory category) {
        return category == MediaCategory.BOOK;
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
        return URI.create(baseUrl + "/search.json?limit=" + MAX_RESULTS
                + "&fields=key,title,first_publish_year,cover_i&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    @Override
    protected URI fetchUri(MediaCategory category, String externalId) {
        if (externalId == null || !WORK_ID.matcher(externalId).matches()) {
            throw new IllegalArgumentException("Not an Open Library work id");
        }
        return URI.create(baseUrl + "/works/" + externalId + ".json");
    }

    @Override
    protected void authorize(HttpRequest.Builder request) {
        request.header("User-Agent", USER_AGENT);
    }

    @Override
    protected List<CatalogEntry> parseSearch(MediaCategory category, String body) {
        List<CatalogEntry> entries = new ArrayList<>();
        for (JsonNode doc : JSON.readTree(body).path("docs")) {
            Matcher key = WORK_KEY.matcher(doc.path("key").asString(""));
            String title = doc.path("title").asString("").strip();
            if (key.matches() && !title.isEmpty()) {
                entries.add(entry(category, key.group(1), title, year(doc.path("first_publish_year").asInt(0)),
                        cover(doc.path("cover_i").asLong(0))));
            }
            if (entries.size() == MAX_RESULTS) {
                break;
            }
        }
        return entries;
    }

    @Override
    protected Optional<CatalogEntry> parseFetch(MediaCategory category, String body) {
        JsonNode work = JSON.readTree(body);
        Matcher key = WORK_KEY.matcher(work.path("key").asString(""));
        String title = work.path("title").asString("").strip();
        if (!key.matches() || title.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entry(category, key.group(1), title, yearOf(work.path("first_publish_date").asString("")),
                cover(firstCover(work.path("covers")))));
    }

    private static CatalogEntry entry(MediaCategory category, String id, String title, Integer year, String cover) {
        String cut = title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title;
        return new CatalogEntry(category, ID, id, cut, year, cover, false);
    }

    private static Integer year(int value) {
        return value >= MIN_YEAR && value <= MAX_YEAR ? Integer.valueOf(value) : null;
    }

    /** "October 1, 1970" and "1970-10-01" both give 1970; anything without a four-digit year gives null. */
    private static Integer yearOf(String date) {
        Matcher found = FOUR_DIGIT_YEAR.matcher(date);
        return found.find() ? year(Integer.parseInt(found.group(1))) : null;
    }

    /** A work lists its cover ids; -1 means "no cover", so the first positive id wins. */
    private static long firstCover(JsonNode covers) {
        for (JsonNode id : covers) {
            if (id.asLong(0) > 0) {
                return id.asLong(0);
            }
        }
        return 0;
    }

    /** Built from a number on a fixed host: the response never supplies a URL. */
    private static String cover(long coverId) {
        return coverId > 0 ? COVER_BASE + coverId + "-M.jpg" : null;
    }
}
