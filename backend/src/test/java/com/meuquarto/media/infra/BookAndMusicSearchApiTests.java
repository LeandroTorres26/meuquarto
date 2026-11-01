package com.meuquarto.media.infra;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * GET /api/catalog/search for BOOK and MUSIC through the real endpoint, with the two real leaf classes
 * pointed at local stubs (the stub constructors are package-private, hence this package). No test reaches
 * Open Library or Deezer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, BookAndMusicSearchApiTests.StubbedCatalogs.class})
class BookAndMusicSearchApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    private static CatalogStub openLibrary;
    private static CatalogStub deezer;

    @TestConfiguration
    static class StubbedCatalogs {

        @Bean
        @Primary
        CatalogRegistry stubbedRegistry() {
            Duration timeout = Duration.ofSeconds(2);
            return new CatalogRegistry(List.of(
                    new OpenLibraryCatalog(HttpCatalogProvider.newClient(), Clock.systemUTC(), Duration.ZERO,
                            openLibrary.baseUrl(), timeout),
                    new DeezerCatalog(HttpCatalogProvider.newClient(), Clock.systemUTC(), Duration.ZERO,
                            deezer.baseUrl(), timeout)));
        }
    }

    @BeforeAll
    static void startStubs() {
        openLibrary = new CatalogStub();
        openLibrary.body = CatalogStub.fixture("openlibrary/search.json");
        deezer = new CatalogStub();
        deezer.body = CatalogStub.fixture("deezer/search-album.json");
    }

    @AfterAll
    static void stopStubs() {
        openLibrary.close();
        deezer.close();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        openLibrary.status = 200;
        deezer.status = 200;
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    private Cookie signedIn() throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/signup").with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"%s\",\"slug\":\"leandro\"}".formatted(PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    @Test
    void anonymousVisitorsAreRejected() throws Exception {
        mvc.perform(get("/api/catalog/search").param("category", "BOOK").param("q", "fox"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/catalog/search").param("category", "MUSIC").param("q", "discovery"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void booksAreSearchedInOpenLibrary() throws Exception {
        Cookie session = signedIn();

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "BOOK").param("q", "fantastic mr fox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].category").value("BOOK"))
                .andExpect(jsonPath("$.results[0].provider").value("OPENLIBRARY"))
                .andExpect(jsonPath("$.results[0].externalId").value("OL45804W"))
                .andExpect(jsonPath("$.results[0].title").value("Fantastic Mr Fox"))
                .andExpect(jsonPath("$.results[0].year").value(1970))
                .andExpect(jsonPath("$.results[0].imageUrl").value("https://covers.openlibrary.org/b/id/8739161-M.jpg"));
    }

    @Test
    void musicIsSearchedInDeezer() throws Exception {
        Cookie session = signedIn();

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MUSIC").param("q", "discovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.results[0].category").value("MUSIC"))
                .andExpect(jsonPath("$.results[0].provider").value("DEEZER"))
                .andExpect(jsonPath("$.results[0].externalId").value("302127"))
                .andExpect(jsonPath("$.results[0].title").value("Discovery - Daft Punk"))
                .andExpect(jsonPath("$.results[0].imageUrl").exists());
    }

    @Test
    void aProviderThatIsDownGivesAnEmptyListAndTheOtherOneKeepsWorking() throws Exception {
        Cookie session = signedIn();
        openLibrary.status = 500;

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "BOOK").param("q", "falha")).andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MUSIC").param("q", "segue")).andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(4));
    }

    @Test
    void invalidQueriesAreRejectedBeforeAnyProviderIsCalled() throws Exception {
        Cookie session = signedIn();
        int before = openLibrary.requests.size() + deezer.requests.size();

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "BOOK").param("q", "a"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MUSIC").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(openLibrary.requests.size() + deezer.requests.size()).isEqualTo(before);
    }
}
