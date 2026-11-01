package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.shared.domain.MediaCategory;
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
import java.util.List;
import java.util.Optional;
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
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The real {@link LetterboxdProvider} through the whole stack against a real PostgreSQL: link an account,
 * run a manual sync and check the collection (BR-04, BR-10). Only the origin is replaced, by a local stub
 * server that serves the fixture; the stub is a test-only constructor, never configuration. This class
 * sits in the {@code infra} package to reach that constructor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LetterboxdSyncTests.Stubs.class})
class LetterboxdSyncTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/letterboxd/diary.xml");
    private static final HttpServer STUB = startStub();
    private static volatile int stubStatus = 200;

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try {
                    boolean ok = stubStatus == 200 && "/ana_l/rss/".equals(exchange.getRequestURI().getPath());
                    if (ok) {
                        byte[] body = Files.readAllBytes(FIXTURE);
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(body);
                        }
                    } else {
                        exchange.sendResponseHeaders(stubStatus == 200 ? 404 : stubStatus, -1);
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

    /** A TMDB that knows film 603 under its Portuguese title, as the manual search would. */
    private static final class FakeTmdb implements CatalogProvider {

        @Override
        public String catalogName() {
            return "TMDB";
        }

        @Override
        public boolean serves(MediaCategory category) {
            return category == MediaCategory.MOVIE;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public List<CatalogEntry> search(MediaCategory category, String query) {
            return List.of();
        }

        @Override
        public Optional<CatalogEntry> fetch(MediaCategory category, String externalId) {
            return "603".equals(externalId)
                    ? Optional.of(new CatalogEntry(category, "TMDB", "603", "Matrix", 1999, null, false))
                    : Optional.empty();
        }
    }

    @TestConfiguration
    static class Stubs {

        /** First in the registry, ahead of the production bean that points at the real host. */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ActivityProvider stubbedLetterboxd() {
            URI origin = URI.create("http://127.0.0.1:" + STUB.getAddress().getPort());
            return new LetterboxdProvider(HttpCatalogProvider.newClient(), origin, Duration.ofSeconds(2));
        }

        @Bean
        @Primary
        CatalogRegistry fakeCatalogs() {
            return new CatalogRegistry(List.of(new FakeTmdb()));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void stubWorks() {
        stubStatus = 200;
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
    void aManualSyncFillsTheCollectionFromTheDiaryWithTheTmdbCanonicalKey() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "Ana_L"); // normalized to lowercase on the way in

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.itemsCount").value(5)); // five diary entries mapped; list, series, no-id skipped

        assertThat(jdbc.queryForObject("select username from linked_accounts", String.class)).isEqualTo("ana_l");
        assertThat(jdbc.queryForList("select source from collection_item_sources", String.class))
                .hasSize(4).containsOnly("LETTERBOXD"); // four distinct films
        assertThat(jdbc.queryForList(
                "select category || ':' || canonical_provider || ':' || external_id from media_items order by external_id",
                String.class)).containsExactly("MOVIE:TMDB:348", "MOVIE:TMDB:496243", "MOVIE:TMDB:603", "MOVIE:TMDB:777");
        assertThat(count("synced_entries")).isEqualTo(5); // the rewatch of The Matrix is its own activity
        assertThat(jdbc.queryForObject("select count(*) from synced_entries where activity_type = 'FINISHED'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("select title from media_items where external_id = '777'", String.class))
                .isEqualTo("<script>alert(1)</script><b>Bold</b>"); // plain text; the UI escapes
        assertThat(jdbc.queryForObject("select image_url from media_items where external_id = '603'", String.class))
                .startsWith("https://a.ltrbxd.com/");
        assertThat(jdbc.queryForObject("select image_url from media_items where external_id = '496243'", String.class))
                .isNull();
    }

    @Test
    void br04_aFilmAddedByHandFromTmdbAfterTheSyncIsTheSameItem() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        sync(session, account).andExpect(status().isOk());
        int itemsBefore = count("collection_items");
        int mediaBefore = count("media_items");

        mvc.perform(post("/api/collection").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MOVIE\",\"externalId\":\"603\"}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(count("collection_items")).isEqualTo(itemsBefore);
        assertThat(count("media_items")).isEqualTo(mediaBefore);
        assertThat(jdbc.queryForList("""
                select s.source from collection_item_sources s join collection_items i on i.id = s.collection_item_id
                join media_items m on m.id = i.media_item_id where m.external_id = '603' order by s.source""", String.class))
                .contains("LETTERBOXD");
    }

    @Test
    void br04_aFilmAddedByHandBeforeTheSyncIsNotDuplicatedBySyncing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        mvc.perform(post("/api/collection").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MOVIE\",\"externalId\":\"603\"}"))
                .andExpect(status().is2xxSuccessful());
        long account = link(session, "ana_l");

        sync(session, account).andExpect(status().isOk());

        assertThat(count("collection_items")).isEqualTo(4); // 603 once, plus 348, 496243, 777
        assertThat(jdbc.queryForObject("select count(*) from media_items where external_id = '603'", Integer.class)).isEqualTo(1);
    }

    @Test
    void syncingAgainChangesNothing() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        sync(session, account).andExpect(status().isOk());
        jdbc.update("update linked_accounts set last_sync_started_at = now() - interval '1 hour'");

        sync(session, account).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(count("collection_items")).isEqualTo(4);
        assertThat(count("synced_entries")).isEqualTo(5);
    }

    @Test
    void whenLetterboxdIsDownTheSyncFailsForThatAccountOnly() throws Exception {
        Cookie session = signUp("a@example.com", "ana");
        long account = link(session, "ana_l");
        stubStatus = 503;

        sync(session, account).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(count("collection_items")).isZero();
    }

    @Test
    void linkingAnAccountThatDoesNotExistIsRefused() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"LETTERBOXD\",\"username\":\"nobody_here\"}"))
                .andExpect(status().isBadRequest());
        assertThat(count("linked_accounts")).isZero();
    }

    @Test
    void aMaliciousUsernameIsRefusedBeforeAnyRequestToLetterboxd() throws Exception {
        Cookie session = signUp("a@example.com", "ana");

        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"LETTERBOXD\",\"username\":\"../../etc/passwd\"}"))
                .andExpect(status().isBadRequest());
        assertThat(count("linked_accounts")).isZero();
    }

    // --- helpers ---

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private long link(Cookie session, String username) throws Exception {
        String body = mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"LETTERBOXD\",\"username\":\"%s\"}".formatted(username)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions sync(Cookie session, long accountId) throws Exception {
        return mvc.perform(post("/api/linked-accounts/" + accountId + "/sync").cookie(session).with(RealCsrf.token(mvc)));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
