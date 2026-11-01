package com.meuquarto.profile.application;

import com.meuquarto.profile.domain.FreePlan;
import com.meuquarto.profile.domain.Plan;
import org.springframework.stereotype.Component;

/**
 * Entitlements do not exist yet, so everybody has the free plan. Issue #39 replaces this with a
 * resolver that returns a {@code PackPlan} when the user owns the pack; nothing that calls
 * {@link PlanResolver} changes.
 */
@Component
class EveryoneIsFree implements PlanResolver {

    private final Plan free = new FreePlan();

    @Override
    public Plan planOf(long userId) {
        return free;
    }
}
