package com.meuquarto.profile.domain;

import java.util.Set;

/**
 * The plan everybody has: 4 highlights per list. It applies the free themes plus any premium theme the
 * user bought on its own (BR-05).
 */
public final class FreePlan implements Plan {

    public static final int HIGHLIGHT_LIMIT = 4;

    private final Set<ProfileTheme> ownedThemes;

    public FreePlan() {
        this(Set.of());
    }

    public FreePlan(Set<ProfileTheme> ownedThemes) {
        this.ownedThemes = Set.copyOf(ownedThemes);
    }

    @Override
    public String code() {
        return "FREE";
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
