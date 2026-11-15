package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityRegistry;
import com.meuquarto.media.domain.ProviderId;
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

/**
 * The real wiring with no {@code STEAM_API_KEY}: the provider is in the registry but unavailable, linking says
 * so and stores nothing, anonymous callers cannot ask, and the providers list tells the panel "Em breve".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SteamWithoutKeyApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ActivityRegistry registry;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    @Test
    void theProductionBeanIsRegisteredButUnavailable() {
        ActivityProvider provider = registry.find(ProviderId.STEAM).orElseThrow();

        assertThat(provider).isInstanceOf(SteamProvider.class);
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void linkingWithoutAKeyAnswersProviderUnavailableAndStoresNothing() throws Exception {
        Cookie session = signUp();

        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"STEAM\",\"username\":\"ana_l\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("select count(*) from linked_accounts", Integer.class)).isZero();
    }

    @Test
    void theProvidersListMarksSteamUnavailableAndAnonymousCannotAskForIt() throws Exception {
        Cookie session = signUp();

        mvc.perform(get("/api/linked-accounts/providers").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'STEAM')].available").value(false))
                .andExpect(jsonPath("$[?(@.provider == 'LETTERBOXD')].available").value(true));
        mvc.perform(get("/api/linked-accounts/providers")).andExpect(status().isUnauthorized());
    }

    private Cookie signUp() throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"cavalo bateria grampo\",\"slug\":\"ana\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }
}
