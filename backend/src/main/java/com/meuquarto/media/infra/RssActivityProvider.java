package com.meuquarto.media.infra;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivitySnapshot;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * What Letterboxd and Goodreads share (ADR 0018, hierarchy 1): both publish the activity of a user as
 * an RSS feed. This class is abstract because it knows the algorithm (build a safe URL, download with
 * limits, parse the XML without trusting it, map the items) but not the platform. {@link #fetchActivity}
 * and {@link #checkAccountExists} are the template methods; the abstract steps below are what each
 * leaf fills in: where the feed is and how one {@code <item>} becomes an {@link ActivityEntry}.
 * <p>
 * Safety, since the feed is controlled by a stranger:
 * <ul>
 *   <li>the host is fixed by the leaf ({@link #baseUri}); the username only fills the path, after the
 *       platform's format rules accepted it, and is URL-encoded anyway;</li>
 *   <li>no redirects, connect and request timeouts, and a body larger than {@link #MAX_BODY_BYTES} is dropped;</li>
 *   <li>the XML parser refuses any DOCTYPE, so external entities, entity expansion and external DTDs
 *       cannot happen (XXE); item and text sizes are bounded;</li>
 *   <li>failures become a value or one exception code, and the log names only the provider and the
 *       kind of failure, never the URL.</li>
 * </ul>
 */
public abstract class RssActivityProvider implements ActivityProvider {

    public static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    public static final int MAX_ITEMS = 200;
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(RssActivityProvider.class);

    private final HttpClient client;
    private final Duration requestTimeout;

    protected RssActivityProvider(HttpClient client) {
        this(client, REQUEST_TIMEOUT);
    }

    /** Overload with a shorter timeout, so tests do not wait five seconds. */
    protected RssActivityProvider(HttpClient client, Duration requestTimeout) {
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    // ---- the template methods: the same steps for every RSS platform ----

    @Override
    public final AccountCheck checkAccountExists(String username) {
        try {
            Download download = download(feedUri(username, feedQueries().getFirst()));
            if (download.status() == 404 || download.status() == 410) {
                return AccountCheck.NOT_FOUND;
            }
            if (download.status() != 200) {
                return unavailable("HTTP " + download.status());
            }
            parse(download.body());
            return AccountCheck.EXISTS;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        } catch (IOException | SAXException | ParserConfigurationException | RuntimeException e) {
            return unavailable(e.getClass().getSimpleName());
        }
    }

    @Override
    public final ActivitySnapshot fetchActivity(String username) {
        try {
            List<ActivityEntry> entries = new ArrayList<>();
            for (String query : feedQueries()) {
                Download download = download(feedUri(username, query));
                if (download.status() != 200) {
                    throw failed("HTTP " + download.status());
                }
                Element root = parse(download.body()).getDocumentElement();
                entries.addAll(mapItems(root.getElementsByTagName("item"), query));
            }
            return new ActivitySnapshot(entries);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed("interrupted");
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw failed(e.getClass().getSimpleName());
        } catch (LinkedAccountException e) {
            throw e;
        } catch (RuntimeException e) {
            throw failed(e.getClass().getSimpleName());
        }
    }

    private List<ActivityEntry> mapItems(NodeList items, String feedQuery) {
        List<ActivityEntry> entries = new ArrayList<>();
        int count = Math.min(items.getLength(), MAX_ITEMS);
        for (int i = 0; i < count; i++) {
            try {
                toEntry((Element) items.item(i), feedQuery).ifPresent(entries::add);
            } catch (RuntimeException brokenItem) {
                // One malformed item must not hide the others.
                log.warn("Activity {} skipped an unreadable item ({})", id(), brokenItem.getClass().getSimpleName());
            }
        }
        return entries;
    }

    /**
     * The feed URL: the fixed base of the leaf plus its path for this user. Defense in depth: the
     * username is refused here too if the format rules do not accept it, then URL-encoded, and the
     * result must keep the scheme, host and port of the base (a path can never move the request elsewhere).
     */
    private URI feedUri(String username, String query) {
        if (username == null || validateUsernameFormat(username).isPresent()) {
            throw new IllegalArgumentException("invalid username");
        }
        URI base = baseUri();
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        URI uri = URI.create(base.getScheme() + "://" + base.getRawAuthority() + feedPath(encoded));
        boolean sameOrigin = base.getScheme().equals(uri.getScheme())
                && base.getHost().equalsIgnoreCase(uri.getHost()) && base.getPort() == uri.getPort()
                && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null;
        if (!sameOrigin) {
            throw new IllegalStateException("feed URL left the allowed host");
        }
        return query.isEmpty() ? uri : URI.create(uri + "?" + query);
    }

    private record Download(int status, byte[] body) {}

    private Download download(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "application/rss+xml, application/xml;q=0.9, text/xml;q=0.8")
                .header("User-Agent", "meuquarto/1.0")
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                return new Download(response.statusCode(), new byte[0]);
            }
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IOException("response too large");
            }
            return new Download(200, bytes);
        }
    }

    /** XML without trust: DOCTYPE is refused outright, which closes XXE and entity-expansion attacks. */
    static Document parse(byte[] xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        try (InputStream in = new ByteArrayInputStream(xml)) {
            return factory.newDocumentBuilder().parse(in);
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

    // ---- the steps each platform fills in ----

    /** The fixed origin of the platform, such as {@code https://letterboxd.com}: scheme, host and optional port only. */
    protected abstract URI baseUri();

    /** The path of the feed of one user, starting with {@code /}. The name arrives validated and URL-encoded. */
    protected abstract String feedPath(String encodedUsername);

    /**
     * The fixed query strings (without {@code ?}) of the feeds to read, one request each. Default: one feed
     * without a query (Letterboxd). A platform with one feed per shelf (Goodreads) lists them here. These
     * are constants of the leaf, never built from user input, so the user still only fills the path.
     */
    protected List<String> feedQueries() {
        return List.of("");
    }

    /**
     * Turns one {@code <item>} of the feed into an entry, or empty to skip it (for example a list that
     * is not a film). {@code feedQuery} is the query of the feed the item came from (see
     * {@link #feedQueries}), so a leaf can tell the shelves apart; leaves with one feed ignore it. May
     * throw; the template skips that one item. Text from the feed is untrusted: take only what is needed
     * and bound it.
     */
    protected abstract Optional<ActivityEntry> toEntry(Element item, String feedQuery);
}
