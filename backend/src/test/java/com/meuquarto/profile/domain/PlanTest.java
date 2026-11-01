package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The Plan hierarchy (BR-02, BR-05) with no Spring: callers see only the interface. */
class PlanTest {

    @Test
    void freePlanAllowsFourHighlightsAndOnlyFreeThemes_BR02_BR05() {
        Plan plan = new FreePlan();

        assertThat(plan.code()).isEqualTo("FREE");
        assertThat(plan.highlightLimit()).isEqualTo(4);
        assertThat(plan.canApply(ThemeTier.FREE)).isTrue();
        assertThat(plan.canApply(ThemeTier.PREMIUM)).isFalse();
    }

    @Test
    void packPlanAllowsTwelveHighlightsAndPremiumThemes_BR02_BR05() {
        Plan plan = new PackPlan();

        assertThat(plan.code()).isEqualTo("PACK");
        assertThat(plan.highlightLimit()).isEqualTo(12);
        assertThat(plan.canApply(ThemeTier.FREE)).isTrue();
        assertThat(plan.canApply(ThemeTier.PREMIUM)).isTrue();
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
