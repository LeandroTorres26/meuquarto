package com.meuquarto.profile.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The themes a profile can wear. A new theme is a new constant, free or {@link ThemeTier#PREMIUM}, plus a
 * row in {@code themes} (seeded) and its tokens in the design system; {@link Plan#canApply(ProfileTheme)}
 * decides who may use it (BR-05).
 */
public enum ProfileTheme {
    NOITE("noite", ThemeTier.FREE),
    DIA("dia", ThemeTier.FREE),
    LOCADORA("locadora", ThemeTier.PREMIUM),
    ESTUFA("estufa", ThemeTier.PREMIUM);

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

    /** True for the themes that are bought. */
    public boolean premium() {
        return tier == ThemeTier.PREMIUM;
    }

    /** Themes a plan may apply, in display order. */
    public static List<ProfileTheme> availableTo(Plan plan) {
        return Arrays.stream(values()).filter(plan::canApply).toList();
    }
}
