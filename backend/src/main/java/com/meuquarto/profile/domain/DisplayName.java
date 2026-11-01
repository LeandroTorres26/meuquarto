package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.ProfileException.Problem;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.List;
import java.util.Optional;

/**
 * The name shown at the top of a profile (FR-05): plain text, trimmed, 1 to 40 characters. Checked
 * by the same {@link ValidationRule} list pattern as {@link Slug}.
 */
public record DisplayName(String value) {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 40;

    private static final List<ValidationRule<String, Problem>> RULES = List.of(
            new DisplayNameLengthRule(),
            new PlainTextRule(false, Problem.DISPLAY_NAME_INVALID));

    public DisplayName {
        Optional<Problem> problem = check(value);
        if (problem.isPresent()) {
            throw new ProfileException(problem.get());
        }
    }

    /** Trims the raw input and validates it. */
    public static DisplayName of(String raw) {
        return new DisplayName(normalize(raw));
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip();
    }

    /** Why an already normalized value is not a valid name, if it isn't. */
    public static Optional<Problem> check(String normalized) {
        return ValidationRule.firstProblem(RULES, normalized);
    }

    @Override
    public String toString() {
        return value;
    }
}
