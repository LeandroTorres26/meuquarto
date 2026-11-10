package com.meuquarto.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.StorageTestcontainersConfiguration;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
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

/** Themes in the editor (FR-05, ADR 0022): the four are free, anyone applies any, an unknown id is refused. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, StorageTestcontainersConfiguration.class})
class ThemeApiTests {

    // Random per run: a fixed password in the source would trip the secret scanner.
    private static final String PASSWORD = UUID.randomUUID().toString();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions applyTheme(Cookie session, String slug, String theme) throws Exception {
        return mvc.perform(put("/api/profile").cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Nome\",\"bio\":\"\",\"slug\":\"%s\",\"theme\":\"%s\"}".formatted(slug, theme)));
    }

    @Test
    void theEditorListsTheFourThemesInOrderWithNoStoreFlags() throws Exception {
        Cookie session = signUp("a@example.com", "theme-ana");

        mvc.perform(get("/api/profile").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("noite"))
                .andExpect(jsonPath("$.themes.length()").value(4))
                .andExpect(jsonPath("$.themes[0]").value("noite"))
                .andExpect(jsonPath("$.themes[1]").value("dia"))
                .andExpect(jsonPath("$.themes[2]").value("locadora"))
                .andExpect(jsonPath("$.themes[3]").value("estufa"));
    }

    @Test
    void everyThemeCanBeAppliedAndShowsOnThePublicProfile() throws Exception {
        Cookie session = signUp("a@example.com", "theme-ana");

        for (String theme : new String[] {"locadora", "estufa", "dia", "noite"}) {
            applyTheme(session, "theme-ana", theme).andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value(theme));
            mvc.perform(get("/api/public/profiles/theme-ana")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value(theme));
        }
    }

    @Test
    void anUnknownThemeIsRefusedAndTheProfileKeepsItsTheme() throws Exception {
        Cookie session = signUp("a@example.com", "theme-ana");
        applyTheme(session, "theme-ana", "estufa").andExpect(status().isOk());

        applyTheme(session, "theme-ana", "neon").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("THEME_UNKNOWN"));

        mvc.perform(get("/api/profile").cookie(session)).andExpect(jsonPath("$.theme").value("estufa"));
    }

    @Test
    void anonymousCannotListOrApplyThemes() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/profile").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"x\",\"slug\":\"theme-ana\",\"theme\":\"locadora\"}"))
                .andExpect(status().isUnauthorized());
    }
}
