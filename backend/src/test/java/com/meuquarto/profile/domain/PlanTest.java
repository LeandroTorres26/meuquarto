package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Plan hierarchy (BR-02, BR-05) with no Spring: callers see only the interface. */
class PlanTest {

    @Test
    void freePlanAllowsFourHighlightsAndOnlyFreeThemesUntilOneIsBought_BR02_BR05() {
        Plan plan = new FreePlan();

        assertThat(plan.code()).isEqualTo("FREE");
        assertThat(plan.highlightLimit()).isEqualTo(4);
        assertThat(plan.canApply(ProfileTheme.NOITE)).isTrue();
        assertThat(plan.canApply(ProfileTheme.DIA)).isTrue();
        assertThat(plan.canApply(ProfileTheme.LOCADORA)).isFalse();
        assertThat(plan.canApply(ProfileTheme.ESTUFA)).isFalse();
    }

    @Test
    void packPlanAllowsTwelveHighlightsAndDoesNotIncludeThemes_BR02_BR05() {
        Plan plan = new PackPlan();

        assertThat(plan.code()).isEqualTo("PACK");
        assertThat(plan.highlightLimit()).isEqualTo(12);
        assertThat(plan.canApply(ProfileTheme.DIA)).isTrue();
        assertThat(plan.canApply(ProfileTheme.LOCADORA)).isFalse();
    }

    @Test
    void anOwnedPremiumThemeCanBeAppliedOnEveryPlan_BR05() {
        Set<ProfileTheme> owned = Set.of(ProfileTheme.LOCADORA);

        for (Plan plan : List.of(new FreePlan(owned), new PackPlan(owned))) {
            assertThat(plan.canApply(ProfileTheme.LOCADORA)).isTrue();
            assertThat(plan.canApply(ProfileTheme.ESTUFA)).isFalse();
        }
    }

    @Test
    void hasRoomForUsesTheLimitOfWhicheverPlanItIs_BR02() {
        assertThat(new FreePlan().hasRoomFor(3)).isTrue();
        assertThat(new FreePlan().hasRoomFor(4)).isFalse();
        assertThat(new PackPlan().hasRoomFor(4)).isTrue();
        assertThat(new PackPlan().hasRoomFor(11)).isTrue();
        assertThat(new PackPlan().hasRoomFor(12)).isFalse();
    }

    /** Same caller code for every plan: swapping the object changes the answer, nothing else changes. */
    @Test
    void theSameCallerCodeGetsDifferentLimitsFromDifferentPlans_BR02() {
        List<Plan> plans = List.of(new FreePlan(), new PackPlan());

        List<Integer> limits = plans.stream().map(Plan::highlightLimit).toList();

        assertThat(limits).containsExactly(4, 12);
    }
}
