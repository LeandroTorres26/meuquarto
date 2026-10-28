package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.profile.domain.Slug.Problem;
import com.meuquarto.shared.domain.LengthRule;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Each slug rule on its own (BR-01), then the rules together as a list. */
class SlugRulesTest {

    @Test
    void lengthRuleIsALengthRule() {
        LengthRule<String, Problem> rule = new SlugLengthRule();
        assertThat(rule.check("ab")).contains(Problem.TOO_SHORT);
        assertThat(rule.check("abc")).isEmpty();
        assertThat(rule.check("a".repeat(30))).isEmpty();
        assertThat(rule.check("a".repeat(31))).contains(Problem.TOO_LONG);
        assertThat(rule.check(null)).contains(Problem.TOO_SHORT);
    }

    @Test
    void charactersRuleAcceptsOnlyLowercaseDigitsAndHyphen() {
        SlugCharactersRule rule = new SlugCharactersRule();
        assertThat(rule.check("abc-123")).isEmpty();
        assertThat(rule.check("Abc")).contains(Problem.INVALID_CHARACTERS);
        assertThat(rule.check("joão")).contains(Problem.INVALID_CHARACTERS);
        assertThat(rule.check("a b")).contains(Problem.INVALID_CHARACTERS);
    }

    @Test
    void hyphenRuleRefusesEdgesAndDoubles() {
        SlugHyphenRule rule = new SlugHyphenRule();
        assertThat(rule.check("a-b-c")).isEmpty();
        assertThat(rule.check("-abc")).contains(Problem.INVALID_HYPHEN);
        assertThat(rule.check("abc-")).contains(Problem.INVALID_HYPHEN);
        assertThat(rule.check("a--b")).contains(Problem.INVALID_HYPHEN);
    }

    @Test
    void reservedRuleRefusesRoutesAndKeepsOthers() {
        SlugReservedRule rule = new SlugReservedRule();
        assertThat(rule.check("admin")).contains(Problem.RESERVED);
        assertThat(rule.check("painel")).contains(Problem.RESERVED);
        assertThat(rule.check("leandro")).isEmpty();
    }

    @Test
    void theListReportsTheFirstBrokenRule() {
        List<ValidationRule<String, Problem>> rules = List.of(
                new SlugLengthRule(), new SlugCharactersRule(), new SlugHyphenRule(), new SlugReservedRule());
        assertThat(ValidationRule.firstProblem(rules, "a")).contains(Problem.TOO_SHORT);
        assertThat(ValidationRule.firstProblem(rules, "AB-")).contains(Problem.INVALID_CHARACTERS);
        assertThat(ValidationRule.firstProblem(rules, "ab-")).contains(Problem.INVALID_HYPHEN);
        assertThat(ValidationRule.firstProblem(rules, "admin")).contains(Problem.RESERVED);
        assertThat(ValidationRule.firstProblem(rules, "leandro")).isEmpty();
    }

    @Test
    void slugCheckUsesTheSameRules() {
        assertThat(Slug.check("admin")).contains(Problem.RESERVED);
        assertThat(Slug.check("leandro")).isEmpty();
    }
}
