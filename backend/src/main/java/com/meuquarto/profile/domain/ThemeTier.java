package com.meuquarto.profile.domain;

/**
 * How a profile theme is sold: free for everyone, or premium (needs a plan that includes it, BR-05).
 * Themes themselves arrive with issue #37; the plan only needs this much to answer "can I apply it".
 */
public enum ThemeTier {
    FREE,
    PREMIUM
}
