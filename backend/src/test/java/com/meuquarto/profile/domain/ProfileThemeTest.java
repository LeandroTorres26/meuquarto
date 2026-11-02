package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.profile.domain.ProfileException.Problem;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The themes a profile can wear (FR-17) and which ones a plan offers (BR-05). */
class ProfileThemeTest {

    @Test
    void thereAreTwoFreeAndTwoPremiumThemesInDisplayOrder() {
        assertThat(ProfileTheme.values()).containsExactly(ProfileTheme.NOITE, ProfileTheme.DIA,
                ProfileTheme.LOCADORA, ProfileTheme.ESTUFA);
        assertThat(ProfileTheme.NOITE.premium()).isFalse();
        assertThat(ProfileTheme.DIA.premium()).isFalse();
        assertThat(ProfileTheme.LOCADORA.premium()).isTrue();
        assertThat(ProfileTheme.ESTUFA.premium()).isTrue();
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
    void thePlanListsTheThemesItCanApply_BR05() {
        assertThat(ProfileTheme.availableTo(new FreePlan())).containsExactly(ProfileTheme.NOITE, ProfileTheme.DIA);
        assertThat(ProfileTheme.availableTo(new FreePlan(Set.of(ProfileTheme.ESTUFA))))
                .containsExactly(ProfileTheme.NOITE, ProfileTheme.DIA, ProfileTheme.ESTUFA);
    }

    @Test
    void aProfileKeepsItsThemeAndFallsBackToTheDefaultForAStoredIdItDoesNotKnow() {
        Profile profile = new Profile(1L, Slug.of("leandro"));
        assertThat(profile.theme()).isEqualTo(ProfileTheme.NOITE);
        profile.changeTheme(ProfileTheme.DIA);
        assertThat(profile.theme()).isEqualTo(ProfileTheme.DIA);
    }
}
