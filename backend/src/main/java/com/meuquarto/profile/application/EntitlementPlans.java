package com.meuquarto.profile.application;

import com.meuquarto.billing.application.Entitlements;
import com.meuquarto.profile.domain.FreePlan;
import com.meuquarto.profile.domain.Plan;
import com.meuquarto.profile.domain.ProfileTheme;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds the user's {@link Plan} from what billing says they own. Today that is the premium themes they
 * bought, so everybody has a {@code FreePlan}; issue #39 hands out a {@code PackPlan} to who owns the pack.
 * This is the only place that decides who has what: nothing that calls {@link PlanResolver} changes.
 */
@Component
class EntitlementPlans implements PlanResolver {

    private final Entitlements entitlements;

    EntitlementPlans(Entitlements entitlements) {
        this.entitlements = entitlements;
    }

    @Override
    public Plan planOf(long userId) {
        Set<ProfileTheme> owned = EnumSet.noneOf(ProfileTheme.class);
        for (String themeId : entitlements.ownedThemeIds(userId)) {
            ProfileTheme.find(themeId).ifPresent(owned::add); // an id this code does not know yet is skipped
        }
        return new FreePlan(owned);
    }
}
