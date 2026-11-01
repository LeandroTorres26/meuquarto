package com.meuquarto.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** POST /api/collection has its own per-address limit (ADR 0015); here it is lowered to 2 per hour. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "meuquarto.rate-limit.collection-add.limit=2",
        "meuquarto.rate-limit.collection-add.window=1h"})
class CollectionRateLimitApiTests {

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
    void thirdAddInTheWindowIsRefusedWithRetryAfter() throws Exception {
        Cookie session = mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"cavalo bateria grampo\",\"slug\":\"leandro\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");

        for (int i = 0; i < 2; i++) {
            // Not a 429: the catalog has no key in tests, so the add itself answers 503, which still counts as a hit.
            mvc.perform(add(session)).andExpect(status().isServiceUnavailable());
        }
        mvc.perform(add(session)).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder add(Cookie session) throws Exception {
        return post("/api/collection").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"MOVIE\",\"externalId\":\"603\"}");
    }
}
