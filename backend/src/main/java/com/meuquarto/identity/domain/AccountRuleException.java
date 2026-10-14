package com.meuquarto.identity.domain;

/** A sign-up or credential rule was broken; {@link #rule()} says which one, for the API error code. */
public class AccountRuleException extends RuntimeException {

    public enum Rule {
        INVALID_EMAIL,
        EMAIL_TAKEN,
        PASSWORD_TOO_SHORT,
        PASSWORD_TOO_LONG,
        PASSWORD_TOO_COMMON,
        PASSWORD_SAME_AS_EMAIL
    }

    private final Rule rule;

    public AccountRuleException(Rule rule) {
        super(rule.name());
        this.rule = rule;
    }

    public Rule rule() {
        return rule;
    }
}
