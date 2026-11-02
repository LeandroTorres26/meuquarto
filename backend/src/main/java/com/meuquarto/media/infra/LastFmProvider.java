package com.meuquarto.media.infra;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivitySnapshot;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.LinkHints;
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
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * Last.fm, the third kind of leaf of hierarchy 1 (ADR 0018): a {@code final} class that implements
 * {@link ActivityProvider} <b>directly</b>. It shares nothing with the RSS providers (it speaks JSON, and
 * AniList's GraphQL has a different shape), so there is no base class to extend and no duplicated code
 * worth a new abstract one; the contract is the only thing it has in common with the others.
 * <p>
 * One request per link check or sync: {@code user.getrecenttracks} with the newest 200 scrobbles
 * ({@code GET https://ws.audioscrobbler.com/2.0/}). That single answer feeds both uses of the platform:
 * <ul>
 *   <li><b>Agora</b> (FR-15, "Ouvindo"): when the first track has {@code nowplaying="true"} its album
 *       becomes an {@link ActivityType#IN_PROGRESS} entry, timed at the moment of the fetch (Last.fm gives
 *       no date for what is playing);</li>
 *   <li><b>Collection</b> (BR-10): the scrobbles are grouped by <i>album</i> (the unit of music here; a
 *       scrobble without an album is skipped), and an album with at least {@link #MIN_SCROBBLES}
 *       scrobbles in that window becomes one {@link ActivityType#LOVED} entry, timed at its newest
 *       scrobble. A single play never adds anything by itself ("never adds individual plays").</li>
 * </ul>
 * Why the recent window and not {@code user.gettopalbums}: the top list has the long-term counts but no
 * date, and an entry needs a stable date (the dedup key of a synced entry includes it; a date that is
 * "now" would add a row per album on every sync and move all of them to the top of the collection).
 * The cost is that the count is "in the last 200 plays", so an album heard a lot long ago does not
 * qualify; the collection accumulates from the link date on, as the Letterboxd one does. Loved tracks
 * ({@code user.getlovedtracks}) are not read: they carry no album, and the unit is the album.
 * <p>
 * Dedup (ADR 0005): Deezer is the canonical catalog of music; Last.fm albums carry a MusicBrainz id only
 * sometimes (and the same album can appear with and without it), and mapping to Deezer would need a fuzzy
 * search per album. So the work is a <em>provisional</em> row {@code MUSIC:LASTFM:{key}} where the key is
 * a SHA-256 of the artist and album names after a purely mechanical normalization (Unicode NFKC,
 * lower-case, whitespace collapsed). Same names give the same key for every user; any difference beyond
 * case and spacing is a different item (it can be merged later, as with Goodreads).
 * <p>
 * Safety: the host is fixed ({@code ws.audioscrobbler.com}); the username passed the Last.fm format rules
 * and is URL-encoded; no redirects; connect and request timeouts; the body is capped at 2 MB; requests
 * are spaced to stay under Last.fm's ~5 per second. <b>The API key travels in the query string</b> (that
 * is how Last.fm works), so the request URL is never logged, never put in an exception message, and only
 * the class name and a numeric code of a failure are logged. A key that is missing, or not
 * alphanumeric, makes the provider report itself unavailable without sending anything. Covers are kept
 * only from Last.fm's image hosts, and the grey star placeholder is dropped.
 */
public final class LastFmProvider implements ActivityProvider {

    static final URI ENDPOINT = URI.create("https://ws.audioscrobbler.com/2.0/");
    /** The only hosts a cover may come from. */
    static final Set<String> IMAGE_HOSTS = Set.of("lastfm.freetls.fastly.net", "lastfm-img2.akamaized.net");
    /** The hash in the path of Last.fm's "no image" star. */
    static final String PLACEHOLDER_IMAGE = "2a96cbd8b46e442fc41c2b86b821562f";
    /** BR-10: scrobbles of an album, in the window, before it counts for the collection. */
    static final int MIN_SCROBBLES = 10;
    /** The most Last.fm returns in one page of recent tracks. */
    static final int WINDOW = 200;
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    /** About 4 requests per second, under the documented limit of 5. */
    static final Duration REQUEST_SPACING = Duration.ofMillis(250);

    private static final Logger log = LoggerFactory.getLogger(LastFmProvider.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern API_KEY = Pattern.compile("[A-Za-z0-9]{8,64}");
    private static final Pattern COVER_PATH = Pattern.compile("/i/u/(?:[A-Za-z0-9_-]{1,40}/)?[A-Za-z0-9_-]{1,100}[.](?:jpg|jpeg|png|gif|webp)");
    private static final int ERROR_USER_NOT_FOUND = 6;
    private static final int ERROR_LOGIN_REQUIRED = 17; // recent listening is private
    private static final int MAX_NAME = 150;
    private static final int MAX_TITLE = 300;
    private static final int MAX_IMAGE_URL = 500;
    private static final int MAX_ENTRIES = 200;
    private static final List<String> SIZES = List.of("extralarge", "large", "medium", "small");

    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final Clock clock;
    private final Duration requestTimeout;
    private final Duration spacing;
    private final int minScrobbles;
    private final Object gate = new Object();
    private long nextRequestAtNanos;

    /** What production uses: the real endpoint and the key from the environment (blank or invalid = unavailable). */
    public LastFmProvider(String apiKey, Clock clock) {
        this(HttpCatalogProvider.newClient(), ENDPOINT, apiKey, clock, REQUEST_TIMEOUT, REQUEST_SPACING, MIN_SCROBBLES);
    }

    /**
     * For tests only (a local stub server, no waiting). Nothing in the application reads the address from
     * configuration or from a user: the production bean is built with the constructor above.
     */
    LastFmProvider(HttpClient client, URI endpoint, String apiKey, Clock clock, Duration requestTimeout,
            Duration spacing, int minScrobbles) {
        this.client = client;
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.clock = clock;
        this.requestTimeout = requestTimeout;
        this.spacing = spacing;
        this.minScrobbles = minScrobbles;
    }

    // ---- ActivityProvider ----

    @Override
    public ProviderId id() {
        return ProviderId.LASTFM;
    }

    @Override
    public Set<MediaCategory> categories() {
        return Set.of(MediaCategory.MUSIC);
    }

    /** Last.fm names: 2 to 15 characters, a letter first, then letters, digits, underscore or hyphen. */
    @Override
    public List<ValidationRule<String, UsernameProblem>> usernameRules() {
        return UsernameRules.of(2, 15, "[A-Za-z][A-Za-z0-9_-]*");
    }

    /** True when a usable key is configured; without one nothing is sent and every call reports "unavailable". */
    public boolean isConfigured() {
        return API_KEY.matcher(apiKey).matches();
    }

    /** Without a usable key the provider cannot work, and the linking screen says so. */
    @Override
    public boolean isAvailable() {
        return isConfigured();
    }

    /** One request for the newest scrobble. Last.fm answers error 6 for a missing user and 17 for private listening. */
    @Override
    public AccountCheck checkAccountExists(String username) {
        try {
            requireUsable(username);
            Answer answer = recentTracks(username, 1);
            if (answer.noPublicAccount()) {
                return AccountCheck.NOT_FOUND;
            }
            if (answer.status() != 200 || answer.errorCode() != 0) {
                return unavailable(answer.describe());
            }
            return answer.root().path("recenttracks").isObject() ? AccountCheck.EXISTS : unavailable("unexpected answer");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        } catch (IOException | RuntimeException e) {
            return unavailable(e.getClass().getSimpleName());
        }
    }

    /**
     * One request. A user that vanished or went private throws {@code ACCOUNT_NOT_FOUND_ON_PROVIDER} (the
     * panel explains it); any other failure throws {@code PROVIDER_UNAVAILABLE}.
     */
    @Override
    public ActivitySnapshot fetchActivity(String username) {
        try {
            requireUsable(username);
            Answer answer = recentTracks(username, WINDOW);
            if (answer.noPublicAccount()) {
                throw failedWith(answer.describe(), Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);
            }
            if (answer.status() != 200 || answer.errorCode() != 0) {
                throw failed(answer.describe());
            }
            JsonNode recent = answer.root().path("recenttracks");
            if (!recent.isObject()) {
                throw failed("unexpected answer");
            }
            return new ActivitySnapshot(toEntries(recent.path("track")));
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

    /** The albums of a page of tracks, as activity: what is playing now, then the albums played enough. */
    private List<ActivityEntry> toEntries(JsonNode tracks) {
        List<JsonNode> list = new ArrayList<>();
        if (tracks.isArray()) {
            tracks.forEach(list::add);
        } else if (tracks.isObject()) {
            list.add(tracks); // Last.fm sends a single track as an object, not as a one-item array
        }
        Map<String, Album> albums = new LinkedHashMap<>();
        List<ActivityEntry> entries = new ArrayList<>();
        int skipped = 0;
        for (JsonNode track : list) {
            try {
                Optional<Album> album = album(track);
                if (album.isEmpty()) {
                    skipped++; // no album or no artist: not a unit we keep
                } else if (track.path("@attr").path("nowplaying").asString("").equals("true")) {
                    entries.add(new ActivityEntry(album.get().work(), ActivityType.IN_PROGRESS,
                            clock.instant().truncatedTo(ChronoUnit.SECONDS)));
                } else {
                    Instant when = scrobbledAt(track);
                    if (when != null) {
                        albums.computeIfAbsent(album.get().key(), key -> album.get()).add(when, album.get());
                    }
                }
            } catch (RuntimeException broken) {
                skipped++; // one malformed track must not hide the others
            }
        }
        if (skipped > 0) {
            log.warn("Activity {} skipped {} unusable tracks", id(), skipped);
        }
        for (Album album : albums.values()) {
            if (album.scrobbles >= minScrobbles) {
                entries.add(new ActivityEntry(album.work(), ActivityType.LOVED, album.latest));
            }
        }
        entries.sort(Comparator.comparing(ActivityEntry::occurredAt).reversed());
        return entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries;
    }

    /** An album as seen on a track, with the number of scrobbles counted so far. */
    private static final class Album {
        private final String key;
        private final String title;
        private final String artist;
        private final String name;
        private String cover;
        private int scrobbles;
        private Instant latest = Instant.EPOCH;

        private Album(String key, String title, String artist, String name, String cover) {
            this.key = key;
            this.title = title;
            this.artist = artist;
            this.name = name;
            this.cover = cover;
        }

        String key() {
            return key;
        }

        void add(Instant when, Album seenAgain) {
            scrobbles++;
            if (when.isAfter(latest)) {
                latest = when;
            }
            if (cover == null) {
                cover = seenAgain.cover; // the first scrobble may have had no cover; a later one may
            }
        }

        CatalogEntry work() {
            return new CatalogEntry(MediaCategory.MUSIC, ProviderId.LASTFM.name(), key, title, null, cover, false,
                    LinkHints.ofAlbum(artist, name)); // the names address the album's Last.fm page (FR-10a)
        }
    }

    private static Optional<Album> album(JsonNode track) {
        String artist = clean(firstText(track.path("artist").path("#text"), track.path("artist").path("name")));
        String name = clean(track.path("album").path("#text").asString(""));
        if (artist.isEmpty() || name.isEmpty()) {
            return Optional.empty();
        }
        String title = bounded(name + " - " + artist, MAX_TITLE);
        return Optional.of(new Album(keyOf(artist, name), title, artist, name, cover(track.path("image"))));
    }

    private static Instant scrobbledAt(JsonNode track) {
        long seconds = track.path("date").path("uts").asLong(0);
        return seconds > 0 ? Instant.ofEpochSecond(seconds) : null;
    }

    /** Exact names only: NFKC, lower-case, whitespace collapsed. No transliteration, no "feat." stripping, no fuzziness. */
    static String keyOf(String artist, String album) {
        String text = normalize(artist) + "\n" + normalize(album);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 40);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", impossible);
        }
    }

    private static String normalize(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static String clean(String raw) {
        return bounded(raw.replaceAll("\\s+", " ").strip(), MAX_NAME);
    }

    private static String firstText(JsonNode first, JsonNode second) {
        String text = first.asString("").strip();
        return text.isEmpty() ? second.asString("").strip() : text;
    }

    private static String bounded(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** The largest usable cover of the sizes Last.fm lists; none when no one is an allowed https image. */
    private static String cover(JsonNode images) {
        Map<String, String> bySize = new LinkedHashMap<>();
        for (JsonNode image : images) {
            bySize.put(image.path("size").asString(""), image.path("#text").asString(""));
        }
        for (String size : SIZES) {
            String url = allowedImage(bySize.getOrDefault(size, ""));
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static String allowedImage(String raw) {
        if (raw.isEmpty() || raw.length() > MAX_IMAGE_URL || raw.contains(PLACEHOLDER_IMAGE)) {
            return null; // empty, or the grey star Last.fm sends when there is no cover
        }
        try {
            URI uri = new URI(raw);
            boolean allowed = "https".equals(uri.getScheme()) && uri.getHost() != null
                    && IMAGE_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && uri.getRawUserInfo() == null && uri.getPort() == -1 && uri.getRawFragment() == null
                    && uri.getRawQuery() == null && uri.getRawPath() != null
                    && COVER_PATH.matcher(uri.getRawPath()).matches();
            return allowed ? uri.toString() : null;
        } catch (URISyntaxException notAUrl) {
            return null;
        }
    }

    // ---- the request ----

    /** What came back: the HTTP status, the parsed JSON (or a missing node) and Last.fm's own error code (0 for none). */
    private record Answer(int status, JsonNode root, int errorCode) {

        /** Error 6 (no such user) or 17 (the listening is private): the account is not a public one. */
        boolean noPublicAccount() {
            return errorCode == ERROR_USER_NOT_FOUND || errorCode == ERROR_LOGIN_REQUIRED;
        }

        /** For the log: only numbers, never text from the response and never the request. */
        String describe() {
            return errorCode != 0 ? "Last.fm error " + errorCode : "HTTP " + status;
        }
    }

    private Answer recentTracks(String username, int limit) throws IOException, InterruptedException {
        // The key is in this URL, so the URL is built here, used once and never logged or put in a message.
        URI uri = URI.create(endpoint + "?method=user.getrecenttracks&user="
                + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&api_key=" + apiKey
                + "&format=json&limit=" + limit);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "meuquarto/1.0")
                .GET()
                .build();
        spaceRequests();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IOException("response too large");
            }
            JsonNode root = parseOrMissing(new String(bytes, StandardCharsets.UTF_8));
            return new Answer(response.statusCode(), root, root.path("error").asInt(0));
        }
    }

    /** Last.fm sends its errors as JSON, sometimes with a 4xx status: the body is read for every status. */
    private static JsonNode parseOrMissing(String body) {
        try {
            return JSON.readTree(body);
        } catch (RuntimeException notJson) {
            return JSON.missingNode();
        }
    }

    /** Keeps the whole process under Last.fm's rate limit: each request waits for its slot. */
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

    /** Defense in depth: nothing is sent without a usable key or with a name the rules refuse. */
    private void requireUsable(String username) {
        if (!isConfigured()) {
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
