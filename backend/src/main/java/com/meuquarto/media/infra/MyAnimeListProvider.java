package com.meuquarto.media.infra;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.media.domain.UsernameProblem;
import com.meuquarto.media.domain.UsernameRules;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.shared.domain.ValidationRule;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * MyAnimeList, a {@code final} leaf of {@link JsonActivityProvider} (ADR 0018, hierarchy 1; ADR 0023 decision 4).
 * Reads the user's public anime and manga lists, page by page, with the project's client id in a header.
 * <p>
 * What counts (BR-10): completed, watching or reading, on hold and rewatching or rereading; never planned or
 * dropped. The time is the finish (or start) date when MAL has the full day, otherwise the last update.
 * <p>
 * <b>The work is the AniList work</b> (ADR 0005): the MAL ids are resolved through AniList's {@code idMal}
 * ({@link AniListProvider#worksByMalId}), so the same anime linked on both platforms appears once, and no
 * MyAnimeList title, picture or score is kept. An entry without a match is skipped. The next page is built
 * from the offset; the URL in {@code paging.next} is never followed.
 */
public final class MyAnimeListProvider extends JsonActivityProvider {

    static final URI ENDPOINT = URI.create("https://api.myanimelist.net/v2/");
    /** The most the API allows in one page of a list. */
    static final int PAGE_SIZE = 1000;
    static final int MAX_PAGES = 10;
    /** Entries kept per list before the lookup, and returned by one sync (newest first). Same cap as AniList. */
    static final int MAX_ENTRIES = 500;
    static final Duration REQUEST_SPACING = Duration.ofMillis(500);

    private static final Logger log = LoggerFactory.getLogger(MyAnimeListProvider.class);
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

    private final URI endpoint;
    private final String clientId;
    private final AniListProvider anilist;

    /** What production uses: the real endpoint and the client id from the environment (blank or invalid = unavailable). */
    public MyAnimeListProvider(String clientId, AniListProvider anilist) {
        this(HttpCatalogProvider.newClient(), ENDPOINT, clientId, anilist, REQUEST_TIMEOUT, REQUEST_SPACING);
    }

    /** For tests: a local stub server, no waiting. */
    MyAnimeListProvider(HttpClient client, URI endpoint, String clientId, AniListProvider anilist,
            Duration requestTimeout, Duration spacing) {
        super(client, requestTimeout, spacing);
        this.endpoint = endpoint;
        this.clientId = clientId == null ? "" : clientId.strip();
        this.anilist = anilist;
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

    @Override
    public boolean isAvailable() {
        return CLIENT_ID.matcher(clientId).matches();
    }

    @Override
    protected int maxEntries() {
        return MAX_ENTRIES;
    }

    @Override
    protected void authorize(HttpRequest.Builder request) {
        request.header("X-MAL-CLIENT-ID", clientId);
    }

    /** One request for one anime entry. */
    @Override
    protected AccountCheck check(String username) throws IOException, InterruptedException {
        Reply reply = get(listUri(username, Kind.ANIME, 1, 0));
        if (notReadable(reply)) {
            return AccountCheck.NOT_FOUND;
        }
        if (reply.status() != 200) {
            return unavailable("HTTP " + reply.status());
        }
        return reply.root().path("data").isArray() ? AccountCheck.EXISTS : unavailable("unexpected answer");
    }

    /** Anime list, manga list, then the AniList lookup. When neither list is readable the account is gone or private. */
    @Override
    protected List<ActivityEntry> readEntries(String username) throws IOException, InterruptedException {
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
            throw failed("list not readable", Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);
        }
        return all;
    }

    /** 404 is a missing user and 403 a private list; MAL's answer is the same for us. */
    private static boolean notReadable(Reply reply) {
        return reply.status() == 403 || reply.status() == 404;
    }

    // ---- reading one list ----

    /** Pages through a list, keeping only the entries whose status counts, newest first, up to the cap. */
    private ListRead readList(String username, Kind kind) throws IOException, InterruptedException {
        List<Listed> kept = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            Reply reply = get(listUri(username, kind, PAGE_SIZE, offset));
            if (notReadable(reply)) {
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
}
