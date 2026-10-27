package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.Slug.Problem;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.Optional;
import java.util.regex.Pattern;

/** Hyphens only between letters or digits, never at the ends or two in a row (BR-01). */
final class SlugHyphenRule implements ValidationRule<String, Problem> {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    @Override
    public Optional<Problem> check(String value) {
        return FORMAT.matcher(value).matches() ? Optional.empty() : Optional.of(Problem.INVALID_HYPHEN);
    }
}
