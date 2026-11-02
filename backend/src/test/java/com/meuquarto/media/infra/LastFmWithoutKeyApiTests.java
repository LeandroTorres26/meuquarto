package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** The real wiring with no {@code LASTFM_API_KEY}: the provider exists but says it is unavailable, and linking says so. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LastFmWithoutKeyApiTests {

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
    void theProductionBeanIsRegisteredButNotConfigured() {
        LastFmProvider provider = (LastFmProvider) registry.find(ProviderId.LASTFM).orElseThrow();

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    void linkingLastFmWithoutAKeyAnswersProviderUnavailableAndStoresNothing() throws Exception {
        Cookie session = mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"cavalo bateria grampo\",\"slug\":\"ana\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");

        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"LASTFM\",\"username\":\"ana_l\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("select count(*) from linked_accounts", Integer.class)).isZero();
    }
}
