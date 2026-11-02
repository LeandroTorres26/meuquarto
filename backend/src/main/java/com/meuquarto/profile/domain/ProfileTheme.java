package com.meuquarto.profile.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The themes a profile can wear. Only the two free ones exist until the store (#37, #39) adds the
 * premium themes: each new theme is a new constant with {@link ThemeTier#PREMIUM}, and
 * {@link Plan#canApply(ThemeTier)} decides who may use it (BR-05).
 */
public enum ProfileTheme {
    NOITE("noite", ThemeTier.FREE),
    DIA("dia", ThemeTier.FREE);

    public static final ProfileTheme DEFAULT = NOITE;

    private final String id;
    private final ThemeTier tier;

    ProfileTheme(String id, ThemeTier tier) {
        this.id = id;
        this.tier = tier;
    }

    /** The id stored in {@code profiles.theme_id} and used as the {@code data-theme} attribute. */
    public String id() {
        return id;
    }

    public ThemeTier tier() {
        return tier;
    }

    /** The theme with this id; an unknown or missing id is refused. */
    public static ProfileTheme of(String id) {
        return find(id).orElseThrow(() -> new ProfileException(ProfileException.Problem.THEME_UNKNOWN));
    }

    public static Optional<ProfileTheme> find(String id) {
        return Arrays.stream(values()).filter(theme -> theme.id.equals(id)).findFirst();
    }

    /** Themes a plan may apply, in display order. */
    public static List<ProfileTheme> availableTo(Plan plan) {
        return Arrays.stream(values()).filter(theme -> plan.canApply(theme.tier)).toList();
    }
}
