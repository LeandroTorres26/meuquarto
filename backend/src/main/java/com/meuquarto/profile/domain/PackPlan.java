package com.meuquarto.profile.domain;

import java.util.Set;

/**
 * The plan of a user who bought the pack: 12 highlights per list. The pack does not include themes
 * (they are sold one by one), so it applies the free themes plus the premium ones the user owns.
 */
public final class PackPlan implements Plan {

    public static final int HIGHLIGHT_LIMIT = 12;

    private final Set<ProfileTheme> ownedThemes;

    public PackPlan() {
        this(Set.of());
    }

    public PackPlan(Set<ProfileTheme> ownedThemes) {
        this.ownedThemes = Set.copyOf(ownedThemes);
    }

    @Override
    public String code() {
        return "PACK";
    }

    @Override
    public int highlightLimit() {
        return HIGHLIGHT_LIMIT;
    }

    @Override
    public Set<ProfileTheme> ownedThemes() {
        return ownedThemes;
    }
}
