package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.Slug.Problem;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.Optional;
import java.util.regex.Pattern;

/** Only a-z, 0-9 and hyphen; accents and spaces are refused (BR-01). */
final class SlugCharactersRule implements ValidationRule<String, Problem> {

    private static final Pattern ALLOWED = Pattern.compile("[a-z0-9-]*");

    @Override
    public Optional<Problem> check(String value) {
        return ALLOWED.matcher(value).matches() ? Optional.empty() : Optional.of(Problem.INVALID_CHARACTERS);
    }
}
