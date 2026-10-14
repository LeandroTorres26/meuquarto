package com.meuquarto.profile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.profile.domain.Profile;
import com.meuquarto.profile.domain.Slug;
import com.meuquarto.profile.infra.ProfileRepository;
import com.meuquarto.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SlugAvailabilityApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProfileRepository profiles;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        profiles.deleteAll();
    }

    @Test
    void freeSlugIsAvailableToAnonymousVisitors() throws Exception {
        mvc.perform(get("/api/slugs/{slug}/availability", "Leandro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("leandro"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void takenSlugIsUnavailable() throws Exception {
        profiles.save(new Profile(1L, Slug.of("leandro")));

        mvc.perform(get("/api/slugs/{slug}/availability", "LEANDRO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("TAKEN"));
    }

    @Test
    void reservedAndMalformedSlugsAreUnavailable() throws Exception {
        mvc.perform(get("/api/slugs/{slug}/availability", "painel"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("RESERVED"));
        mvc.perform(get("/api/slugs/{slug}/availability", "joão"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("INVALID_CHARACTERS"));
        mvc.perform(get("/api/slugs/{slug}/availability", "a".repeat(200)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("TOO_LONG"));
    }

    @Test
    void onlyGetIsPublic() throws Exception {
        // Without a CSRF token a state-changing request is refused before anything else.
        mvc.perform(post("/api/slugs/{slug}/availability", "leandro")).andExpect(status().isForbidden());
    }

    @Test
    void databaseRejectsDuplicateAndMalformedSlugsEvenOutsideTheApi() {
        profiles.saveAndFlush(new Profile(1L, Slug.of("leandro")));

        assertThatThrownBy(() -> profiles.saveAndFlush(new Profile(2L, Slug.of("leandro"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into profiles (user_id, slug) values (3, 'Bad Slug')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
