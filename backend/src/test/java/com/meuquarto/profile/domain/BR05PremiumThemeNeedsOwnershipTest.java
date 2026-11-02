package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * BR-05: a premium theme can only be applied if the user owns it. The check is a question to the
 * {@link Plan}; the test never mentions which plan it is, only what the object answers.
 */
class BR05PremiumThemeNeedsOwnershipTest {

    @Test
    void aPremiumThemeIsRefusedToWhoDoesNotOwnIt() {
        List<Plan> plansWithoutThemes = List.of(new FreePlan(), new PackPlan());

        for (Plan plan : plansWithoutThemes) {
            assertThat(plan.canApply(ProfileTheme.LOCADORA)).isFalse();
            assertThat(plan.canApply(ProfileTheme.ESTUFA)).isFalse();
        }
    }

    @Test
    void ownershipIsPerThemeNotPerTier() {
        Plan plan = new FreePlan(Set.of(ProfileTheme.LOCADORA));

        assertThat(plan.canApply(ProfileTheme.LOCADORA)).isTrue();
        assertThat(plan.canApply(ProfileTheme.ESTUFA)).isFalse();
    }

    @Test
    void freeThemesAreAlwaysAllowed() {
        Plan plan = new FreePlan();

        Arrays.stream(ProfileTheme.values()).filter(theme -> !theme.premium())
                .forEach(theme -> assertThat(plan.canApply(theme)).isTrue());
    }

    @Test
    void thePlanOwnsAnImmutableCopyOfWhatWasBought() {
        Set<ProfileTheme> bought = new java.util.HashSet<>(Set.of(ProfileTheme.ESTUFA));
        Plan plan = new FreePlan(bought);

        bought.add(ProfileTheme.LOCADORA);

        assertThat(plan.canApply(ProfileTheme.LOCADORA)).isFalse();
    }
}
