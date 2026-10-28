package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.ValidationRule;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds the usual pair of rules (length, then characters) so each provider only says its numbers and
 * its pattern. The length rule comes first: a huge string is refused before the regex looks at it.
 */
public final class UsernameRules {

    private UsernameRules() {}

    /** {@code characters} is a regex for the whole name, such as {@code [a-z0-9_]+}; keep it ASCII. */
    public static List<ValidationRule<String, UsernameProblem>> of(int minLength, int maxLength, String characters) {
        return List.of(
                new UsernameLengthRule(minLength, maxLength),
                new UsernamePatternRule(Pattern.compile(characters)));
    }
}
