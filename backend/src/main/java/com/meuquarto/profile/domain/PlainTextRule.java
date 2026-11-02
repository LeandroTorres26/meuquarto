package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.ProfileException.Problem;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.Optional;

/**
 * Text typed by the owner must be plain text: no control characters. A line break is allowed only
 * where the field says so (the bio). This is not HTML escaping: the UI never renders markup from
 * users, this rule only keeps invisible control characters out of the database.
 */
final class PlainTextRule implements ValidationRule<String, Problem> {

    private final boolean allowLineBreaks;
    private final Problem problem;

    PlainTextRule(boolean allowLineBreaks, Problem problem) {
        this.allowLineBreaks = allowLineBreaks;
        this.problem = problem;
    }

    @Override
    public Optional<Problem> check(String value) {
        if (value != null && value.codePoints().anyMatch(this::isForbidden)) {
            return Optional.of(problem);
        }
        return Optional.empty();
    }

    private boolean isForbidden(int codePoint) {
        if (codePoint == '\n') {
            return !allowLineBreaks;
        }
        return Character.isISOControl(codePoint);
    }
}
