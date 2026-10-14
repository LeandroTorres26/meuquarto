package com.meuquarto.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** A sign-in e-mail: trimmed, lowercased, at most 254 characters, one {@code @} and a dotted domain. */
public record EmailAddress(String value) {

    public static final int MAX_LENGTH = 254;
    private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+");

    public EmailAddress {
        if (value == null || value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()
                || !value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new AccountRuleException(AccountRuleException.Rule.INVALID_EMAIL);
        }
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw == null ? null : raw.strip().toLowerCase(Locale.ROOT));
    }

    /** Part before the {@code @}, used to refuse passwords equal to it. */
    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }

    @Override
    public String toString() {
        return value;
    }
}
