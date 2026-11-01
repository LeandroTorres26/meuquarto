package com.meuquarto.media.domain;

/** Answer of {@code ActivityProvider.checkAccountExists}: a provider failure is a value, not an exception. */
public enum AccountCheck {
    /** The account exists and its activity is public. */
    EXISTS,
    /** The platform says there is no such public account. */
    NOT_FOUND,
    /** The platform could not answer now (timeout, error, unreadable response). */
    UNAVAILABLE
}
