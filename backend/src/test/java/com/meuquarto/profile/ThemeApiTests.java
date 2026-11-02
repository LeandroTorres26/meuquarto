package com.meuquarto.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.profile.domain.ProfileTheme;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.StorageTestcontainersConfiguration;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
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

/** Themes in the store catalog and in the editor (FR-17, BR-05): the seed, the picker, and who may apply what. */
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
        jdbc.update("delete from users"); // entitlements go with their user
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

    private void grant(String email, String sku) {
        int rows = jdbc.update("""
                insert into entitlements (user_id, product_id)
                select u.id, p.id from users u, products p where u.email = ? and p.sku = ?
                """, email, sku);
        assertThat(rows).isEqualTo(1);
    }

    // --- the seed ---

    @Test
    void theCatalogIsSeededAndMatchesTheThemesTheCodeKnows() {
        List<String> ids = jdbc.queryForList("select id from themes order by id", String.class);
        assertThat(ids).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(ProfileTheme.values()).map(ProfileTheme::id).toList());

        for (ProfileTheme theme : ProfileTheme.values()) {
            Long productId = jdbc.queryForObject("select product_id from themes where id = ?", Long.class, theme.id());
            assertThat(productId == null).as("%s is free when it has no product", theme.id())
                    .isEqualTo(!theme.premium());
        }
        assertThat(jdbc.queryForList("select sku from products order by sku", String.class))
                .containsExactly("pack-highlights", "theme-estufa", "theme-locadora");
        assertThat(jdbc.queryForObject("select price_cents from products where sku = 'pack-highlights'", Integer.class))
                .isEqualTo(1490);
        assertThat(jdbc.queryForObject("select price_cents from products where sku = 'theme-locadora'", Integer.class))
                .isEqualTo(990);
        assertThat(jdbc.queryForObject("select count(*) from products where stripe_price_ref is not null", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from entitlements", Integer.class)).isZero();
    }

    // --- the picker ---

    @Test
    void theEditorListsAllFourThemesWithTheLockedOnesMarked() throws Exception {
        Cookie session = signUp("a@example.com", "theme-ana");

        mvc.perform(get("/api/profile").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.themes.length()").value(4))
                .andExpect(jsonPath("$.themes[0].id").value("noite"))
                .andExpect(jsonPath("$.themes[0].premium").value(false))
                .andExpect(jsonPath("$.themes[0].locked").value(false))
                .andExpect(jsonPath("$.themes[1].id").value("dia"))
                .andExpect(jsonPath("$.themes[1].locked").value(false))
                .andExpect(jsonPath("$.themes[2].id").value("locadora"))
                .andExpect(jsonPath("$.themes[2].premium").value(true))
                .andExpect(jsonPath("$.themes[2].owned").value(false))
                .andExpect(jsonPath("$.themes[2].locked").value(true))
                .andExpect(jsonPath("$.themes[3].id").value("estufa"))
                .andExpect(jsonPath("$.themes[3].locked").value(true))
                .andExpect(jsonPath("$.availableThemes.length()").value(2));
    }

    // --- BR-05 ---

    @Test
    void aPremiumThemeIsRefusedWithoutEntitlement_BR05() throws Exception {
        Cookie session = signUp("a@example.com", "theme-ana");

        applyTheme(session, "theme-ana", "locadora").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("THEME_LOCKED"));
        applyTheme(session, "theme-ana", "estufa").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("THEME_LOCKED"));

        mvc.perform(get("/api/profile").cookie(session)).andExpect(jsonPath("$.theme").value("noite"));
        mvc.perform(get("/api/public/profiles/theme-ana")).andExpect(jsonPath("$.theme").value("noite"))
                .andExpect(jsonPath("$.premiumTheme").value(false));
    }

    @Test
    void anOwnedPremiumThemeCanBeAppliedAndTheOtherStaysLocked_BR05() throws Exception {
        Cookie session = signUp("a@example.com", "theme-ana");
        grant("a@example.com", "theme-locadora");

        mvc.perform(get("/api/profile").cookie(session))
                .andExpect(jsonPath("$.themes[2].owned").value(true))
                .andExpect(jsonPath("$.themes[2].locked").value(false))
                .andExpect(jsonPath("$.themes[3].locked").value(true))
                .andExpect(jsonPath("$.availableThemes.length()").value(3));

        applyTheme(session, "theme-ana", "locadora").andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("locadora"));
        applyTheme(session, "theme-ana", "estufa").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("THEME_LOCKED"));

        mvc.perform(get("/api/public/profiles/theme-ana")).andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("locadora"))
                .andExpect(jsonPath("$.premiumTheme").value(true));
    }

    @Test
    void aFreeThemeCanAlwaysBeAppliedBackAfterAPremiumOne_BR05() throws Exception {
        Cookie session = signUp("a@example.com", "theme-ana");
        grant("a@example.com", "theme-estufa");

        applyTheme(session, "theme-ana", "estufa").andExpect(status().isOk());
        applyTheme(session, "theme-ana", "dia").andExpect(status().isOk()).andExpect(jsonPath("$.theme").value("dia"));
    }

    // --- authorization ---

    @Test
    void anotherUsersPurchaseGivesMeNothing_BR05() throws Exception {
        Cookie ana = signUp("a@example.com", "theme-ana");
        Cookie bia = signUp("b@example.com", "theme-bia");
        grant("a@example.com", "theme-locadora");

        applyTheme(ana, "theme-ana", "locadora").andExpect(status().isOk());

        applyTheme(bia, "theme-bia", "locadora").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("THEME_LOCKED"));
        mvc.perform(get("/api/profile").cookie(bia)).andExpect(jsonPath("$.theme").value("noite"))
                .andExpect(jsonPath("$.themes[2].owned").value(false))
                .andExpect(jsonPath("$.themes[2].locked").value(true));
    }

    @Test
    void anonymousCannotListOrApplyThemes() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/profile").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"x\",\"slug\":\"theme-ana\",\"theme\":\"locadora\"}"))
                .andExpect(status().isUnauthorized());
    }
}
