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
import org.w3c.dom.Node;

/**
 * Goodreads, the second concrete leaf of {@link RssActivityProvider} (ADR 0018, hierarchy 1). Like
 * {@link LetterboxdProvider} it only says <b>where</b> the feeds are and <b>how</b> one {@code <item>}
 * becomes an {@link ActivityEntry}; the safe download, the XML without trust and the per-item error
 * isolation are inherited. The class is {@code final}.
 * <p>
 * Goodreads has one public RSS feed per shelf: {@code /review/list_rss/{userId}?shelf=read} and
 * {@code ?shelf=currently-reading}. The user is identified by the <b>numeric</b> Goodreads id (the number
 * in {@code goodreads.com/user/show/12345678-name}), so that is the "username" we store; the optional
 * {@code -name} suffix a user may paste is dropped by {@link #normalizeUsername}. The shelves are the
 * two fixed queries of {@link #feedQueries}; {@link #toEntry} receives which one an item came from.
 * <p>
 * Mapping (BR-10): shelf {@code read} is {@link ActivityType#FINISHED} (at the date read, else the date
 * added), {@code currently-reading} is {@link ActivityType#IN_PROGRESS} (at the date added). Both dates are
 * chosen because they do not change between syncs, so the same shelf entry is the same activity.
 * <p>
 * Dedup (ADR 0005): Goodreads is not the canonical catalog of books (Open Library is) and the feed does
 * not carry an Open Library id, so the work is a <em>provisional</em> row keyed by the Goodreads book id
 * ({@code BOOK:GOODREADS:{book_id}}); no fuzzy title matching and no extra request per book. The same
 * book stays one item for every user who syncs it. {@code adult} is false: the feed does not say.
 * <p>
 * Everything read from the feed is untrusted: titles are plain text cut at 300 characters, the cover is
 * accepted only from Goodreads' image hosts, ids must be digits, and a broken item is skipped alone.
 */
public final class GoodreadsProvider extends RssActivityProvider {

    static final URI BASE = URI.create("https://www.goodreads.com");

    /** The only hosts a cover may come from: Goodreads' own image CDN. */
    static final Set<String> IMAGE_HOSTS = Set.of("i.gr-assets.com", "images.gr-assets.com", "s.gr-assets.com");

    static final String PROVIDER = "GOODREADS";
    static final String READ_FEED = "shelf=read&per_page=200";
    static final String READING_FEED = "shelf=currently-reading&per_page=200";

    private static final Pattern PROFILE_ID = Pattern.compile("([0-9]{1,12})(?:-[A-Za-z0-9_-]{1,100})?");
    private static final Pattern BOOK_ID = Pattern.compile("[0-9]{1,12}");
    private static final Pattern YEAR = Pattern.compile("(?<![0-9])([0-9]{4})(?![0-9])");
    private static final int MAX_TITLE = 300;
    private static final int MAX_URL = 500;
    private static final int MIN_YEAR = 1000;
    private static final int MAX_YEAR = 2200;

    private final URI base;

    /** What production uses: the real Goodreads origin. */
    public GoodreadsProvider() {
        this(HttpCatalogProvider.newClient(), BASE, REQUEST_TIMEOUT);
    }

    /**
     * For tests only (a local stub server). Nothing in the application reads this from configuration or
     * from a user: the production bean is built with the constructor above.
     */
    GoodreadsProvider(HttpClient client, URI base, Duration timeout) {
        super(client, timeout);
        this.base = base;
    }

    @Override
    public ProviderId id() {
        return ProviderId.GOODREADS;
    }

    @Override
    public Set<MediaCategory> categories() {
        return Set.of(MediaCategory.BOOK);
    }

    /** The numeric Goodreads user id: 1 to 12 digits (the normalized form, without the {@code -name} suffix). */
    @Override
    public List<ValidationRule<String, UsernameProblem>> usernameRules() {
        return UsernameRules.of(1, 12, "[0-9]+");
    }

    /**
     * Accepts {@code 12345678} or {@code 12345678-name} (as in the profile address) and keeps the digits.
     * Anything else is returned trimmed, for the rules to refuse.
     */
    @Override
    public String normalizeUsername(String raw) {
        String trimmed = super.normalizeUsername(raw);
        Matcher match = PROFILE_ID.matcher(trimmed);
        return match.matches() ? match.group(1) : trimmed;
    }

    @Override
    protected URI baseUri() {
        return base;
    }

    @Override
    protected String feedPath(String encodedUsername) {
        return "/review/list_rss/" + encodedUsername;
    }

    @Override
    protected List<String> feedQueries() {
        return List.of(READ_FEED, READING_FEED);
    }

    @Override
    protected Optional<ActivityEntry> toEntry(Element item, String feedQuery) {
        boolean finished = READ_FEED.equals(feedQuery);
        String bookId = text(item, "book_id");
        String title = text(item, "title");
        if (!BOOK_ID.matcher(bookId).matches() || Long.parseLong(bookId) <= 0 || title.isEmpty()) {
            return Optional.empty();
        }
        Optional<Instant> when = finished
                ? firstDate(text(item, "user_read_at"), text(item, "user_date_added"), text(item, "user_date_created"))
                : firstDate(text(item, "user_date_added"), text(item, "user_date_created"));
        if (when.isEmpty()) {
            return Optional.empty();
        }
        CatalogEntry work = new CatalogEntry(MediaCategory.BOOK, PROVIDER, Long.toString(Long.parseLong(bookId)),
                bounded(title, MAX_TITLE), year(text(item, "book_published")), cover(item), false);
        return Optional.of(new ActivityEntry(work, finished ? ActivityType.FINISHED : ActivityType.IN_PROGRESS,
                when.get()));
    }

    /** The first of these RFC 1123 dates that parses; empty fields and broken ones are passed over. */
    private static Optional<Instant> firstDate(String... candidates) {
        for (String candidate : candidates) {
            try {
                return Optional.of(ZonedDateTime.parse(candidate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (DateTimeParseException notADate) {
                // try the next one
            }
        }
        return Optional.empty();
    }

    private static Integer year(String raw) {
        Matcher found = YEAR.matcher(raw);
        if (found.find()) {
            int year = Integer.parseInt(found.group(1));
            return year >= MIN_YEAR && year <= MAX_YEAR ? year : null;
        }
        return null;
    }

    /** The first usable cover among the sizes the feed offers; none if no one is https on an allowed host. */
    private static String cover(Element item) {
        for (String field : List.of("book_medium_image_url", "book_image_url", "book_large_image_url")) {
            String url = allowedImage(text(item, field));
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static String allowedImage(String raw) {
        if (raw.isEmpty() || raw.length() > MAX_URL || raw.toLowerCase(Locale.ROOT).contains("nophoto")) {
            return null; // Goodreads' grey placeholder is not a cover
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

    /** Text of the first <em>direct</em> child with this local name (a nested {@code <book>} cannot shadow it); empty when absent. */
    private static String text(Element item, String localName) {
        for (Node child = item.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return child.getTextContent().strip();
            }
        }
        return "";
    }

    private static String bounded(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
