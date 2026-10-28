package com.meuquarto.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain JUnit, no Spring: the rule hierarchy is ordinary Java (ADR 0018). */
class ValidationRuleTest {

    enum Problem { SHORT, LONG, NO_VOWEL }

    /** Leaf of the hierarchy: a LengthRule over Strings, counting chars. */
    static final class TextLength extends LengthRule<String, Problem> {
        TextLength(int min, int max) {
            super(min, max);
        }

        @Override
        protected int lengthOf(String value) {
            return value.length();
        }

        @Override
        protected Problem tooShort() {
            return Problem.SHORT;
        }

        @Override
        protected Problem tooLong() {
            return Problem.LONG;
        }
    }

    /** Another leaf, implementing the interface directly. */
    static final class HasVowel implements ValidationRule<String, Problem> {
        @Override
        public Optional<Problem> check(String value) {
            return value.matches(".*[aeiou].*") ? Optional.empty() : Optional.of(Problem.NO_VOWEL);
        }
    }

    private final List<ValidationRule<String, Problem>> rules = List.of(new TextLength(3, 5), new HasVowel());

    @Test
    void lengthRuleUsesTheBoundsInclusively() {
        TextLength length = new TextLength(3, 5);
        assertThat(length.check("ab")).contains(Problem.SHORT);
        assertThat(length.check("abc")).isEmpty();
        assertThat(length.check("abcde")).isEmpty();
        assertThat(length.check("abcdef")).contains(Problem.LONG);
    }

    @Test
    void lengthRuleTreatsNullAsTooShort() {
        assertThat(new TextLength(1, 5).check(null)).contains(Problem.SHORT);
    }

    @Test
    void lengthRuleRejectsImpossibleBounds() {
        assertThatThrownBy(() -> new TextLength(5, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextLength(-1, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void firstProblemWalksTheListPolymorphically() {
        assertThat(ValidationRule.firstProblem(rules, "bcd")).contains(Problem.NO_VOWEL);
        assertThat(ValidationRule.firstProblem(rules, "a")).contains(Problem.SHORT);
        assertThat(ValidationRule.firstProblem(rules, "abc")).isEmpty();
    }

    @Test
    void firstProblemStopsAtTheFirstBrokenRule() {
        // "b" breaks both rules; the length rule is first in the list, so it wins.
        assertThat(ValidationRule.firstProblem(rules, "b")).contains(Problem.SHORT);
    }

    @Test
    void acceptsIsTheDefaultMethodOverCheck() {
        assertThat(new HasVowel().accepts("abc")).isTrue();
        assertThat(new HasVowel().accepts("xyz")).isFalse();
    }
}
