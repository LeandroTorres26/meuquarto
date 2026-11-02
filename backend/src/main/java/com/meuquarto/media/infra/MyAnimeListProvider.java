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
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MyAnimeList, a {@code final} leaf of hierarchy 1 (ADR 0018) that implements {@link ActivityProvider}
 * <b>directly</b>, like Last.fm: it speaks its own JSON API, so there is no base class worth sharing. It
 * feeds anime and manga from the user's public lists (BR-10, ADR 0023 decision 4).
 * <p>
 * Two kinds of requests, both {@code GET https://api.myanimelist.net/v2/users/{name}/animelist} and
 * {@code /mangalist} with {@code fields=list_status}, paged by {@code limit} (1000) and {@code offset}. The
 * header {@code X-MAL-CLIENT-ID} carries the project's client id (variable {@code MAL_CLIENT_ID}); without a
 * usable one the provider is {@linkplain #isAvailable() unavailable} and sends nothing.
 * <p>
 * What counts (BR-10): anime {@code completed}, {@code watching} and {@code on_hold}; manga
 * {@code completed}, {@code reading} and {@code on_hold}; an entry flagged {@code is_rewatching} /
 * {@code is_rereading} (a work finished before, being taken up again) counts as finished. {@code plan_to_*}
 * and {@code dropped} never enter. Finished and rewatching are {@link ActivityType#FINISHED}; watching,
 * reading and on hold are {@link ActivityType#IN_PROGRESS}. The time is the finish (or start) date when MAL
 * has the full day, otherwise the last update, as the AniList provider does.
 * <p>
 * <b>The work is the AniList work</b> (ADR 0005, canonical for anime and manga). MyAnimeList itself is never
 * the source of a title, a year or a picture: the MAL ids of the wanted entries are resolved through AniList's
 * {@code idMal} ({@link AniListProvider#worksByMalId}), in batches of 50, and the AniList work is what goes to
 * the sync. So the same anime reached through an AniList account or a MyAnimeList account is the same row and
 * the same collection item: each work appears once. MyAnimeList's titles, pictures and scores are read in
 * memory and dropped (its agreement asks apps not to keep users' content); an entry AniList has no match for
 * is skipped and counted in one warning line (the count only).
 * <p>
 * Safety and limits: the host is fixed; the username passed MAL's format rules and is URL-encoded; the next
 * page is built here from the offset and the {@code paging.next} URL of the answer is never followed (it is
 * only the signal that there is another page); no redirects; connect and request timeouts; the body is capped
 * at 4 MB; requests are spaced 500 ms apart; at most {@value #MAX_PAGES} pages per list and
 * {@value #MAX_ENTRIES} entries per sync (the most recent). The client id is a header, never part of a URL, and
 * is never logged or put in an exception message: failures log the provider and the HTTP status or the class
 * name only. 403 and 404 mean the list is private or the user is missing (MAL does not say which), which the
 * panel explains; any other failure (including 401, a client id MAL refuses) is "unavailable".
 */
public final class MyAnimeListProvider implements ActivityProvider {

    static final URI ENDPOINT = URI.create("https://api.myanimelist.net/v2/");
    /** The most the API allows in one page of a list. */
    static final int PAGE_SIZE = 1000;
    static final int MAX_PAGES = 10;
    /** Entries kept per list before the lookup, and returned by one sync (newest first). Same cap as AniList. */
    static final int MAX_ENTRIES = 500;
    static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_SPACING = Duration.ofMillis(500);

    private static final Logger log = LoggerFactory.getLogger(MyAnimeListProvider.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9]{16,64}");
    private static final int MIN_YEAR = 1800;
    private static final int MAX_YEAR = 2200;
    private static final long MAX_MAL_ID = Integer.MAX_VALUE;

    /** The two lists, each with the category it feeds and the words MAL uses for it. */
    private enum Kind {
        ANIME("animelist", MediaCategory.ANIME, "watching", "is_rewatching"),
        MANGA("mangalist", MediaCategory.MANGA, "reading", "is_rereading");

        private final String path;
        private final MediaCategory category;
        private final String currentStatus;
        private final String repeatFlag;

        Kind(String path, MediaCategory category, String currentStatus, String repeatFlag) {
            this.path = path;
            this.category = category;
            this.currentStatus = currentStatus;
            this.repeatFlag = repeatFlag;
        }

        /** What an entry with this MAL status means for us, or empty for plan to watch/read, dropped and unknown. */
        Optional<ActivityType> typeOf(String status, boolean repeating) {
            if (status.equals("completed") || (repeating && (status.equals(currentStatus) || status.equals("on_hold")))) {
                return Optional.of(ActivityType.FINISHED);
            }
            if (status.equals(currentStatus) || status.equals("on_hold")) {
                return Optional.of(ActivityType.IN_PROGRESS);
            }
            return Optional.empty(); // plan_to_watch, plan_to_read, dropped (BR-10) and anything MAL adds later
        }
    }

    /** An entry that passed the status filter, with the MAL id still to be resolved. */
    private record Listed(long malId, ActivityType type, Instant when) {}

    /** The answer for one list: whether it was readable and what passed the filter (newest first). */
    private record ListRead(boolean readable, List<Listed> entries) {}

    /** What came back: the HTTP status and, only for a 200, the parsed body. */
    private record Reply(int status, JsonNode root) {}

    private final HttpClient client;
    private final URI endpoint;
    private final String clientId;
    private final AniListProvider anilist;
    private final Duration requestTimeout;
    private final Duration spacing;
    private final Object gate = new Object();
    private long nextRequestAtNanos;

    /** What production uses: the real endpoint and the client id from the environment (blank or invalid = unavailable). */
    public MyAnimeListProvider(String clientId, AniListProvider anilist) {
        this(HttpCatalogProvider.newClient(), ENDPOINT, clientId, anilist, REQUEST_TIMEOUT, REQUEST_SPACING);
    }

    /**
     * For tests only (a local stub server, no waiting). Nothing in the application reads the address from
     * configuration or from a user: the production bean is built with the constructor above.
     */
    MyAnimeListProvider(HttpClient client, URI endpoint, String clientId, AniListProvider anilist,
            Duration requestTimeout, Duration spacing) {
        this.client = client;
        this.endpoint = endpoint;
        this.clientId = clientId == null ? "" : clientId.strip();
        this.anilist = anilist;
        this.requestTimeout = requestTimeout;
        this.spacing = spacing;
    }

    // ---- ActivityProvider ----

    @Override
    public ProviderId id() {
        return ProviderId.MYANIMELIST;
    }

    @Override
    public Set<MediaCategory> categories() {
        return Set.of(MediaCategory.ANIME, MediaCategory.MANGA);
    }

    /** MyAnimeList names: 2 to 16 characters, letters, digits, underscore and hyphen. Case is kept as typed. */
    @Override
    public List<ValidationRule<String, UsernameProblem>> usernameRules() {
        return UsernameRules.of(2, 16, "[A-Za-z0-9_-]+");
    }

    /** True when a usable client id is configured; without one nothing is sent. */
    @Override
    public boolean isAvailable() {
        return CLIENT_ID.matcher(clientId).matches();
    }

    /** One request for one anime entry: 404 is a missing user and 403 a private list, which look the same to us. */
    @Override
    public AccountCheck checkAccountExists(String username) {
        try {
            requireUsable(username);
            Reply reply = get(listUri(username, Kind.ANIME, 1, 0));
            if (reply.status() == 403 || reply.status() == 404) {
                return AccountCheck.NOT_FOUND;
            }
            if (reply.status() != 200) {
                return unavailable("HTTP " + reply.status());
            }
            return reply.root().path("data").isArray() ? AccountCheck.EXISTS : unavailable("unexpected answer");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        } catch (IOException | RuntimeException e) {
            return unavailable(e.getClass().getSimpleName());
        }
    }

    /**
     * Anime list, manga list, then the AniList lookup of what passed. When neither list is readable the account
     * is gone or private: {@code ACCOUNT_NOT_FOUND_ON_PROVIDER}. Any other failure of either platform throws
     * {@code PROVIDER_UNAVAILABLE}, so nothing is applied half-way.
     */
    @Override
    public ActivitySnapshot fetchActivity(String username) {
        try {
            requireUsable(username);
            List<ActivityEntry> all = new ArrayList<>();
            int notReadable = 0;
            for (Kind kind : Kind.values()) {
                ListRead list = readList(username, kind);
                if (!list.readable()) {
                    notReadable++;
                } else {
                    all.addAll(toWorks(kind, list.entries()));
                }
            }
            if (notReadable == Kind.values().length) {
                throw failedWith("list not readable", Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);
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

    // ---- reading one list ----

    /** Pages through a list, keeping only the entries whose status counts, newest first, up to the cap. */
    private ListRead readList(String username, Kind kind) throws IOException, InterruptedException {
        List<Listed> kept = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            Reply reply = get(listUri(username, kind, PAGE_SIZE, offset));
            if (reply.status() == 403 || reply.status() == 404) {
                return new ListRead(false, List.of());
            }
            if (reply.status() != 200) {
                throw failed("HTTP " + reply.status());
            }
            JsonNode data = reply.root().path("data");
            if (!data.isArray()) {
                throw failed("unexpected answer");
            }
            for (JsonNode item : data) {
                listed(kind, item).ifPresent(kept::add);
            }
            // The next page is built from the offset; the URL MAL sends in paging.next is never requested.
            boolean more = !reply.root().path("paging").path("next").asString("").isEmpty();
            offset += data.size();
            if (!more || data.isEmpty() || kept.size() >= MAX_ENTRIES) {
                break;
            }
        }
        kept.sort(Comparator.comparing(Listed::when).reversed());
        return new ListRead(true, kept.size() > MAX_ENTRIES ? kept.subList(0, MAX_ENTRIES) : kept);
    }

    /** One list entry, or empty when its status does not count or it is unusable (one bad entry hides no other). */
    private static Optional<Listed> listed(Kind kind, JsonNode item) {
        try {
            long malId = item.path("node").path("id").asLong(0);
            JsonNode status = item.path("list_status");
            if (malId <= 0 || malId > MAX_MAL_ID) {
                return Optional.empty();
            }
            Optional<ActivityType> type = kind.typeOf(status.path("status").asString(""),
                    status.path(kind.repeatFlag).asBoolean(false));
            if (type.isEmpty()) {
                return Optional.empty();
            }
            String day = status.path(type.get() == ActivityType.FINISHED ? "finish_date" : "start_date").asString("");
            Optional<Instant> when = date(day).or(() -> updatedAt(status.path("updated_at").asString("")));
            return when.map(time -> new Listed(malId, type.get(), time));
        } catch (RuntimeException broken) {
            return Optional.empty();
        }
    }

    /** A full {@code YYYY-MM-DD} at midnight UTC (stable between syncs); MAL's partial dates ({@code 2020-05}) do not count. */
    private static Optional<Instant> date(String day) {
        try {
            LocalDate date = LocalDate.parse(day);
            return date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR
                    ? Optional.empty()
                    : Optional.of(date.atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (DateTimeException notADate) {
            return Optional.empty();
        }
    }

    private static Optional<Instant> updatedAt(String iso) {
        try {
            return Optional.of(OffsetDateTime.parse(iso).toInstant());
        } catch (DateTimeException notADate) {
            return Optional.empty();
        }
    }

    // ---- mapping to the AniList catalog ----

    /** Resolves the MAL ids through AniList and keeps each AniList work once; no match or an adult work is dropped. */
    private List<ActivityEntry> toWorks(Kind kind, List<Listed> entries) throws IOException, InterruptedException {
        Map<Long, CatalogEntry> works = anilist.worksByMalId(kind.category, entries.stream().map(Listed::malId).toList());
        List<ActivityEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int unmatched = 0;
        for (Listed entry : entries) {
            CatalogEntry work = works.get(entry.malId());
            if (work == null) {
                unmatched++;
            } else if (!work.adult() && seen.add(work.externalId())) {
                result.add(new ActivityEntry(work, entry.type(), entry.when()));
            }
        }
        if (unmatched > 0) {
            log.warn("Activity {} skipped {} {} entries without an AniList match", id(), unmatched, kind.category);
        }
        return result;
    }

    // ---- the request ----

    private URI listUri(String username, Kind kind, int limit, int offset) {
        return URI.create(endpoint + "users/" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "/" + kind.path
                + "?fields=list_status&sort=list_updated_at&limit=" + limit + "&offset=" + offset);
    }

    /** The client id goes in a header; the URI never carries it, so a URI in an exception cannot leak it. */
    private Reply get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "meuquarto/1.0")
                .header("X-MAL-CLIENT-ID", clientId)
                .GET()
                .build();
        spaceRequests();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                return new Reply(response.statusCode(), JSON.missingNode());
            }
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IOException("response too large");
            }
            return new Reply(200, JSON.readTree(new String(bytes, StandardCharsets.UTF_8)));
        }
    }

    /** Keeps the whole process under a gentle rate: each request waits for its slot. */
    private void spaceRequests() throws InterruptedException {
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

    /** Defense in depth: nothing is sent without a usable client id or with a name the rules refuse. */
    private void requireUsable(String username) {
        if (!isAvailable()) {
            throw new IllegalStateException("not configured");
        }
        if (username == null || validateUsernameFormat(username).isPresent()) {
            throw new IllegalArgumentException("invalid username");
        }
    }

    private AccountCheck unavailable(String reason) {
        log.warn("Activity {} check failed: {}", id(), reason);
        return AccountCheck.UNAVAILABLE;
    }

    private LinkedAccountException failed(String reason) {
        return failedWith(reason, Problem.PROVIDER_UNAVAILABLE);
    }

    private LinkedAccountException failedWith(String reason, Problem problem) {
        log.warn("Activity {} fetch failed: {}", id(), reason);
        return new LinkedAccountException(problem);
    }
}
