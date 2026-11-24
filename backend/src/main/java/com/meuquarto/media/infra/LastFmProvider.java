package com.meuquarto.media.infra;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.LinkHints;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.media.domain.UsernameProblem;
import com.meuquarto.media.domain.UsernameRules;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.shared.domain.ValidationRule;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Last.fm, a {@code final} leaf of {@link JsonActivityProvider} (ADR 0018, hierarchy 1). One request for the
 * newest 200 scrobbles ({@code user.getrecenttracks}) feeds both uses of the platform:
 * <ul>
 *   <li><b>Agora</b> (FR-15): the album of the track playing now is an {@link ActivityType#IN_PROGRESS} entry;</li>
 *   <li><b>Collection</b> (BR-10): an album with at least {@link #MIN_SCROBBLES} scrobbles in that window is one
 *       {@link ActivityType#LOVED} entry at its newest scrobble. A single play never adds anything.</li>
 * </ul>
 * The recent window is used instead of {@code user.gettopalbums} because the top list has no dates, and an
 * entry needs a stable one. An album is a provisional row {@code MUSIC:LASTFM:{key}} (ADR 0005), see
 * {@link #keyOf}. Last.fm takes the API key in the URL, so the URL is never logged.
 */
public final class LastFmProvider extends JsonActivityProvider {

    static final URI ENDPOINT = URI.create("https://ws.audioscrobbler.com/2.0/");
    /** The only hosts a cover may come from. */
    static final Set<String> IMAGE_HOSTS = Set.of("lastfm.freetls.fastly.net", "lastfm-img2.akamaized.net");
    /** The hash in the path of Last.fm's "no image" star. */
    static final String PLACEHOLDER_IMAGE = "2a96cbd8b46e442fc41c2b86b821562f";
    /** BR-10: scrobbles of an album, in the window, before it counts for the collection. */
    static final int MIN_SCROBBLES = 10;
    /** The most Last.fm returns in one page of recent tracks. */
    static final int WINDOW = 200;
    /** About 4 requests per second, under the documented limit of 5. */
    static final Duration REQUEST_SPACING = Duration.ofMillis(250);

    private static final Logger log = LoggerFactory.getLogger(LastFmProvider.class);
    private static final Pattern API_KEY = Pattern.compile("[A-Za-z0-9]{8,64}");
    private static final Pattern COVER_PATH = Pattern.compile("/i/u/(?:[A-Za-z0-9_-]{1,40}/)?[A-Za-z0-9_-]{1,100}[.](?:jpg|jpeg|png|gif|webp)");
    private static final int ERROR_USER_NOT_FOUND = 6;
    private static final int ERROR_LOGIN_REQUIRED = 17; // recent listening is private
    private static final int MAX_NAME = 150;
    private static final int MAX_TITLE = 300;
    private static final int MAX_IMAGE_URL = 500;
    private static final int MAX_ENTRIES = 200;
    private static final List<String> SIZES = List.of("extralarge", "large", "medium", "small");

    private final URI endpoint;
    private final String apiKey;
    private final Clock clock;

    /** What production uses: the real endpoint and the key from the environment (blank or invalid = unavailable). */
    public LastFmProvider(String apiKey, Clock clock) {
        this(HttpCatalogProvider.newClient(), ENDPOINT, apiKey, clock, REQUEST_TIMEOUT, REQUEST_SPACING);
    }

    /** For tests: a local stub server, no waiting. */
    LastFmProvider(HttpClient client, URI endpoint, String apiKey, Clock clock, Duration requestTimeout,
            Duration spacing) {
        super(client, requestTimeout, spacing);
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.clock = clock;
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

    @Override
    public boolean isAvailable() {
        return API_KEY.matcher(apiKey).matches();
    }

    @Override
    protected int maxEntries() {
        return MAX_ENTRIES;
    }

    /** One request for the newest scrobble. */
    @Override
    protected AccountCheck check(String username) throws IOException, InterruptedException {
        Answer answer = recentTracks(username, 1);
        if (answer.noPublicAccount()) {
            return AccountCheck.NOT_FOUND;
        }
        return answer.usable() ? AccountCheck.EXISTS : unavailable(answer.describe());
    }

    /** One request. A user that vanished or went private is reported as such; the panel explains it. */
    @Override
    protected List<ActivityEntry> readEntries(String username) throws IOException, InterruptedException {
        Answer answer = recentTracks(username, WINDOW);
        if (answer.noPublicAccount()) {
            throw failed(answer.describe(), Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);
        }
        if (!answer.usable()) {
            throw failed(answer.describe());
        }
        return toEntries(answer.recent().path("track"));
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
            if (album.scrobbles >= MIN_SCROBBLES) {
                entries.add(new ActivityEntry(album.work(), ActivityType.LOVED, album.latest));
            }
        }
        return entries;
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
        String title = bounded(name, MAX_TITLE);
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

    /** Last.fm sends its errors as JSON, sometimes with a 4xx status: {@code errorCode} is 0 when there is none. */
    private record Answer(int status, JsonNode recent, int errorCode) {

        /** Error 6 (no such user) or 17 (the listening is private): the account is not a public one. */
        boolean noPublicAccount() {
            return errorCode == ERROR_USER_NOT_FOUND || errorCode == ERROR_LOGIN_REQUIRED;
        }

        boolean usable() {
            return status == 200 && errorCode == 0 && recent.isObject();
        }

        /** For the log: only numbers, never text from the response and never the request. */
        String describe() {
            return errorCode != 0 ? "Last.fm error " + errorCode : "HTTP " + status;
        }
    }

    private Answer recentTracks(String username, int limit) throws IOException, InterruptedException {
        // The key is in this URL, so the URL is built here, used once and never logged or put in a message.
        Reply reply = get(URI.create(endpoint + "?method=user.getrecenttracks&user="
                + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&api_key=" + apiKey
                + "&format=json&limit=" + limit));
        return new Answer(reply.status(), reply.root().path("recenttracks"), reply.root().path("error").asInt(0));
    }
}
