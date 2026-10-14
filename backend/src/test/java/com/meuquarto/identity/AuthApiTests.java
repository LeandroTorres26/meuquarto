package com.meuquarto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** FR-01 and ADR 0012 end to end: sign-up, sign-in, session, CSRF, throttling, sign-out. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";

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
    void signUpCreatesUserAndProfileAndSignsIn() throws Exception {
        Cookie session = signUp("Leandro@Example.com", PASSWORD, "leandro")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("leandro@example.com"))
                .andExpect(jsonPath("$.slug").value("leandro"))
                .andExpect(cookie().httpOnly("SESSION", true))
                .andExpect(cookie().secure("SESSION", true))
                .andExpect(cookie().sameSite("SESSION", "Lax"))
                .andReturn().getResponse().getCookie("SESSION");

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("leandro"));

        String hash = jdbc.queryForObject("select password_hash from users", String.class);
        assertThat(hash).startsWith("{argon2}$argon2id$").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isEqualTo(1);
    }

    @Test
    void signUpRulesAreEnforcedAndNothingIsLeftBehind() throws Exception {
        signUp("leandro@example.com", PASSWORD, "leandro").andExpect(status().isCreated());

        signUp("LEANDRO@example.com", PASSWORD, "outro").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
        signUp("maria@example.com", PASSWORD, "leandro").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLUG_TAKEN"));
        signUp("maria@example.com", PASSWORD, "painel").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SLUG_RESERVED"));
        signUp("maria@example.com", "senha123", "maria").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PASSWORD_TOO_COMMON"));
        signUp("not-an-email", PASSWORD, "maria").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_EMAIL"));

        // The failed sign-up with a taken slug must not leave a user without a profile.
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    }

    @Test
    void signInWorksAndRotatesTheSession() throws Exception {
        signUp("leandro@example.com", PASSWORD, "leandro");
        Cookie before = new Cookie("SESSION", "attacker-chosen");

        Cookie after = mvc.perform(loginRequest("leandro@example.com", PASSWORD).cookie(before))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(after).isNotNull();
        assertThat(after.getValue()).isNotEqualTo("attacker-chosen");
        mvc.perform(get("/api/auth/me").cookie(after)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        signUp("leandro@example.com", PASSWORD, "leandro");

        mvc.perform(loginRequest("leandro@example.com", "wrong password"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        mvc.perform(loginRequest("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void repeatedFailuresAreThrottled() throws Exception {
        signUp("throttle@example.com", PASSWORD, "throttle");
        for (int i = 0; i < 5; i++) {
            mvc.perform(loginRequest("throttle@example.com", "wrong " + i)).andExpect(status().isUnauthorized());
        }
        mvc.perform(loginRequest("throttle@example.com", PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void postsWithoutCsrfTokenAreRefused() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointHandsOutTheCookieForTheWebApp() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void signOutEndsTheSession() throws Exception {
        Cookie session = signUp("leandro@example.com", PASSWORD, "leandro")
                .andReturn().getResponse().getCookie("SESSION");

        mvc.perform(post("/api/auth/logout").cookie(session).with(RealCsrf.token(mvc))).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousHasNoCurrentUser() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRequestsToProtectedRoutesOpenNoSession() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist("SESSION"));
        mvc.perform(get("/api/anything")).andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist("SESSION"));
        assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions signUp(String email, String password, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, password, slug)));
    }

    private MockHttpServletRequestBuilder loginRequest(String email, String password) throws Exception {
        return post("/api/auth/login").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

}
