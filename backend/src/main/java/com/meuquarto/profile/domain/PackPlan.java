package com.meuquarto.profile.domain;

/**
 * The plan of a user who bought the pack: 12 highlights per list and the premium themes.
 * Per-theme ownership (which premium theme was bought) may refine {@link #canApply} in issue #37.
 */
public final class PackPlan implements Plan {

    public static final int HIGHLIGHT_LIMIT = 12;

    @Override
    public String code() {
        return "PACK";
    }

    @Override
    public int highlightLimit() {
        return HIGHLIGHT_LIMIT;
    }

    @Override
    public boolean canApply(ThemeTier tier) {
        return true;
    }
}
