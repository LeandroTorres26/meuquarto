package com.meuquarto.media;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** The real wiring with no TMDB token (the default until the owner has a key), plus the per-IP limit. */
@SpringBootTest(properties = {"meuquarto.catalog.tmdb.token=", "meuquarto.rate-limit.catalog-search.limit=3"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogWithoutKeyApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    @Test
    void withoutATmdbKeySearchAnswersEmptyAndUnavailableThenTheRateLimitApplies() throws Exception {
        Cookie session = mvc.perform(MockMvcRequestBuilders.post("/api/auth/signup").with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"cavalo bateria grampo\",\"slug\":\"leandro\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");

        for (String category : new String[] {"MOVIE", "SERIES"}) {
            mvc.perform(get("/api/catalog/search").cookie(session).param("category", category).param("q", "matrix"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.results.length()").value(0));
        }
        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MOVIE").param("q", "matrix"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/catalog/search").cookie(session).param("category", "MOVIE").param("q", "matrix"))
                .andExpect(status().isTooManyRequests());
    }
}
