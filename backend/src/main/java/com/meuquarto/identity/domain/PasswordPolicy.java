package com.meuquarto.identity.domain;

import com.meuquarto.identity.domain.AccountRuleException.Rule;
import java.util.Locale;
import java.util.Set;

/**
 * Password rules from ADR 0012 (OWASP): length 8 to 128, not a common password, not the e-mail.
 * No composition rules (uppercase, symbols): length and a blocklist protect better and annoy less.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private final Set<String> commonPasswords;

    public PasswordPolicy(Set<String> commonPasswords) {
        this.commonPasswords = Set.copyOf(commonPasswords);
    }

    public void check(String password, EmailAddress email) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new AccountRuleException(Rule.PASSWORD_TOO_SHORT);
        }
        if (password.codePointCount(0, password.length()) > MAX_LENGTH) {
            throw new AccountRuleException(Rule.PASSWORD_TOO_LONG);
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(lower)) {
            throw new AccountRuleException(Rule.PASSWORD_TOO_COMMON);
        }
        if (lower.equals(email.value()) || lower.equals(email.localPart())) {
            throw new AccountRuleException(Rule.PASSWORD_SAME_AS_EMAIL);
        }
    }
}
