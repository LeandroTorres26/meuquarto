package com.meuquarto.media.infra;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivitySnapshot;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import com.meuquarto.media.domain.ProfileUrls;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steam, a {@code final} leaf of hierarchy 1 (ADR 0018) that implements {@link ActivityProvider}
 * <b>directly</b>, like Last.fm and MyAnimeList: it speaks its own JSON API, so there is no base class
 * worth sharing. It feeds games from the user's public game list (BR-10, FR-15).
 * <p>
 * <b>The account.</b> The user types either the vanity name of {@code steamcommunity.com/id/{name}} or the
 * 17-digit SteamID64 of {@code steamcommunity.com/profiles/{id}}. Both pass the same format rule before
 * anything is sent ({@link #usernameRules()}); {@link ProfileUrls#isSteamId64} tells them apart. A SteamID64
 * is used as it is; a vanity name is resolved to one with {@code ISteamUser/ResolveVanityURL/v1} (one extra
 * request per check or sync; "no match" is {@code NOT_FOUND}). What is stored is what the user typed, and
 * {@link ProfileUrls} builds the matching profile link from it, so no URL typed by a user is ever kept.
 * <p>
 * <b>The games.</b> {@code IPlayerService/GetOwnedGames/v1} with {@code include_appinfo=1} (names) and
 * {@code include_played_free_games=1}. A private profile, or a private list of game details, comes back as
 * an empty {@code response} object: {@code NOT_FOUND} when linking and {@code ACCOUNT_NOT_FOUND_ON_PROVIDER}
 * when syncing, the same "account not found or private" every provider uses. BR-10: only a game with
 * {@code playtime_forever > 0} enters; an owned game never played is dropped. Each one is an
 * {@link ActivityType#OWNED} entry timed at {@code rtime_last_played}, the last time it was played (a game
 * with no usable date gets {@link #UNKNOWN_DATE}, a constant, so a repeated sync never makes a new entry).
 * <p>
 * <b>Agora</b> (FR-15, "Jogando"): {@code IPlayerService/GetRecentlyPlayedGames/v1} lists the games played in
 * the last two weeks. The one among them that was played most recently becomes an
 * {@link ActivityType#IN_PROGRESS} entry instead of an {@code OWNED} one, and "Agora" (the item with the
 * latest activity) shows it. Only a game that also passed BR-10 can be that one.
 * <p>
 * <b>The work</b> (ADR 0005): the canonical catalog of games is IGDB, which needs Twitch credentials and a
 * search per game. Like Goodreads and Last.fm, a Steam game is a <em>provisional</em> row
 * {@code GAME:STEAM:{appId}}: the name comes from Steam and the cover is built from the app id with a fixed
 * template on Steam's image host ({@link #IMAGE_HOSTS}); no other URL is ever used as a cover. IGDB is not
 * called while syncing. The app id is also what links the game to its Steam store page ({@code MediaLinks}).
 * <p>
 * <b>Safety and limits.</b> The host is fixed; both account formats are validated and everything is URL-encoded;
 * a SteamID64 read from an answer is validated again before it is used; no redirects; connect and request
 * timeouts; the body is capped at 8 MB; requests are spaced 300 ms apart; at most {@value #MAX_ENTRIES} games
 * per sync (the most recently played). <b>The key travels in the query string</b> (Steam offers no header), so
 * the request URL is never logged and never put in an exception message: failures log the provider and the
 * HTTP status or the class name only. A key that is missing or is not 32 hex digits makes the provider
 * {@linkplain #isAvailable() unavailable} and nothing is sent. Names and ids from the answer are text and
 * numbers only; nothing from it is treated as HTML or as a URL.
 */
public final class SteamProvider implements ActivityProvider {

    static final URI ENDPOINT = URI.create("https://api.steampowered.com/");
    /** The only hosts a cover may come from. Checked even though the URL is built here from a fixed template. */
    static final Set<String> IMAGE_HOSTS = Set.of("shared.akamai.steamstatic.com");
    /** The portrait "library capsule" of an app, 600x900: the same shape as the covers of the other games. */
    static final String COVER_TEMPLATE = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/%d/library_600x900.jpg";
    static final int MAX_ENTRIES = 500;
    static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    static final Duration REQUEST_SPACING = Duration.ofMillis(300);
    /** Steam opened in September 2003: no real {@code rtime_last_played} is older, so an older one is not a date. */
    static final Instant UNKNOWN_DATE = Instant.parse("2003-09-12T00:00:00Z");

    private static final Logger log = LoggerFactory.getLogger(SteamProvider.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern API_KEY = Pattern.compile("[A-Fa-f0-9]{32}");
    private static final Pattern COVER_PATH = Pattern.compile("/store_item_assets/steam/apps/[0-9]{1,10}/library_600x900[.]jpg");
    private static final int VANITY_NO_MATCH = 42;
    private static final long MAX_APP_ID = Integer.MAX_VALUE;
    private static final int MAX_TITLE = 300;
    private static final int MAX_IMAGE_URL = 200;

    /** A game that passed BR-10, with the time of its last play. */
    private record Played(long appId, String name, Instant when) {}

    /** What came back: the HTTP status and, only for a 200, the parsed body. */
    private record Reply(int status, JsonNode root) {}

    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final Clock clock;
    private final Duration requestTimeout;
    private final Duration spacing;
    private final Object gate = new Object();
    private long nextRequestAtNanos;

    /** What production uses: the real endpoint and the key from the environment (blank or invalid = unavailable). */
    public SteamProvider(String apiKey, Clock clock) {
        this(HttpCatalogProvider.newClient(), ENDPOINT, apiKey, clock, REQUEST_TIMEOUT, REQUEST_SPACING);
    }

    /**
     * For tests only (a local stub server, no waiting). Nothing in the application reads the address from
     * configuration or from a user: the production bean is built with the constructor above.
     */
    SteamProvider(HttpClient client, URI endpoint, String apiKey, Clock clock, Duration requestTimeout,
            Duration spacing) {
        this.client = client;
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.clock = clock;
        this.requestTimeout = requestTimeout;
        this.spacing = spacing;
    }

    // ---- ActivityProvider ----

    @Override
    public ProviderId id() {
        return ProviderId.STEAM;
    }

    @Override
    public Set<MediaCategory> categories() {
        return Set.of(MediaCategory.GAME);
    }

    /**
     * A vanity name (3 to 32 characters: letters, digits, underscore, hyphen) or a SteamID64 (17 digits, which
     * fit the same pattern). ASCII only, so a lookalike digit from another script is refused. Case is kept as typed.
     */
    @Override
    public List<ValidationRule<String, UsernameProblem>> usernameRules() {
        return UsernameRules.of(3, 32, "[A-Za-z0-9_-]+");
    }

    /** Without a usable key the provider cannot work, and the linking screen says "Em breve". */
    @Override
    public boolean isAvailable() {
        return API_KEY.matcher(apiKey).matches();
    }

    /** Resolves the name if needed, then asks for the game list: an empty answer means private (or missing). */
    @Override
    public AccountCheck checkAccountExists(String username) {
        try {
            requireUsable(username);
            Optional<String> steamId = steamIdOf(username);
            if (steamId.isEmpty()) {
                return AccountCheck.NOT_FOUND;
            }
            Reply reply = get("IPlayerService/GetOwnedGames/v1/", ownedGamesQuery(steamId.get()));
            if (reply.status() != 200) {
                return unavailable("HTTP " + reply.status());
            }
            JsonNode response = reply.root().path("response");
            if (!response.isObject()) {
                return unavailable("unexpected answer");
            }
            return isReadable(response) ? AccountCheck.EXISTS : AccountCheck.NOT_FOUND;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        } catch (IOException | RuntimeException e) {
            return unavailable(e.getClass().getSimpleName());
        }
    }

    /**
     * Resolve the account, read the owned games, read the recently played ones. A missing or private
     * account throws {@code ACCOUNT_NOT_FOUND_ON_PROVIDER} (the panel explains it); any other failure throws
     * {@code PROVIDER_UNAVAILABLE}, so nothing is applied half-way.
     */
    @Override
    public ActivitySnapshot fetchActivity(String username) {
        try {
            requireUsable(username);
            Optional<String> steamId = steamIdOf(username);
            if (steamId.isEmpty()) {
                throw failedWith("no such account", Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);
            }
            JsonNode owned = response(get("IPlayerService/GetOwnedGames/v1/", ownedGamesQuery(steamId.get())));
            if (!isReadable(owned)) {
                throw failedWith("game list not readable", Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);
            }
            JsonNode recent = response(get("IPlayerService/GetRecentlyPlayedGames/v1/",
                    "steamid=" + steamId.get() + "&count=10"));
            return new ActivitySnapshot(toEntries(playedGames(owned.path("games")), recentIds(recent.path("games"))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed("interrupted");
        } catch (LinkedAccountException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw failed(e.getClass().getSimpleName());
        }
    }

    // ---- the account ----

    /** A SteamID64 is used as typed; a vanity name is resolved. Empty when Steam has no such name. */
    private Optional<String> steamIdOf(String username) throws IOException, InterruptedException {
        if (ProfileUrls.isSteamId64(username)) {
            return Optional.of(username);
        }
        Reply reply = get("ISteamUser/ResolveVanityURL/v1/", "vanityurl=" + encode(username));
        JsonNode answer = response(reply);
        int success = answer.path("success").asInt(0);
        if (success == VANITY_NO_MATCH) {
            return Optional.empty();
        }
        String steamId = answer.path("steamid").asString("");
        if (success != 1 || !ProfileUrls.isSteamId64(steamId)) {
            throw new IOException("unexpected answer"); // never use an id that does not look like one
        }
        return Optional.of(steamId);
    }

    private static String ownedGamesQuery(String steamId) {
        return "steamid=" + steamId + "&include_appinfo=1&include_played_free_games=1";
    }

    /**
     * Steam answers a private profile or private game details with an empty {@code response}. A public profile
     * has {@code game_count}, even when it is 0 (and {@code games} when it has any).
     */
    private static boolean isReadable(JsonNode response) {
        return response.has("games") || response.has("game_count");
    }

    /** The {@code response} object of a 200 answer; anything else is a failure with no details. */
    private static JsonNode response(Reply reply) throws IOException {
        if (reply.status() != 200) {
            throw new IOException("HTTP " + reply.status());
        }
        JsonNode response = reply.root().path("response");
        if (!response.isObject()) {
            throw new IOException("unexpected answer");
        }
        return response;
    }

    // ---- mapping ----

    /** BR-10: the games with playtime above zero, newest play first (ties by app id, so the order is stable). */
    private List<Played> playedGames(JsonNode games) {
        List<Played> played = new ArrayList<>();
        if (games.isArray()) {
            for (JsonNode game : games) {
                played(game).ifPresent(played::add);
            }
        }
        played.sort(Comparator.comparing(Played::when).reversed().thenComparingLong(Played::appId));
        return played;
    }

    /** One game, or empty when it was never played (BR-10) or is unusable: one bad game hides no other. */
    private Optional<Played> played(JsonNode game) {
        try {
            long appId = game.path("appid").asLong(0);
            String name = clean(game.path("name").asString(""));
            if (appId <= 0 || appId > MAX_APP_ID || name.isEmpty() || game.path("playtime_forever").asLong(0) <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Played(appId, name, lastPlayed(game.path("rtime_last_played").asLong(0))));
        } catch (RuntimeException broken) {
            return Optional.empty();
        }
    }

    /** The last play as an instant; a missing, ancient or future value is not a date and becomes the constant. */
    private Instant lastPlayed(long seconds) {
        if (seconds <= 0) {
            return UNKNOWN_DATE;
        }
        Instant when = Instant.ofEpochSecond(seconds);
        boolean plausible = !when.isBefore(UNKNOWN_DATE) && !when.isAfter(clock.instant().plus(1, ChronoUnit.DAYS));
        return plausible ? when : UNKNOWN_DATE;
    }

    /** The app ids of the recently played games, in the order Steam lists them. */
    private static List<Long> recentIds(JsonNode games) {
        List<Long> ids = new ArrayList<>();
        if (games.isArray()) {
            for (JsonNode game : games) {
                long appId = game.path("appid").asLong(0);
                if (appId > 0) {
                    ids.add(appId);
                }
            }
        }
        return ids;
    }

    /** Every played game as {@code OWNED}, except the most recently played of the recent ones: {@code IN_PROGRESS}. */
    private List<ActivityEntry> toEntries(List<Played> played, List<Long> recent) {
        List<Played> kept = played.size() > MAX_ENTRIES ? played.subList(0, MAX_ENTRIES) : played;
        Played now = null;
        for (long appId : recent) {
            for (Played game : kept) {
                if (game.appId() == appId && (now == null || game.when().isAfter(now.when()))) {
                    now = game;
                }
            }
        }
        List<ActivityEntry> entries = new ArrayList<>();
        for (Played game : kept) {
            ActivityType type = game == now ? ActivityType.IN_PROGRESS : ActivityType.OWNED;
            entries.add(new ActivityEntry(work(game), type, game.when()));
        }
        return entries;
    }

    private static CatalogEntry work(Played game) {
        return new CatalogEntry(MediaCategory.GAME, ProviderId.STEAM.name(), Long.toString(game.appId()),
                game.name(), null, cover(game.appId()), false);
    }

    private static String clean(String raw) {
        String name = raw.replaceAll("\\s+", " ").strip();
        return name.length() <= MAX_TITLE ? name : name.substring(0, MAX_TITLE);
    }

    /** The cover of an app: the fixed template filled with its number, and only if the result passes the allow-list. */
    static String cover(long appId) {
        return allowedImage(String.format(Locale.ROOT, COVER_TEMPLATE, appId));
    }

    /** Only https, a listed host, no user info, port, query or fragment, and the exact path of a library cover. */
    static String allowedImage(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_IMAGE_URL) {
            return null;
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * One GET. The key is in this URL, so the URL is built here, used once and never logged or put in a
     * message. {@code query} holds only validated or encoded values.
     */
    private Reply get(String path, String query) throws IOException, InterruptedException {
        URI uri = URI.create(endpoint + path + "?key=" + apiKey + "&" + query + "&format=json");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "meuquarto/1.0")
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

    /** Defense in depth: nothing is sent without a usable key or with a name the rules refuse. */
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
