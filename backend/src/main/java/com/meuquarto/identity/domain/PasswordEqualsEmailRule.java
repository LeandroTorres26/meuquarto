package com.meuquarto.identity.domain;

import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.Locale;
import java.util.Optional;

/** Refuses a password equal to the e-mail or to the part before the {@code @} (ADR 0012). */
final class PasswordEqualsEmailRule implements ValidationRule<PasswordCandidate, Rule> {

    @Override
    public Optional<Rule> check(PasswordCandidate value) {
        String lower = value.password().toLowerCase(Locale.ROOT);
        EmailAddress email = value.email();
        boolean same = lower.equals(email.value()) || lower.equals(email.localPart());
        return same ? Optional.of(Rule.PASSWORD_SAME_AS_EMAIL) : Optional.empty();
    }
}
