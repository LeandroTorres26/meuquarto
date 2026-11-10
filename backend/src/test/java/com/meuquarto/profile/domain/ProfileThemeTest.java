package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.profile.domain.ProfileException.Problem;
import org.junit.jupiter.api.Test;

/** The four themes a profile can wear, free for everyone (FR-05, ADR 0022). */
class ProfileThemeTest {

    @Test
    void thereAreFourThemesInDisplayOrderAndNoiteIsTheDefault() {
        assertThat(ProfileTheme.values()).containsExactly(ProfileTheme.NOITE, ProfileTheme.DIA,
                ProfileTheme.LOCADORA, ProfileTheme.ESTUFA);
        assertThat(ProfileTheme.of("locadora")).isEqualTo(ProfileTheme.LOCADORA);
        assertThat(ProfileTheme.DEFAULT).isEqualTo(ProfileTheme.NOITE);
    }

    @Test
    void anUnknownIdIsRefused() {
        assertThatThrownBy(() -> ProfileTheme.of("neon")).isInstanceOfSatisfying(ProfileException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.THEME_UNKNOWN));
        assertThatThrownBy(() -> ProfileTheme.of(null)).isInstanceOf(ProfileException.class);
        assertThatThrownBy(() -> ProfileTheme.of("NOITE")).isInstanceOf(ProfileException.class);
    }

    @Test
    void aProfileKeepsItsTheme() {
        Profile profile = new Profile(1L, Slug.of("leandro"));
        assertThat(profile.theme()).isEqualTo(ProfileTheme.NOITE);
        profile.changeTheme(ProfileTheme.ESTUFA);
        assertThat(profile.theme()).isEqualTo(ProfileTheme.ESTUFA);
    }
}
