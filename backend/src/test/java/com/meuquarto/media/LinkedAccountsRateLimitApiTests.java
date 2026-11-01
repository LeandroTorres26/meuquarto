package com.meuquarto.media;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * POST /api/linked-accounts has its own per-address limit (ADR 0015) because every link sends a request
 * to a platform; here it is lowered to 2 per hour. STEAM has no provider yet (and Letterboxd would reach the
 * real platform, which no test may do), so each attempt answers 503, which still counts as a hit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "meuquarto.rate-limit.link-account.limit=2",
        "meuquarto.rate-limit.link-account.window=1h"})
class LinkedAccountsRateLimitApiTests {

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
    void thirdLinkInTheWindowIsRefusedWithRetryAfter() throws Exception {
        Cookie session = mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"cavalo bateria grampo\",\"slug\":\"leandro\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");

        for (int i = 0; i < 2; i++) {
            mvc.perform(link(session)).andExpect(status().isServiceUnavailable());
        }
        mvc.perform(link(session)).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    private MockHttpServletRequestBuilder link(Cookie session) throws Exception {
        return post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"STEAM\",\"username\":\"leandro\"}");
    }
}
