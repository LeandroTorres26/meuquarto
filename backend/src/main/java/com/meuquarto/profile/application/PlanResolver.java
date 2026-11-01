package com.meuquarto.profile.application;

import com.meuquarto.profile.domain.Plan;

/**
 * Tells which {@link Plan} a user has. Callers get a {@code Plan} and ask it things; they never learn
 * (or test) which plan it is.
 */
public interface PlanResolver {

    Plan planOf(long userId);
}
