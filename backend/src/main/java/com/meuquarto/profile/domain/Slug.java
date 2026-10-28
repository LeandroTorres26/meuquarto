package com.meuquarto.profile.domain;

import com.meuquarto.shared.domain.ValidationRule;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Public address of a profile, as in {@code meuquarto.com.br/<slug>} (BR-01).
 * Input is trimmed and lowercased; the result must be 3 to 30 characters of {@code a-z}, {@code 0-9}
 * and single hyphens between them, and must not be a reserved word.
 */
public record Slug(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    /** Asked in this order; the first broken rule is the problem reported (polymorphism over the list). */
    private static final List<ValidationRule<String, Problem>> RULES = List.of(
            new SlugLengthRule(),
            new SlugCharactersRule(),
            new SlugHyphenRule(),
            new SlugReservedRule());

    public Slug {
        Problem problem = check(value).orElse(null);
        if (problem != null) {
            throw new InvalidSlugException(problem);
        }
    }

    /** Normalizes raw input (trim, lowercase) and validates it. */
    public static Slug of(String raw) {
        return new Slug(normalize(raw));
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    /** Why an already normalized value is not a valid slug, if it isn't. */
    public static Optional<Problem> check(String normalized) {
        return ValidationRule.firstProblem(RULES, normalized);
    }

    @Override
    public String toString() {
        return value;
    }

    public enum Problem {
        TOO_SHORT,
        TOO_LONG,
        /** Anything other than a-z, 0-9 and hyphen, including accents and spaces. */
        INVALID_CHARACTERS,
        /** Hyphen at the start or end, or two in a row. */
        INVALID_HYPHEN,
        RESERVED
    }
}
