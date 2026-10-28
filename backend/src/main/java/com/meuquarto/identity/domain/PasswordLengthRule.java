package com.meuquarto.identity.domain;

import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.shared.domain.LengthRule;

/** Password length: 8 to 128 characters, counted in code points so emoji count once (ADR 0012). */
final class PasswordLengthRule extends LengthRule<PasswordCandidate, Rule> {

    PasswordLengthRule() {
        super(PasswordPolicy.MIN_LENGTH, PasswordPolicy.MAX_LENGTH);
    }

    @Override
    protected int lengthOf(PasswordCandidate value) {
        String password = value.password();
        return password == null ? 0 : password.codePointCount(0, password.length());
    }

    @Override
    protected Rule tooShort() {
        return Rule.PASSWORD_TOO_SHORT;
    }

    @Override
    protected Rule tooLong() {
        return Rule.PASSWORD_TOO_LONG;
    }
}
