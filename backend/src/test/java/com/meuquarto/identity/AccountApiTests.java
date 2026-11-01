package com.meuquarto.identity;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** S14 / FR-02: change password and delete account. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    private static final String NEW_PASSWORD = "lampada azul no telhado";

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
    void changePasswordNeedsTheCurrentOneAndEndsOtherSessions() throws Exception {
        Cookie first = signUp("leandro@example.com", "leandro");
        Cookie second = login("leandro@example.com", PASSWORD);

        post(first, "/api/account/password", "{\"currentPassword\":\"wrong pass\",\"newPassword\":\"%s\"}".formatted(NEW_PASSWORD))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("WRONG_PASSWORD"));
        post(first, "/api/account/password", "{\"currentPassword\":\"%s\",\"newPassword\":\"senha123\"}".formatted(PASSWORD))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PASSWORD_TOO_COMMON"));

        Cookie rotated = post(first, "/api/account/password",
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SESSION");

        Cookie current = rotated != null ? rotated : first;
        mvc.perform(get("/api/auth/me").cookie(current)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(second)).andExpect(status().isUnauthorized());
        login("leandro@example.com", NEW_PASSWORD);
    }

    @Test
    void deleteAccountRemovesUserProfileAndSessions() throws Exception {
        Cookie session = signUp("leandro@example.com", "leandro");
        signUp("other@example.com", "other");

        post(session, "/api/account/delete", "{\"password\":\"wrong pass\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("WRONG_PASSWORD"));
        assertThat(count("select count(*) from users where email = 'leandro@example.com'")).isEqualTo(1);

        post(session, "/api/account/delete", "{\"password\":\"%s\"}".formatted(PASSWORD)).andExpect(status().isNoContent());

        assertThat(count("select count(*) from users where email = 'leandro@example.com'")).isZero();
        assertThat(count("select count(*) from profiles where slug = 'leandro'")).isZero();
        assertThat(count("select count(*) from spring_session where principal_name = 'leandro@example.com'")).isZero();
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        // Other accounts are untouched.
        assertThat(count("select count(*) from profiles where slug = 'other'")).isEqualTo(1);
    }

    @Test
    void deletedAccountStopsBeingServedFromThePublicCacheAtOnce() throws Exception {
        Cookie session = signUp("leandro@example.com", "gone-fast");
        mvc.perform(get("/api/public/profiles/gone-fast")).andExpect(status().isOk());
        mvc.perform(get("/api/public/profiles/gone-fast")).andExpect(status().isOk()); // from the cache

        post(session, "/api/account/delete", "{\"password\":\"%s\"}".formatted(PASSWORD)).andExpect(status().isNoContent());

        mvc.perform(get("/api/public/profiles/gone-fast")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROFILE_NOT_FOUND"));
        mvc.perform(get("/api/public/profiles/gone-fast/items/1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/profiles/gone-fast/collection?category=MOVIE")).andExpect(status().isNotFound());
    }

    @Test
    void anonymousAndCsrfLessRequestsAreRefused() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/account/delete").with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        Cookie session = signUp("leandro@example.com", "leandro");
        mvc.perform(MockMvcRequestBuilders.post("/api/account/delete").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isForbidden());
        assertThat(count("select count(*) from users")).isEqualTo(1);
    }

    @Test
    void passwordGuessesThroughASessionAreThrottled() throws Exception {
        Cookie session = signUp("leandro@example.com", "leandro");
        for (int i = 0; i < 5; i++) {
            post(session, "/api/account/delete", "{\"password\":\"wrong %d\"}".formatted(i)).andExpect(status().isBadRequest());
        }
        post(session, "/api/account/delete", "{\"password\":\"%s\"}".formatted(PASSWORD))
                .andExpect(status().isTooManyRequests());
        assertThat(count("select count(*) from users")).isEqualTo(1);
    }

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/signup").with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions post(Cookie session, String path, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path).cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
