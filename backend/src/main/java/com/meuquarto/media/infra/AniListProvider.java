package com.meuquarto.media.infra;

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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AniList, the one class of the project that is <b>two things at once</b> (ADR 0018): the canonical
 * catalog of anime and manga (ADR 0005) and the source of what a user watched and read (BR-10).
 * The shape is {@code extends} plus {@code implements}, like {@code Cachorro extends Animal implements
 * Brincavel, Nadavel} from the course:
 * <ul>
 *   <li>{@code extends HttpCatalogProvider}: inherits the whole catalog algorithm (the final template
 *       methods {@code search} and {@code fetch}: availability, request with timeouts, cache, failure
 *       handling) and the safe HTTP of {@code postJson}. This class only fills in the abstract steps;</li>
 *   <li>{@code HttpCatalogProvider implements CatalogProvider}: so an AniList object <i>is a</i>
 *       {@code CatalogProvider} through its parent;</li>
 *   <li>{@code implements ActivityProvider}: a second contract, written here (id, categories, username
 *       rules, {@code checkAccountExists}, {@code fetchActivity}).</li>
 * </ul>
 * There is one object and one bean: the catalog registry sees it as a {@code CatalogProvider}, the activity
 * registry as an {@code ActivityProvider}. The two interfaces both wanted a method {@code id()} with
 * different return types, which a class cannot implement, so the catalog one is called
 * {@link #catalogName()}.
 * <p>
 * All traffic goes to one fixed endpoint (GraphQL, {@code POST https://graphql.anilist.co}, no key for
 * public data). User input (search text, ids, usernames) travels only in the GraphQL {@code variables};
 * the query texts are constants, so nothing a user types is ever concatenated into a query.
 * AniList allows 90 requests per minute (30 while degraded); a search is one request, a sync is two, a link
 * check is one. Any failure, 429 included, becomes an empty answer (catalog) or a value/exception code
 * (activity) plus one warning that names the kind of failure and never a payload.
 * <p>
 * Catalog choices: the title is {@code title.romaji}, falling back to {@code title.english} (romaji always
 * exists and is what AniList itself shows by default; english can be missing); the year is
 * {@code startDate.year}, falling back to {@code seasonYear}; adult works are asked out of the search
 * ({@code isAdult: false}) and flagged again here (BR-08); the cover must be an https image on
 * {@code s4.anilist.co}, anything else becomes "no cover". The external id is the AniList media id.
 * <p>
 * Activity choices (BR-04, BR-10): the work is the canonical AniList media (same key as the catalog
 * search, so adding by hand and syncing give one item); only entries with status COMPLETED, CURRENT, PAUSED
 * or REPEATING are asked for, PLANNING and DROPPED never; COMPLETED and REPEATING are
 * {@link ActivityType#FINISHED}, CURRENT and PAUSED are {@link ActivityType#IN_PROGRESS}. The time is the
 * day the user finished (or started) the work when AniList has the full date, otherwise the last update;
 * the date, unlike the update time, does not change on every progress tick, so syncing again adds nothing.
 * At most {@value #MAX_ENTRIES} entries (the most recent) are returned per sync. The class is {@code final}.
 */
public final class AniListProvider extends HttpCatalogProvider implements ActivityProvider {

    public static final String CATALOG_NAME = "ANILIST";
    static final URI ENDPOINT = URI.create("https://graphql.anilist.co");
    /** The only host a cover image may come from. */
    static final Set<String> IMAGE_HOSTS = Set.of("s4.anilist.co");
    /** Entries returned by one sync, newest first. AniList itself caps one chunk at 500 entries. */
    static final int MAX_ENTRIES = 500;

    private static final Logger log = LoggerFactory.getLogger(AniListProvider.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_RESULTS = 20;
    private static final int MAX_TITLE = 300;
    private static final int MAX_IMAGE_URL = 500;
    private static final int MIN_YEAR = 1800;
    private static final int MAX_YEAR = 2200;
    private static final Pattern NUMERIC_ID = Pattern.compile("[0-9]{1,10}");
    private static final List<MediaCategory> ACTIVITY_CATEGORIES = List.of(MediaCategory.ANIME, MediaCategory.MANGA);

    /** The fields of a work, shared by the search and the detail queries. */
    private static final String MEDIA_FIELDS =
            "id isAdult title { romaji english } startDate { year } seasonYear coverImage { large }";

    private static final String SEARCH_QUERY = "query ($search: String, $type: MediaType) {"
            + " Page(page: 1, perPage: " + MAX_RESULTS + ") {"
            + " media(search: $search, type: $type, isAdult: false, sort: SEARCH_MATCH) { " + MEDIA_FIELDS + " } } }";

    private static final String FETCH_QUERY = "query ($id: Int, $type: MediaType) {"
            + " Media(id: $id, type: $type) { " + MEDIA_FIELDS + " } }";

    /** One request: the user must exist and the anime list must be readable (a private list answers 404). */
    private static final String CHECK_QUERY = "query ($name: String) {"
            + " User(name: $name) { id }"
            + " MediaListCollection(userName: $name, type: ANIME, perChunk: 1, chunk: 1) { hasNextChunk } }";

    private static final String LIST_QUERY = "query ($name: String, $type: MediaType) {"
            + " MediaListCollection(userName: $name, type: $type,"
            + " status_in: [COMPLETED, CURRENT, PAUSED, REPEATING],"
            + " sort: UPDATED_TIME_DESC, forceSingleCompletedList: true, perChunk: " + MAX_ENTRIES + ", chunk: 1) {"
            + " lists { isCustomList entries { status updatedAt startedAt { year month day }"
            + " completedAt { year month day } media { " + MEDIA_FIELDS + " } } } } }";

    /** The AniList list statuses we accept and what each one means for us (BR-10). PLANNING and DROPPED are absent. */
    private enum ListStatus {
        COMPLETED(ActivityType.FINISHED),
        REPEATING(ActivityType.FINISHED),
        CURRENT(ActivityType.IN_PROGRESS),
        PAUSED(ActivityType.IN_PROGRESS);

        private final ActivityType type;

        ListStatus(ActivityType type) {
            this.type = type;
        }

        static Optional<ListStatus> of(String raw) {
            for (ListStatus status : values()) {
                if (status.name().equals(raw)) {
                    return Optional.of(status);
                }
            }
            return Optional.empty();
        }
    }

    private final URI endpoint;

    /** What production uses: the real AniList endpoint, search answers cached for ten minutes. */
    public AniListProvider(Clock clock) {
        this(HttpCatalogProvider.newClient(), clock, Duration.ofMinutes(10), ENDPOINT, REQUEST_TIMEOUT);
    }

    /**
     * For tests only (a local stub server). Nothing in the application reads the address from configuration
     * or from a user: the production bean is built with the constructor above.
     */
    AniListProvider(HttpClient client, Clock clock, Duration cacheTtl, URI endpoint, Duration timeout) {
        super(client, clock, cacheTtl, timeout);
        this.endpoint = endpoint;
    }

    // ---- CatalogProvider (the abstract steps of HttpCatalogProvider) ----

    @Override
    public String catalogName() {
        return CATALOG_NAME;
    }

    @Override
    public boolean serves(MediaCategory category) {
        return ACTIVITY_CATEGORIES.contains(category);
    }

    /** Public data needs no key, so AniList is always available (a failing call just answers empty). */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    protected URI searchUri(MediaCategory category, String query) {
        return endpoint;
    }

    @Override
    protected String searchBody(MediaCategory category, String query) {
        return body(SEARCH_QUERY, Map.of("search", query, "type", category.name()));
    }

    @Override
    protected URI fetchUri(MediaCategory category, String externalId) {
        if (externalId == null || !NUMERIC_ID.matcher(externalId).matches()) {
            throw new IllegalArgumentException("Not an AniList id");
        }
        return endpoint;
    }

    @Override
    protected String fetchBody(MediaCategory category, String externalId) {
        return body(FETCH_QUERY, Map.of("id", Long.parseLong(externalId), "type", category.name()));
    }

    @Override
    protected List<CatalogEntry> parseSearch(MediaCategory category, String body) {
        List<CatalogEntry> entries = new ArrayList<>();
        for (JsonNode node : JSON.readTree(body).path("data").path("Page").path("media")) {
            toWork(category, node).filter(entry -> !entry.adult()).ifPresent(entries::add);
            if (entries.size() == MAX_RESULTS) {
                break;
            }
        }
        return entries;
    }

    @Override
    protected Optional<CatalogEntry> parseFetch(MediaCategory category, String body) {
        return toWork(category, JSON.readTree(body).path("data").path("Media"));
    }

    // ---- ActivityProvider (the second contract) ----

    @Override
    public ProviderId id() {
        return ProviderId.ANILIST;
    }

    @Override
    public Set<MediaCategory> categories() {
        return Set.copyOf(ACTIVITY_CATEGORIES);
    }

    /**
     * AniList names: 2 to 20 characters, letters, digits and underscore, case-insensitive. This is the
     * format observed on the sign-up form, not a published specification (the API docs say nothing about it),
     * so it is deliberately a little loose: the existence check is what decides, and a name this rule lets
     * through but AniList does not know is simply "not found". Lower-cased on the way in.
     */
    @Override
    public List<ValidationRule<String, UsernameProblem>> usernameRules() {
        return UsernameRules.of(2, 20, "[a-z0-9_]+");
    }

    @Override
    public String normalizeUsername(String raw) {
        return ActivityProvider.super.normalizeUsername(raw).toLowerCase(Locale.ROOT);
    }

    /** AniList answers 404 for a user that does not exist and also for one whose lists are private. */
    @Override
    public AccountCheck checkAccountExists(String username) {
        try {
            requireValid(username);
            Reply reply = postJson(endpoint, body(CHECK_QUERY, Map.of("name", username)));
            if (reply.status() == 404) {
                return AccountCheck.NOT_FOUND;
            }
            if (reply.status() != 200) {
                return unavailable("HTTP " + reply.status());
            }
            boolean found = JSON.readTree(reply.body()).path("data").path("User").isObject();
            return found ? AccountCheck.EXISTS : unavailable("unexpected answer");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        } catch (IOException | RuntimeException e) {
            return unavailable(e.getClass().getSimpleName());
        }
    }

    /** Two requests (anime, then manga). Any failure of either throws {@code PROVIDER_UNAVAILABLE}. */
    @Override
    public ActivitySnapshot fetchActivity(String username) {
        try {
            requireValid(username);
            List<ActivityEntry> all = new ArrayList<>();
            for (MediaCategory category : ACTIVITY_CATEGORIES) {
                Reply reply = postJson(endpoint, body(LIST_QUERY, Map.of("name", username, "type", category.name())));
                if (reply.status() != 200) {
                    throw failed("HTTP " + reply.status());
                }
                all.addAll(parseList(category, reply.body()));
            }
            all.sort(Comparator.comparing(ActivityEntry::occurredAt).reversed());
            return new ActivitySnapshot(all.size() > MAX_ENTRIES ? all.subList(0, MAX_ENTRIES) : all);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed("interrupted");
        } catch (LinkedAccountException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw failed(e.getClass().getSimpleName());
        }
    }

    // ---- mapping ----

    private List<ActivityEntry> parseList(MediaCategory category, String body) {
        JsonNode collection = JSON.readTree(body).path("data").path("MediaListCollection");
        if (!collection.isObject()) {
            throw new IllegalStateException("no list in the answer");
        }
        List<ActivityEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int skipped = 0;
        for (JsonNode list : collection.path("lists")) {
            if (list.path("isCustomList").asBoolean(false)) {
                continue; // a custom list repeats entries of the real lists
            }
            for (JsonNode node : list.path("entries")) {
                Optional<ActivityEntry> entry = toActivity(category, node);
                if (entry.isEmpty() || !seen.add(entry.get().work().externalId())) {
                    skipped++;
                } else {
                    entries.add(entry.get());
                }
            }
        }
        if (skipped > 0) {
            log.warn("Activity {} skipped {} unusable entries", id(), skipped);
        }
        return entries;
    }

    private static Optional<ActivityEntry> toActivity(MediaCategory category, JsonNode node) {
        try {
            Optional<ListStatus> status = ListStatus.of(node.path("status").asString(""));
            Optional<CatalogEntry> work = toWork(category, node.path("media"));
            if (status.isEmpty() || work.isEmpty() || work.get().adult()) {
                return Optional.empty(); // PLANNING, DROPPED, a broken work or an adult one (BR-08)
            }
            ActivityType type = status.get().type;
            JsonNode day = node.path(type == ActivityType.FINISHED ? "completedAt" : "startedAt");
            Optional<Instant> when = date(day).or(() -> updatedAt(node.path("updatedAt")));
            return when.map(time -> new ActivityEntry(work.get(), type, time));
        } catch (RuntimeException broken) {
            return Optional.empty(); // one malformed entry must not hide the others
        }
    }

    /** A work as AniList describes it; empty without a positive id or a title. */
    private static Optional<CatalogEntry> toWork(MediaCategory category, JsonNode media) {
        long id = media.path("id").asLong(0);
        String title = bounded(firstText(media.path("title").path("romaji"), media.path("title").path("english")));
        if (id <= 0 || title.isEmpty()) {
            return Optional.empty();
        }
        Integer year = year(media.path("startDate").path("year")).or(() -> year(media.path("seasonYear"))).orElse(null);
        return Optional.of(new CatalogEntry(category, CATALOG_NAME, Long.toString(id), title, year,
                cover(media.path("coverImage").path("large").asString("")), media.path("isAdult").asBoolean(false)));
    }

    private static String firstText(JsonNode first, JsonNode second) {
        String text = first.asString("").strip();
        return text.isEmpty() ? second.asString("").strip() : text;
    }

    private static String bounded(String value) {
        return value.length() <= MAX_TITLE ? value : value.substring(0, MAX_TITLE);
    }

    private static Optional<Integer> year(JsonNode node) {
        int year = node.asInt(0);
        return year >= MIN_YEAR && year <= MAX_YEAR ? Optional.of(year) : Optional.empty();
    }

    /** A full {year, month, day} at midnight UTC (stable between syncs); empty when any part is missing or impossible. */
    private static Optional<Instant> date(JsonNode day) {
        int year = day.path("year").asInt(0);
        int month = day.path("month").asInt(0);
        int dayOfMonth = day.path("day").asInt(0);
        try {
            return year < MIN_YEAR || year > MAX_YEAR
                    ? Optional.empty()
                    : Optional.of(LocalDate.of(year, month, dayOfMonth).atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (DateTimeException impossible) {
            return Optional.empty();
        }
    }

    /** AniList sends the last update as epoch seconds. */
    private static Optional<Instant> updatedAt(JsonNode seconds) {
        long value = seconds.asLong(0);
        return value > 0 ? Optional.of(Instant.ofEpochSecond(value)) : Optional.empty();
    }

    /** Only an https image on an allowed host, without user info, port or fragment; otherwise no cover. */
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

    // ---- request building and failures ----

    /** The JSON body of a GraphQL call: the constant query plus the variables (user input lives only there). */
    private static String body(String query, Map<String, Object> variables) {
        return JSON.writeValueAsString(Map.of("query", query, "variables", variables));
    }

    /** Defense in depth: the callers validated the username already; nothing invalid is ever sent. */
    private void requireValid(String username) {
        if (username == null || validateUsernameFormat(username).isPresent()) {
            throw new IllegalArgumentException("invalid username");
        }
    }

    private AccountCheck unavailable(String reason) {
        log.warn("Activity {} check failed: {}", id(), reason);
        return AccountCheck.UNAVAILABLE;
    }

    private LinkedAccountException failed(String reason) {
        log.warn("Activity {} fetch failed: {}", id(), reason);
        return new LinkedAccountException(Problem.PROVIDER_UNAVAILABLE);
    }
}
