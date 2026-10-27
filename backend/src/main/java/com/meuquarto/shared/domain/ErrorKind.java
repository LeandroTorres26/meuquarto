package com.meuquarto.shared.domain;

/**
 * Our own, framework-free classification of a business error. The api layer decides which HTTP
 * status each kind becomes, so the domain never needs to know about HTTP.
 */
public enum ErrorKind {
    /** The input broke a rule (a bad e-mail, a weak password, a reserved slug). */
    INVALID,
    /** The input is fine but clashes with existing data (e-mail or slug already taken). */
    CONFLICT,
    /** Too many attempts in a short time; the caller must wait. */
    TOO_MANY
}
