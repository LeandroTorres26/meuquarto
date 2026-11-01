package com.meuquarto.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.shared.domain.LengthRule;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Each password rule on its own (ADR 0012), then the rules together as a list. */
class PasswordRulesTest {

    private final EmailAddress email = EmailAddress.of("leandro@example.com");

    private PasswordCandidate candidate(String password) {
        return new PasswordCandidate(password, email);
    }

    @Test
    void lengthRuleIsALengthRuleCountingCodePoints() {
        LengthRule<PasswordCandidate, Rule> rule = new PasswordLengthRule();
        assertThat(rule.check(candidate("abc1234"))).contains(Rule.PASSWORD_TOO_SHORT);
        assertThat(rule.check(candidate("abc12345"))).isEmpty();
        assertThat(rule.check(candidate("a".repeat(128)))).isEmpty();
        assertThat(rule.check(candidate("a".repeat(129)))).contains(Rule.PASSWORD_TOO_LONG);
        // 4 emoji are 8 chars in UTF-16 but 4 code points: still too short.
        assertThat(rule.check(candidate("😀".repeat(4)))).contains(Rule.PASSWORD_TOO_SHORT);
        assertThat(rule.check(candidate(null))).contains(Rule.PASSWORD_TOO_SHORT);
    }

    @Test
    void commonPasswordRuleIgnoresCase() {
        CommonPasswordRule rule = new CommonPasswordRule(Set.of("senha123"));
        assertThat(rule.check(candidate("SENHA123"))).contains(Rule.PASSWORD_TOO_COMMON);
        assertThat(rule.check(candidate("cavalo bateria grampo"))).isEmpty();
    }

    @Test
    void equalsEmailRuleRefusesEmailAndLocalPart() {
        PasswordEqualsEmailRule rule = new PasswordEqualsEmailRule();
        assertThat(rule.check(candidate("leandro@example.com"))).contains(Rule.PASSWORD_SAME_AS_EMAIL);
        assertThat(rule.check(candidate("LEANDRO"))).contains(Rule.PASSWORD_SAME_AS_EMAIL);
        assertThat(rule.check(candidate("cavalo bateria grampo"))).isEmpty();
    }

    @Test
    void theListReportsTheFirstBrokenRule() {
        List<ValidationRule<PasswordCandidate, Rule>> rules = List.of(
                new PasswordLengthRule(), new CommonPasswordRule(Set.of("leandro1")), new PasswordEqualsEmailRule());
        assertThat(ValidationRule.firstProblem(rules, candidate("abc"))).contains(Rule.PASSWORD_TOO_SHORT);
        assertThat(ValidationRule.firstProblem(rules, candidate("leandro1"))).contains(Rule.PASSWORD_TOO_COMMON);
        assertThat(ValidationRule.firstProblem(rules, candidate("cavalo bateria grampo"))).isEmpty();
    }
}
