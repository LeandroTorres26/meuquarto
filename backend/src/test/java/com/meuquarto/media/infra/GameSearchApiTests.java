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
import java.util.UUID;
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
 * GET /api/catalog/search for GAME through the real endpoint, with the real {@link IgdbCatalog} pointed at a
 * local stub of Twitch and IGDB (the stub constructor is package-private, hence this package). The
 * credentials are random values made for the run. No test reaches Twitch or IGDB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GameSearchApiTests.StubbedCatalogs.class})
class GameSearchApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    private static IgdbStub igdb;

    @TestConfiguration
    static class StubbedCatalogs {

        @Bean
        @Primary
        CatalogRegistry stubbedRegistry() {
            return new CatalogRegistry(List.of(new IgdbCatalog(HttpCatalogProvider.newClient(), Clock.systemUTC(),
                    Duration.ZERO, igdb.apiUrl(), igdb.tokenUrl(), "id" + UUID.randomUUID(), "sec" + UUID.randomUUID(),
                    Duration.ofSeconds(2), Duration.ZERO)));
        }
    }

    @BeforeAll
    static void startStub() {
        igdb = new IgdbStub();
        igdb.gamesBody = CatalogStub.fixture("igdb/search-games.json");
    }

    @AfterAll
    static void stopStub() {
        igdb.close();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        igdb.gamesStatus = 200;
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
        mvc.perform(get("/api/catalog/search").param("category", "GAME").param("q", "witcher"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gamesAreSearchedInIgdbAndAdultOnesAreFilteredOut() throws Exception {
        Cookie session = signedIn();

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "GAME").param("q", "witcher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.results[0].category").value("GAME"))
                .andExpect(jsonPath("$.results[0].provider").value("IGDB"))
                .andExpect(jsonPath("$.results[0].externalId").value("1942"))
                .andExpect(jsonPath("$.results[0].title").value("The Witcher 3: Wild Hunt"))
                .andExpect(jsonPath("$.results[0].year").value(2015))
                .andExpect(jsonPath("$.results[0].imageUrl")
                        .value("https://images.igdb.com/igdb/image/upload/t_cover_big/co1wyy.jpg"))
                .andExpect(jsonPath("$.results[?(@.externalId == '555')]").isEmpty());
    }

    @Test
    void aDownProviderGivesAnEmptyListNotAnError() throws Exception {
        Cookie session = signedIn();
        igdb.gamesStatus = 500;

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "GAME").param("q", "falha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void invalidQueriesAreRejectedBeforeTheProviderIsCalled() throws Exception {
        Cookie session = signedIn();
        int before = igdb.games.size();

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "GAME").param("q", "a"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "GAME").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(igdb.games.size()).isEqualTo(before);
    }
}
