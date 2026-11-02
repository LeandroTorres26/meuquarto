package com.meuquarto.identity.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

/** A sign-up or credential rule was broken; {@link #rule()} says which one, for the API error code. */
public final class AccountRuleException extends DomainException {

    public enum Rule {
        INVALID_EMAIL,
        EMAIL_TAKEN,
        PASSWORD_TOO_SHORT,
        PASSWORD_TOO_LONG,
        PASSWORD_TOO_COMMON,
        PASSWORD_SAME_AS_EMAIL,
        /** The current password given to confirm a sensitive action is wrong. */
        WRONG_PASSWORD,
        /** A password reset link is unknown, tampered with, expired or already used (one code for all, on purpose). */
        INVALID_RESET_TOKEN
    }

    private final Rule rule;

    public AccountRuleException(Rule rule) {
        super(rule.name(), rule == Rule.EMAIL_TAKEN ? ErrorKind.CONFLICT : ErrorKind.INVALID);
        this.rule = rule;
    }

    public Rule rule() {
        return rule;
    }
}
