package com.meuquarto.identity.domain;

import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Refuses passwords found in a blocklist of common ones, ignoring case (ADR 0012). */
final class CommonPasswordRule implements ValidationRule<PasswordCandidate, Rule> {

    private final Set<String> commonPasswords;

    CommonPasswordRule(Set<String> commonPasswords) {
        this.commonPasswords = Set.copyOf(commonPasswords);
    }

    @Override
    public Optional<Rule> check(PasswordCandidate value) {
        String lower = value.password().toLowerCase(Locale.ROOT);
        return commonPasswords.contains(lower) ? Optional.of(Rule.PASSWORD_TOO_COMMON) : Optional.empty();
    }
}
