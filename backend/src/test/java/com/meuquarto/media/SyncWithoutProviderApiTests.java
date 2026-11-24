package com.meuquarto.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.media.domain.ActivityRegistry;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The sync of an account whose platform has no provider in the registry: the run is SKIPPED with the
 * unavailable code and the account stays healthy. Every syncing platform has a provider now, so the registry
 * here is an empty one that wins over the real one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SyncWithoutProviderApiTests.EmptyRegistry.class})
class SyncWithoutProviderApiTests {

    @TestConfiguration
    static class EmptyRegistry {

        @Bean
        @Primary
        ActivityRegistry noProviders() {
            return new ActivityRegistry(List.of());
        }
    }

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
    void anAccountWhosePlatformHasNoProviderIsSkipped() throws Exception {
        Cookie session = mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"cavalo bateria grampo\",\"slug\":\"ana\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
        long profileId = jdbc.queryForObject("select id from profiles where slug = 'ana'", Long.class);
        jdbc.update("insert into linked_accounts (profile_id, provider, username) values (?, 'LASTFM', 'ana_s')", profileId);
        long account = jdbc.queryForObject("select id from linked_accounts", Long.class);

        mvc.perform(post("/api/linked-accounts/" + account + "/sync").cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("select status from sync_runs", String.class)).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("select status from linked_accounts", String.class)).isEqualTo("ACTIVE");
    }
}
