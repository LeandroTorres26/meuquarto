package com.meuquarto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.shared.ratelimit.FixedWindowLimiter;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.RecordingMailer;
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
import org.springframework.test.web.servlet.ResultActions;

/** Password reset limits (ADR 0015/0017): per address on both endpoints, and per e-mail without a visible signal. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingMailer.Config.class})
@TestPropertySource(properties = {
        "meuquarto.rate-limit.password-reset.limit=3",
        "meuquarto.rate-limit.password-reset.window=1h",
        "meuquarto.rate-limit.password-reset-confirm.limit=2",
        "meuquarto.rate-limit.password-reset-confirm.window=1h",
        "meuquarto.rate-limit.password-reset-email.limit=1",
        "meuquarto.rate-limit.password-reset-email.window=1h"})
class PasswordResetRateLimitApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecordingMailer mailer;

    @Autowired
    FixedWindowLimiter limiter;

    @BeforeEach
    void reset() {
        limiter.reset();
        mailer.clear();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    @Test
    void requestsFromOneAddressAreLimitedWithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            request("nobody" + i + "@example.com").andExpect(status().isAccepted());
        }
        request("nobody9@example.com").andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void confirmationsFromOneAddressAreLimited() throws Exception {
        for (int i = 0; i < 2; i++) {
            confirm().andExpect(status().isBadRequest());
        }
        confirm().andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void theSameEmailGetsOneLinkPerWindowAndTheAnswerDoesNotChange() throws Exception {
        signUp("leandro@example.com");

        request("leandro@example.com").andExpect(status().isAccepted());
        request("leandro@example.com").andExpect(status().isAccepted()); // dropped silently: same answer

        assertThat(mailer.sent()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens", Integer.class)).isEqualTo(1);
    }

    private ResultActions request(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions confirm() throws Exception {
        return mvc.perform(post("/api/auth/password-reset/confirm").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"lampada azul no telhado\"}".formatted("A".repeat(43))));
    }

    private void signUp(String email) throws Exception {
        mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"cavalo bateria grampo\",\"slug\":\"leandro\"}".formatted(email)))
                .andExpect(status().isCreated());
    }
}
