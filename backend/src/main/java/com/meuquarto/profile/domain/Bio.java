package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.ProfileException.Problem;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.List;
import java.util.Optional;

/**
 * The short text under the name (FR-05): optional plain text, trimmed, up to 160 characters, line
 * breaks allowed. An empty bio is stored as "no bio".
 */
public record Bio(String value) {

    public static final int MAX_LENGTH = 160;

    private static final List<ValidationRule<String, Problem>> RULES = List.of(
            new BioLengthRule(),
            new PlainTextRule(true, Problem.BIO_INVALID));

    public Bio {
        Optional<Problem> problem = check(value);
        if (problem.isPresent()) {
            throw new ProfileException(problem.get());
        }
    }

    /** Trims the raw input, makes every line break a plain {@code \n}, and validates it. */
    public static Bio of(String raw) {
        return new Bio(normalize(raw));
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    /** Why an already normalized value is not a valid bio, if it isn't. */
    public static Optional<Problem> check(String normalized) {
        return ValidationRule.firstProblem(RULES, normalized);
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public String toString() {
        return value;
    }
}
