package com.meuquarto.profile.domain;

/** The plan everybody has: 4 highlights per list and free themes only. */
public final class FreePlan implements Plan {

    public static final int HIGHLIGHT_LIMIT = 4;

    @Override
    public String code() {
        return "FREE";
    }

    @Override
    public int highlightLimit() {
        return HIGHLIGHT_LIMIT;
    }

    @Override
    public boolean canApply(ThemeTier tier) {
        return tier == ThemeTier.FREE;
    }
}
