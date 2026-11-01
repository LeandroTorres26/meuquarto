package com.meuquarto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.identity.domain.ResetToken;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.RecordingMailer;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** FR-01a / ADR 0012 end to end: request a reset link, spend it once, sessions and other links end. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingMailer.Config.class})
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    private static final String NEW_PASSWORD = "lampada azul no telhado";
    private static final String EMAIL = "leandro@example.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecordingMailer mailer;

    @BeforeEach
    void clearMailbox() {
        mailer.clear();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users"); // cascades to password_reset_tokens
        jdbc.update("delete from spring_session");
    }

    // --- request ---

    @Test
    void knownAndUnknownEmailsGetTheSameAnswer() throws Exception {
        signUp(EMAIL, "leandro");

        String known = requestReset(EMAIL).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = requestReset("nobody@example.com").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String malformed = requestReset("not an email").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown).isEqualTo(malformed).isEmpty();
        assertThat(mailer.sent()).hasSize(1);
        assertThat(mailer.sent().get(0).to()).isEqualTo(EMAIL);
        assertThat(count("select count(*) from password_reset_tokens")).isEqualTo(1);
    }

    @Test
    void theLinkPointsAtTheConfiguredSiteAndOnlyTheHashIsStored() throws Exception {
        signUp(EMAIL, "leandro");

        requestReset(EMAIL).andExpect(status().isAccepted());

        String link = mailer.sent().get(0).link();
        assertThat(link).startsWith("http://localhost:3000/redefinir-senha?token=");
        String token = mailer.lastTokenFor(EMAIL);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        String stored = jdbc.queryForObject("select token_hash from password_reset_tokens", String.class);
        assertThat(stored).isEqualTo(ResetToken.parse(token).hash()).isNotEqualTo(token);
        assertThat(count("select count(*) from password_reset_tokens where token_hash like '%" + token + "%'")).isZero();
    }

    @Test
    void theRequestIgnoresAForgedHostHeader() throws Exception {
        signUp(EMAIL, "leandro");

        mvc.perform(post("/api/auth/password-reset").with(RealCsrf.token(mvc)).header("Host", "evil.example")
                        .header("X-Forwarded-Host", "evil.example").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(EMAIL)))
                .andExpect(status().isAccepted());

        assertThat(mailer.sent().get(0).link()).startsWith("http://localhost:3000/").doesNotContain("evil");
    }

    @Test
    void theRequestNeedsTheCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(EMAIL)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted("A".repeat(43), NEW_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anOversizedOrMissingEmailIsABadRequest() throws Exception {
        mvc.perform(post("/api/auth/password-reset").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        mvc.perform(post("/api/auth/password-reset").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted("a".repeat(300))))
                .andExpect(status().isBadRequest());
    }

    // --- confirm ---

    @Test
    void confirmingSetsThePasswordAndEndsEverySession() throws Exception {
        Cookie first = signUp(EMAIL, "leandro");
        Cookie second = login(EMAIL, PASSWORD);
        Cookie bystander = signUp("other@example.com", "other");
        requestReset(EMAIL);
        String token = mailer.lastTokenFor(EMAIL);

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(second)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(bystander)).andExpect(status().isOk()); // other accounts untouched
        loginAttempt(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        login(EMAIL, NEW_PASSWORD);
    }

    @Test
    void aTokenWorksOnlyOnce() throws Exception {
        signUp(EMAIL, "leandro");
        requestReset(EMAIL);
        String token = mailer.lastTokenFor(EMAIL);

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
        confirm(token, "outra senha bem longa").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RESET_TOKEN"));

        login(EMAIL, NEW_PASSWORD); // the second attempt changed nothing
    }

    @Test
    void confirmingSpendsEveryOtherPendingToken() throws Exception {
        signUp(EMAIL, "leandro");
        requestReset(EMAIL);
        String older = mailer.lastTokenFor(EMAIL);
        requestReset(EMAIL);
        String newer = mailer.lastTokenFor(EMAIL);
        assertThat(older).isNotEqualTo(newer);

        confirm(newer, NEW_PASSWORD).andExpect(status().isNoContent());

        confirm(older, "outra senha bem longa").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        signUp(EMAIL, "leandro");
        requestReset(EMAIL);
        String token = mailer.lastTokenFor(EMAIL);
        jdbc.update("update password_reset_tokens set expires_at = now() - interval '1 second'");

        confirm(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RESET_TOKEN"));
        loginAttempt(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theLinkLastsThirtyMinutes() throws Exception {
        signUp(EMAIL, "leandro");
        requestReset(EMAIL);

        long minutes = jdbc.queryForObject(
                "select extract(epoch from (expires_at - created_at)) / 60 from password_reset_tokens", Long.class);
        assertThat(minutes).isEqualTo(30);
    }

    @Test
    void tamperedUnknownAndMalformedTokensAreAllRefusedTheSameWay() throws Exception {
        signUp(EMAIL, "leandro");
        requestReset(EMAIL);
        String token = mailer.lastTokenFor(EMAIL);
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        String expected = confirm(token.toLowerCase().equals(token) ? token + "x" : token.toUpperCase(), NEW_PASSWORD)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(expected).isEqualTo("{\"error\":\"INVALID_RESET_TOKEN\"}");
        for (String bad : new String[] {tampered, "A".repeat(43), "", "short", "../../etc/passwd", token + token}) {
            String body = confirm(bad, NEW_PASSWORD).andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).isEqualTo(expected);
        }
        // The real token still works: none of the failures spent it.
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aWeakNewPasswordIsRefusedWithoutSpendingTheLink() throws Exception {
        signUp(EMAIL, "leandro");
        requestReset(EMAIL);
        String token = mailer.lastTokenFor(EMAIL);

        confirm(token, "senha123").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PASSWORD_TOO_COMMON"));
        confirm(token, "curta").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PASSWORD_TOO_SHORT"));
        confirm(token, EMAIL).andExpect(status().isBadRequest());

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
        login(EMAIL, NEW_PASSWORD);
    }

    @Test
    void aSpentTokenOfOneUserDoesNothingToAnother() throws Exception {
        signUp(EMAIL, "leandro");
        signUp("other@example.com", "other");
        requestReset(EMAIL);
        String token = mailer.lastTokenFor(EMAIL);

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login("other@example.com", PASSWORD); // its password is unchanged
    }

    @Test
    void theTokenNeverReachesTheLogs(CapturedOutput output) throws Exception {
        signUp(EMAIL, "leandro");
        requestReset(EMAIL);
        String token = mailer.lastTokenFor(EMAIL);
        confirm("A".repeat(43), NEW_PASSWORD);
        confirm(token, "senha123");
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
        confirm(token, NEW_PASSWORD).andExpect(status().isBadRequest());

        assertThat(output.getAll()).doesNotContain(token).doesNotContain(ResetToken.parse(token).hash());
    }

    // --- helpers ---

    private ResultActions requestReset(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions confirm(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/confirm").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password)));
    }

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions loginAttempt(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private Cookie login(String email, String password) throws Exception {
        return loginAttempt(email, password).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
