package com.meuquarto.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.StorageTestcontainersConfiguration;
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

/** The profile editor (S6, FR-05): name, bio, slug and theme, who may change what, and what the public profile shows. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, StorageTestcontainersConfiguration.class})
class ProfileEditorApiTests {

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

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions save(Cookie session, String name, String bio, String slug, String theme) throws Exception {
        String body = "{\"displayName\":%s,\"bio\":%s,\"slug\":\"%s\",\"theme\":\"%s\"}".formatted(
                json(name), json(bio), slug, theme);
        return mvc.perform(put("/api/profile").cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String json(String text) {
        return text == null ? "null" : "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    // --- reading ---

    @Test
    void aNewProfileShowsTheSlugAsNameAndTheFreeThemes() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        mvc.perform(get("/api/profile").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("leandro"))
                .andExpect(jsonPath("$.displayName").value("leandro"))
                .andExpect(jsonPath("$.bio").value(""))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.theme").value("noite"))
                .andExpect(jsonPath("$.availableThemes[0]").value("noite"))
                .andExpect(jsonPath("$.availableThemes[1]").value("dia"))
                .andExpect(jsonPath("$.availableThemes.length()").value(2));
    }

    // --- changing ---

    @Test
    void theOwnerChangesNameBioAndThemeAndThePublicProfileFollows() throws Exception {
        Cookie session = signUp("a@example.com", "editor-one");
        mvc.perform(get("/api/public/profiles/editor-one")).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("editor-one")); // primes the cache

        save(session, "  Leandro Torres ", "Dev de dia,\ncinéfilo de noite.", "editor-one", "dia")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Leandro Torres"))
                .andExpect(jsonPath("$.bio").value("Dev de dia,\ncinéfilo de noite."))
                .andExpect(jsonPath("$.theme").value("dia"));

        mvc.perform(get("/api/public/profiles/editor-one")).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Leandro Torres"))
                .andExpect(jsonPath("$.bio").value("Dev de dia,\ncinéfilo de noite."))
                .andExpect(jsonPath("$.theme").value("dia"))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void anEmptyBioClearsIt() throws Exception {
        Cookie session = signUp("a@example.com", "editor-three");
        save(session, "Leandro", "oi", "editor-three", "noite").andExpect(status().isOk());
        save(session, "Leandro", "  ", "editor-three", "noite").andExpect(status().isOk());

        mvc.perform(get("/api/public/profiles/editor-three")).andExpect(jsonPath("$.bio").doesNotExist());
        assertThat(jdbc.queryForObject("select bio from profiles where slug = 'editor-three'", String.class)).isNull();
    }

    @Test
    void changingTheSlugMovesThePublicProfileAndFreesTheOldOne_BR01() throws Exception {
        Cookie session = signUp("a@example.com", "editor-two");
        mvc.perform(get("/api/public/profiles/editor-two")).andExpect(status().isOk()); // cached under the old slug

        save(session, "Leandro", "", "Novo-Nome", "noite").andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("novo-nome"));

        mvc.perform(get("/api/public/profiles/editor-two")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/profiles/novo-nome")).andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("novo-nome"));
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(jsonPath("$.slug").value("novo-nome"));

        // The old slug is free again for anybody.
        Cookie other = signUp("b@example.com", "editor-two");
        mvc.perform(get("/api/profile").cookie(other)).andExpect(jsonPath("$.slug").value("editor-two"));
    }

    @Test
    void aSlugThatIsTakenOrBrokenIsRefusedAndNothingChanges_BR01() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        signUp("b@example.com", "outra");

        save(session, "Novo nome", "", "outra", "dia").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLUG_TAKEN"));
        save(session, "Novo nome", "", "ab", "dia").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SLUG_TOO_SHORT"));
        save(session, "Novo nome", "", "admin", "dia").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SLUG_RESERVED"));
        save(session, "Novo nome", "", "com espaco", "dia").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SLUG_INVALID_CHARACTERS"));

        mvc.perform(get("/api/profile").cookie(session)).andExpect(jsonPath("$.slug").value("leandro"))
                .andExpect(jsonPath("$.displayName").value("leandro")).andExpect(jsonPath("$.theme").value("noite"));
    }

    @Test
    void keepingTheOwnSlugIsNotATakenSlug() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        save(session, "Leandro", "", "LEANDRO", "noite").andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("leandro"));
    }

    @Test
    void invalidTextAndThemesAreRefusedWithTheirOwnCodes() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        save(session, "   ", "", "leandro", "noite").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISPLAY_NAME_REQUIRED"));
        save(session, "a".repeat(41), "", "leandro", "noite").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISPLAY_NAME_TOO_LONG"));
        save(session, "duas\nlinhas", "", "leandro", "noite").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISPLAY_NAME_INVALID"));
        save(session, "Leandro", "b".repeat(161), "leandro", "noite").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BIO_TOO_LONG"));
        save(session, "Leandro", "x", "leandro", "estufa").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("THEME_UNKNOWN"));
        save(session, "Leandro", "x", "leandro", "NOITE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("THEME_UNKNOWN"));
    }

    @Test
    void markupInTheNameIsStoredAsTextAndNeverInterpreted() throws Exception {
        Cookie session = signUp("a@example.com", "editor-four");
        save(session, "<img src=x onerror=alert(1)>", "<script>x</script>", "editor-four", "noite").andExpect(status().isOk());

        // The API hands back JSON; rendering it as text is the UI's job (React escapes it).
        mvc.perform(get("/api/public/profiles/editor-four"))
                .andExpect(jsonPath("$.displayName").value("<img src=x onerror=alert(1)>"));
    }

    @Test
    void aBodyWithoutRequiredFieldsIsRejectedByValidation() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        mvc.perform(put("/api/profile").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/profile").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + "a".repeat(201) + "\",\"slug\":\"leandro\",\"theme\":\"noite\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- authorization ---

    @Test
    void anonymousCannotReadOrChangeAProfile() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/profile").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"x\",\"slug\":\"leandro\",\"theme\":\"noite\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingTheProfileNeedsTheCsrfToken() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        mvc.perform(put("/api/profile").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"x\",\"slug\":\"leandro\",\"theme\":\"noite\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserOnlyEverChangesTheirOwnProfile() throws Exception {
        Cookie ana = signUp("a@example.com", "editor-ana");
        Cookie bia = signUp("b@example.com", "editor-bia");

        save(ana, "Ana Souza", "bio da ana", "editor-ana", "dia").andExpect(status().isOk());

        // There is no id to tamper with: the profile comes from the session. Bia's data is untouched.
        mvc.perform(get("/api/profile").cookie(bia)).andExpect(jsonPath("$.slug").value("editor-bia"))
                .andExpect(jsonPath("$.displayName").value("editor-bia")).andExpect(jsonPath("$.bio").value(""))
                .andExpect(jsonPath("$.theme").value("noite"));
        mvc.perform(get("/api/public/profiles/editor-bia")).andExpect(jsonPath("$.displayName").value("editor-bia"));
        mvc.perform(get("/api/profile").cookie(ana)).andExpect(jsonPath("$.displayName").value("Ana Souza"));

        // Another user's slug in the body is a request to take that slug, which is refused.
        save(bia, "Bia", "", "editor-ana", "noite").andExpect(status().isConflict());
        mvc.perform(get("/api/public/profiles/editor-ana")).andExpect(jsonPath("$.displayName").value("Ana Souza"));
    }
}
