package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The real {@link GoodreadsProvider} through the whole stack against a real PostgreSQL: link an account
 * by Goodreads id, run a manual sync and check the collection (BR-04, BR-10). Only the origin is replaced,
 * by a local stub server that serves the fixtures; the stub is a test-only constructor, never configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GoodreadsSyncTests.Stubs.class})
class GoodreadsSyncTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    private static final Path READ = Path.of("src/test/resources/fixtures/goodreads/read.xml");
    private static final Path READING = Path.of("src/test/resources/fixtures/goodreads/currently-reading.xml");
    private static final HttpServer STUB = startStub();
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();

    /** Ids the stub treats as down (503); every other id except 12345678 does not exist (404). */
    private static final String DOWN_ID = "55555555";

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    String query = String.valueOf(exchange.getRequestURI().getRawQuery());
                    REQUESTS.add(path + "?" + query);
                    if (("/review/list_rss/" + DOWN_ID).equals(path)) {
                        exchange.sendResponseHeaders(503, -1);
                    } else if ("/review/list_rss/12345678".equals(path)) {
                        byte[] body = Files.readAllBytes(query.startsWith("shelf=read") ? READ : READING);
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(body);
                        }
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestConfiguration
    static class Stubs {

        /** First in the registry, ahead of the production bean that points at the real host. */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ActivityProvider stubbedGoodreads() {
            URI origin = URI.create("http://127.0.0.1:" + STUB.getAddress().getPort());
            return new GoodreadsProvider(HttpCatalogProvider.newClient(), origin, Duration.ofSeconds(2));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clearRequests() {
        REQUESTS.clear();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @Test
    void aManualSyncFillsTheCollectionWithProvisionalGoodreadsBooks() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "12345678-ana"); // the profile address suffix is dropped on the way in
        REQUESTS.clear(); // linking asked the read shelf once, to check that the account exists

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.itemsCount").value(5));

        assertThat(jdbc.queryForObject("select username from linked_accounts", String.class)).isEqualTo("12345678");
        assertThat(jdbc.queryForList(
                "select category || ':' || canonical_provider || ':' || external_id from media_items order by external_id",
                String.class)).containsExactly("BOOK:GOODREADS:22328", "BOOK:GOODREADS:234225", "BOOK:GOODREADS:5907",
                "BOOK:GOODREADS:777");
        assertThat(jdbc.queryForList("select source from collection_item_sources", String.class))
                .hasSize(4).containsOnly("GOODREADS");
        assertThat(jdbc.queryForObject("select count(*) from synced_entries where activity_type = 'FINISHED'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from synced_entries where activity_type = 'IN_PROGRESS'", Integer.class))
                .isEqualTo(2); // Neuromancer, and Dune again (a re-read)
        assertThat(jdbc.queryForObject("select title from media_items where external_id = '777'", String.class))
                .isEqualTo("<script>alert(1)</script><b>Bold</b>"); // plain text; the UI escapes
        assertThat(jdbc.queryForObject("select image_url from media_items where external_id = '234225'", String.class))
                .startsWith("https://i.gr-assets.com/");
        assertThat(jdbc.queryForObject("select image_url from media_items where external_id = '777'", String.class)).isNull();
        assertThat(jdbc.queryForObject("select adult from media_items where external_id = '5907'", Boolean.class)).isFalse();
        assertThat(REQUESTS).containsExactly("/review/list_rss/12345678?shelf=read&per_page=200",
                "/review/list_rss/12345678?shelf=currently-reading&per_page=200");
    }

    @Test
    void syncingAgainChangesNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "12345678");
        sync(session, account).andExpect(status().isOk());
        int items = count("collection_items");
        int entries = count("synced_entries");
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '1 hour'");

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(count("collection_items")).isEqualTo(items);
        assertThat(count("synced_entries")).isEqualTo(entries);
    }

    @Test
    void theSyncCooldownAppliesToGoodreadsToo() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "12345678");
        sync(session, account).andExpect(status().isOk());

        sync(session, account).andExpect(status().isTooManyRequests());
    }

    @Test
    void whenGoodreadsIsDownTheSyncFailsForThatAccountOnlyAndTheOtherSyncs() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long good = link(ana, "12345678");
        // The stub answers the account check with 404 for ids it does not know, so the failing account is seeded directly.
        long profileId = jdbc.queryForObject("select id from profiles where slug = 'bia'", Long.class);
        jdbc.update("insert into linked_accounts (profile_id, provider, username) values (?, 'GOODREADS', ?)", profileId, DOWN_ID);
        long down = jdbc.queryForObject("select id from linked_accounts where username = ?", Long.class, DOWN_ID);

        sync(bia, down).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));
        sync(ana, good).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(count("collection_items")).isEqualTo(4); // only ana's books
    }

    @Test
    void linkingAnIdThatDoesNotExistIsRefused() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        link(session, "99999999", status().isBadRequest());

        assertThat(count("linked_accounts")).isZero();
    }

    @Test
    void aMaliciousIdIsRefusedBeforeAnyRequestToGoodreads() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        link(session, "../../etc/passwd", status().isBadRequest());
        link(session, "12345678?shelf=x", status().isBadRequest());
        link(session, "ana", status().isBadRequest());

        assertThat(count("linked_accounts")).isZero();
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void anonymousAndOtherUsersCannotLinkOrSyncSomeoneElsesGoodreadsAccount() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long account = link(ana, "12345678");

        mvc.perform(post("/api/linked-accounts").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GOODREADS\",\"username\":\"12345678\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/linked-accounts/" + account + "/sync").with(RealCsrf.token(mvc)))
                .andExpect(status().isUnauthorized());
        sync(bia, account).andExpect(status().isNotFound());
        assertThat(count("collection_items")).isZero();
    }

    // --- helpers ---

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private long link(Cookie session, String username) throws Exception {
        String body = link(session, username, status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions link(Cookie session, String username,
            org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        return mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GOODREADS\",\"username\":\"%s\"}".formatted(username)))
                .andExpect(expected);
    }

    private ResultActions sync(Cookie session, long accountId) throws Exception {
        return mvc.perform(post("/api/linked-accounts/" + accountId + "/sync").cookie(session).with(RealCsrf.token(mvc)));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
