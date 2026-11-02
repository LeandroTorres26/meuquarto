package com.meuquarto.profile.domain;

import java.util.Set;

/**
 * What a user's plan allows (ADR 0018, hierarchy 3). Plain Java, no framework.
 * <p>
 * Code that needs a limit asks the plan: {@code plan.highlightLimit()}, {@code plan.canApply(theme)}.
 * It never tests whether the user owns the pack, so a new plan is a new class and no existing
 * {@code if} changes (BR-02, BR-05).
 */
public interface Plan {

    /** Stable name sent to the API ({@code FREE}, {@code PACK}). */
    String code();

    /** BR-02: how many highlights each highlight list may hold. */
    int highlightLimit();

    /** The premium themes this user bought (BR-05). Free themes are not listed: everybody has them. */
    Set<ProfileTheme> ownedThemes();

    /** BR-05: a free theme is for everyone; a premium one only for who owns it. */
    default boolean canApply(ProfileTheme theme) {
        return theme.tier() == ThemeTier.FREE || ownedThemes().contains(theme);
    }

    /** True while a list that already holds {@code currentCount} highlights can take one more. */
    default boolean hasRoomFor(int currentCount) {
        return currentCount < highlightLimit();
    }
}
