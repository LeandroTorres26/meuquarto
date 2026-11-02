package com.meuquarto.media.infra;

import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.media.domain.UsernameProblem;
import com.meuquarto.media.domain.UsernameRules;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.shared.domain.ValidationRule;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Letterboxd, the first concrete leaf of {@link RssActivityProvider} (ADR 0018, hierarchy 1). It inherits
 * the whole algorithm (safe URL, download with limits, XML without trust, the per-item error isolation)
 * and only says <b>where</b> the feed is ({@link #baseUri}, {@link #feedPath}) and <b>how</b> one
 * {@code <item>} becomes an {@link ActivityEntry} ({@link #toEntry}). The class is {@code final}: nothing
 * extends a leaf.
 * <p>
 * The public feed {@code https://letterboxd.com/{user}/rss/} lists the latest diary entries (about 50).
 * Each film entry carries the {@code letterboxd:} elements ({@code filmTitle}, {@code filmYear},
 * {@code watchedDate}, {@code rewatch}, {@code memberRating}) and the {@code tmdb:} element
 * ({@code movieId}, or {@code tvId} for a series). Elements are matched by local name in any namespace,
 * so a change of prefix or namespace URI by Letterboxd does not break the parser.
 * <p>
 * Mapping (BR-04, BR-10): the work is the canonical TMDB movie {@code tmdb:movieId} (ADR 0005), so a film
 * added by hand from TMDB and the same film from the diary are one item. Skipped, never an error: lists
 * and anything else that is not a diary entry, series ({@code tvId} only), entries without a TMDB id or
 * title. A watch and a rewatch are both {@link ActivityType#FINISHED}; they differ by date, and the
 * activity key includes the date, so each viewing is its own activity. {@code adult} is false: the feed
 * does not say, so BR-08 is enforced where the data exists (the TMDB catalog when adding by hand).
 * <p>
 * Everything read from the feed is untrusted text: titles are kept as plain text (the UI never renders
 * HTML from a provider), the poster URL is accepted only from a fixed list of image hosts, and the TMDB
 * id must be digits.
 */
public final class LetterboxdProvider extends RssActivityProvider {

    static final URI BASE = URI.create("https://letterboxd.com");

    /** The only hosts a poster may come from: Letterboxd's own image CDN and the TMDB one. */
    static final Set<String> IMAGE_HOSTS = Set.of("a.ltrbxd.com", "image.tmdb.org");

    private static final String TMDB = "TMDB";
    private static final String ANY_NAMESPACE = "*";
    private static final Pattern TMDB_ID = Pattern.compile("[0-9]{1,10}");
    private static final Pattern IMG_SRC = Pattern.compile("<img\\s[^>]*?src\\s*=\\s*[\"']([^\"'<>\\s]{1,500})[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_TITLE = 300;
    private static final int MAX_DESCRIPTION = 20_000;
    private static final int MIN_YEAR = 1870;
    private static final int MAX_YEAR = 2200;

    private final URI base;

    /** What production uses: the real Letterboxd origin. */
    public LetterboxdProvider() {
        this(HttpCatalogProvider.newClient(), BASE, REQUEST_TIMEOUT);
    }

    /**
     * For tests only (a local stub server). Nothing in the application reads this from configuration or
     * from a user: the production bean is built with the constructor above.
     */
    LetterboxdProvider(HttpClient client, URI base, Duration timeout) {
        super(client, timeout);
        this.base = base;
    }

    @Override
    public ProviderId id() {
        return ProviderId.LETTERBOXD;
    }

    @Override
    public Set<MediaCategory> categories() {
        return Set.of(MediaCategory.MOVIE);
    }

    /**
     * Letterboxd usernames: 2 to 15 characters, letters, digits and underscore. This is the rule observed
     * on the sign-up form, not a published specification; if it is looser in reality a valid account would
     * be refused here (safe side), never the other way round. Names are case-insensitive, so we store lowercase.
     */
    @Override
    public List<ValidationRule<String, UsernameProblem>> usernameRules() {
        return UsernameRules.of(2, 15, "[a-z0-9_]+");
    }

    @Override
    public String normalizeUsername(String raw) {
        return super.normalizeUsername(raw).toLowerCase(Locale.ROOT);
    }

    @Override
    protected URI baseUri() {
        return base;
    }

    @Override
    protected String feedPath(String encodedUsername) {
        return "/" + encodedUsername + "/rss/";
    }

    @Override
    protected Optional<ActivityEntry> toEntry(Element item, String feedQuery) {
        if (!isDiaryEntry(text(item, "guid"))) {
            return Optional.empty(); // a list, or something that is not a film log
        }
        String movieId = text(item, "movieId");
        String title = text(item, "filmTitle");
        if (!TMDB_ID.matcher(movieId).matches() || Long.parseLong(movieId) <= 0 || title.isEmpty()) {
            return Optional.empty(); // no TMDB movie (a series has only tvId): cannot be the canonical work
        }
        Optional<Instant> when = watchedAt(text(item, "watchedDate"), text(item, "pubDate"));
        if (when.isEmpty()) {
            return Optional.empty();
        }
        CatalogEntry work = new CatalogEntry(MediaCategory.MOVIE, TMDB, Long.toString(Long.parseLong(movieId)),
                bounded(title, MAX_TITLE), year(text(item, "filmYear")), poster(text(item, "description")), false);
        return Optional.of(new ActivityEntry(work, ActivityType.FINISHED, when.get()));
    }

    /** Diary entries have a guid {@code letterboxd-review-N} or {@code letterboxd-watch-N}; lists are {@code letterboxd-list-N}. */
    private static boolean isDiaryEntry(String guid) {
        return guid.startsWith("letterboxd-review-") || guid.startsWith("letterboxd-watch-");
    }

    /** The day the film was watched (midnight UTC, stable between syncs); the publication time if the day is missing or broken. */
    private static Optional<Instant> watchedAt(String watchedDate, String pubDate) {
        try {
            return Optional.of(LocalDate.parse(watchedDate).atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (DateTimeParseException noDay) {
            try {
                return Optional.of(ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (DateTimeParseException noDate) {
                return Optional.empty();
            }
        }
    }

    private static Integer year(String raw) {
        if (raw.length() == 4 && raw.chars().allMatch(Character::isDigit)) {
            int year = Integer.parseInt(raw);
            return year >= MIN_YEAR && year <= MAX_YEAR ? year : null;
        }
        return null;
    }

    /** The first {@code <img>} of the description if it is an https image on an allowed host; otherwise nothing. */
    private static String poster(String description) {
        Matcher match = IMG_SRC.matcher(bounded(description, MAX_DESCRIPTION));
        if (!match.find()) {
            return null;
        }
        try {
            URI uri = new URI(match.group(1));
            boolean allowed = "https".equals(uri.getScheme()) && uri.getHost() != null
                    && IMAGE_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && uri.getRawUserInfo() == null && uri.getPort() == -1 && uri.getRawFragment() == null;
            return allowed ? uri.toString() : null;
        } catch (URISyntaxException notAUrl) {
            return null;
        }
    }

    /** Text of the first element with this local name, in any namespace; empty when absent. Trimmed. */
    private static String text(Element item, String localName) {
        NodeList found = item.getElementsByTagNameNS(ANY_NAMESPACE, localName);
        return found.getLength() == 0 ? "" : found.item(0).getTextContent().strip();
    }

    private static String bounded(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
