package com.meuquarto.identity.domain;

import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.List;
import java.util.Set;

/**
 * Password rules from ADR 0012 (OWASP): length 8 to 128, not a common password, not the e-mail.
 * No composition rules (uppercase, symbols): length and a blocklist protect better and annoy less.
 * Each rule is a {@link ValidationRule}; the policy only asks them in order (ADR 0018).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private final List<ValidationRule<PasswordCandidate, Rule>> rules;

    public PasswordPolicy(Set<String> commonPasswords) {
        this.rules = List.of(
                new PasswordLengthRule(),
                new CommonPasswordRule(commonPasswords),
                new PasswordEqualsEmailRule());
    }

    public void check(String password, EmailAddress email) {
        Rule broken = ValidationRule.firstProblem(rules, new PasswordCandidate(password, email)).orElse(null);
        if (broken != null) {
            throw new AccountRuleException(broken);
        }
    }
}
