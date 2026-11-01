package com.meuquarto.media;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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

/** GET /api/catalog/search with a fake catalog behind the registry: no test reaches a real provider. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CatalogApiTests.FakeCatalogs.class})
class CatalogApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";

    /** MOVIE: available, returns one clean and one adult entry. SERIES: served but unavailable. */
    private record FakeCatalog(MediaCategory served, boolean available) implements CatalogProvider {

        @Override
        public String catalogName() {
            return "FAKE";
        }

        @Override
        public boolean serves(MediaCategory category) {
            return category == served;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public List<CatalogEntry> search(MediaCategory category, String query) {
            return List.of(
                    new CatalogEntry(category, "FAKE", "7", "Result for " + query, 2020, "https://img.example/7.jpg", false),
                    new CatalogEntry(category, "FAKE", "8", "Adult", 2020, null, true));
        }

        @Override
        public Optional<CatalogEntry> fetch(MediaCategory category, String externalId) {
            return Optional.empty();
        }
    }

    @TestConfiguration
    static class FakeCatalogs {

        @Bean
        @Primary
        CatalogRegistry fakeRegistry() {
            return new CatalogRegistry(List.of(
                    new FakeCatalog(MediaCategory.MOVIE, true), new FakeCatalog(MediaCategory.SERIES, false)));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
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
        mvc.perform(get("/api/catalog/search").param("category", "MOVIE").param("q", "matrix"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUserGetsNormalizedResultsWithoutAdultTitles() throws Exception {
        Cookie session = signedIn();

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MOVIE").param("q", "  matrix "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].category").value("MOVIE"))
                .andExpect(jsonPath("$.results[0].provider").value("FAKE"))
                .andExpect(jsonPath("$.results[0].externalId").value("7"))
                .andExpect(jsonPath("$.results[0].title").value("Result for matrix"))
                .andExpect(jsonPath("$.results[0].year").value(2020))
                .andExpect(jsonPath("$.results[0].imageUrl").value("https://img.example/7.jpg"));
    }

    @Test
    void unavailableOrUnservedCategoriesAnswerEmptyWithTheFlagOff() throws Exception {
        Cookie session = signedIn();

        for (String category : List.of("SERIES", "ANIME")) {
            mvc.perform(get("/api/catalog/search").cookie(session).param("category", category).param("q", "matrix"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.results.length()").value(0));
        }
    }

    @Test
    void invalidParametersAreBadRequests() throws Exception {
        Cookie session = signedIn();

        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MOVIE").param("q", " a "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_QUERY"));
        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MOVIE").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "PODCAST").param("q", "matrix"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/search").cookie(session).param("q", "matrix"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MOVIE"))
                .andExpect(status().isBadRequest());
    }
}
