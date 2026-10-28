package com.meuquarto.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The public read routes share one per-address limit (ADR 0015); here it is lowered to 3 per hour. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "meuquarto.rate-limit.public-read.limit=3",
        "meuquarto.rate-limit.public-read.window=1h"})
class PublicProfileRateLimitApiTests {

    @Autowired
    MockMvc mvc;

    @Test
    void fourthPublicReadInTheWindowIsRefusedWhicheverRouteItHits() throws Exception {
        // 404s count too: probing for slugs is exactly what the limit is for
        mvc.perform(get("/api/public/profiles/nobody")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/profiles/nobody/items/1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/profiles/nobody/collection?category=MOVIE")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/profiles/nobody")).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
        // other public routes are not affected by this limit
        mvc.perform(get("/api/slugs/free-slug/availability")).andExpect(status().isOk());
    }
}
