package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.profile.domain.ProfileException.Problem;
import org.junit.jupiter.api.Test;

/** BR-05 for the editor: only the free themes exist for now, and the plan decides who may apply a theme. */
class ProfileThemeTest {

    @Test
    void onlyTheFreeThemesExist() {
        assertThat(ProfileTheme.of("noite")).isEqualTo(ProfileTheme.NOITE);
        assertThat(ProfileTheme.of("dia")).isEqualTo(ProfileTheme.DIA);
        assertThat(ProfileTheme.values()).allSatisfy(theme -> assertThat(theme.tier()).isEqualTo(ThemeTier.FREE));
        assertThat(ProfileTheme.DEFAULT).isEqualTo(ProfileTheme.NOITE);
    }

    @Test
    void anUnknownIdIsRefused() {
        assertThatThrownBy(() -> ProfileTheme.of("estufa")).isInstanceOfSatisfying(ProfileException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.THEME_UNKNOWN));
        assertThatThrownBy(() -> ProfileTheme.of(null)).isInstanceOf(ProfileException.class);
        assertThatThrownBy(() -> ProfileTheme.of("NOITE")).isInstanceOf(ProfileException.class);
    }

    @Test
    void theFreePlanListsTheFreeThemes() {
        assertThat(ProfileTheme.availableTo(new FreePlan())).containsExactly(ProfileTheme.NOITE, ProfileTheme.DIA);
    }

    @Test
    void aProfileKeepsItsThemeAndFallsBackToTheDefaultForAStoredIdItDoesNotKnow() {
        Profile profile = new Profile(1L, Slug.of("leandro"));
        assertThat(profile.theme()).isEqualTo(ProfileTheme.NOITE);
        profile.changeTheme(ProfileTheme.DIA);
        assertThat(profile.theme()).isEqualTo(ProfileTheme.DIA);
    }
}
