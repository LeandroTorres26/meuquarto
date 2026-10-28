package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.ValidationRule;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The whole username must match the platform pattern. This is what keeps a username from carrying
 * {@code /}, {@code ?}, {@code #}, {@code @}, spaces or any non-ASCII look-alike into a URL (SSRF and
 * injection control). {@code matches()} is used, never {@code find()}, so the pattern cannot match a part.
 */
final class UsernamePatternRule implements ValidationRule<String, UsernameProblem> {

    private final Pattern allowed;

    UsernamePatternRule(Pattern allowed) {
        this.allowed = allowed;
    }

    @Override
    public Optional<UsernameProblem> check(String value) {
        return allowed.matcher(value).matches() ? Optional.empty() : Optional.of(UsernameProblem.INVALID_CHARACTERS);
    }
}
