package com.meuquarto.profile.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The themes a profile can wear, all free for everyone (ADR 0022). A new theme is a new constant plus its
 * tokens in the design system; the id is the {@code data-theme} attribute.
 */
public enum ProfileTheme {
    NOITE("noite"),
    DIA("dia"),
    LOCADORA("locadora"),
    ESTUFA("estufa");

    public static final ProfileTheme DEFAULT = NOITE;

    private final String id;

    ProfileTheme(String id) {
        this.id = id;
    }

    /** The id stored in {@code profiles.theme_id} and used as the {@code data-theme} attribute. */
    public String id() {
        return id;
    }

    /** The theme with this id; an unknown or missing id is refused. */
    public static ProfileTheme of(String id) {
        return find(id).orElseThrow(() -> new ProfileException(ProfileException.Problem.THEME_UNKNOWN));
    }

    public static Optional<ProfileTheme> find(String id) {
        return Arrays.stream(values()).filter(theme -> theme.id.equals(id)).findFirst();
    }
}
