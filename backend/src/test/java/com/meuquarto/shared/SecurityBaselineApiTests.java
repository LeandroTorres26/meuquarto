package com.meuquarto.shared;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.shared.ratelimit.FixedWindowLimiter;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/** Issue 52: security headers, no CORS, and per-IP rate limits answered with 429 and Retry-After. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "meuquarto.rate-limit.signup.limit=3",
    "meuquarto.rate-limit.login.limit=3",
    "meuquarto.rate-limit.slug-availability.limit=5",
    "meuquarto.rate-limit.anonymous.limit=25",
})
class SecurityBaselineApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    FixedWindowLimiter limiter;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetLimiter() {
        limiter.reset();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
        limiter.reset();
    }

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    @Test
    void errorResponsesCarryHeaders() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void crossOriginPreflightIsNotAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Methods"));

        mvc.perform(get("/api/health").header("Origin", "https://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void signUpIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(signUp("user" + i + "@example.com", "user" + i)).andExpect(status().isCreated());
        }
        mvc.perform(signUp("user9@example.com", "user9"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void loginIsLimitedPerIpEvenAcrossDifferentAccounts() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(login("nobody" + i + "@example.com")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("nobody9@example.com"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9]\\d*")))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void slugAvailabilityIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/slugs/{slug}/availability", "free" + i)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/slugs/{slug}/availability", "free9"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void anotherAddressIsNotAffectedByTheLimit() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(get("/api/slugs/{slug}/availability", "free" + i));
        }
        mvc.perform(get("/api/slugs/{slug}/availability", "free").with(request -> {
                    request.setRemoteAddr("203.0.113.9");
                    return request;
                }))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousTrafficHasAnOverallCeiling() throws Exception {
        for (int i = 0; i < 25; i++) {
            mvc.perform(get("/api/health")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/health"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void trafficUnderTheLimitsWorksNormally() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/slugs/{slug}/availability", "leandro")).andExpect(status().isOk());
        mvc.perform(login("nobody@example.com")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder signUp(String email, String slug) throws Exception {
        return post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"cavalo bateria grampo\",\"slug\":\"%s\"}".formatted(email, slug));
    }

    private MockHttpServletRequestBuilder login(String email) throws Exception {
        return post("/api/auth/login").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"wrong password here\"}".formatted(email));
    }
}
