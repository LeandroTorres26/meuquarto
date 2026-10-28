package com.meuquarto.shared.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * Base class of every business error in the project (ADR 0018). It is abstract because a
 * "generic domain error" makes no sense alone: you always throw a specific one.
 * <p>
 * The code and the kind come from the constructor, so each subclass calls {@code super(code, kind)}.
 * This reads better than an abstract {@code code()} method: the subclass states its values once, in
 * one place, and cannot forget to override anything.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final ErrorKind kind;

    protected DomainException(String code, ErrorKind kind) {
        super(code);
        this.code = code;
        this.kind = kind;
    }

    /** Stable code the web app turns into a message, e.g. {@code EMAIL_TAKEN}. */
    public String code() {
        return code;
    }

    public ErrorKind kind() {
        return kind;
    }

    /** How long the caller should wait. Empty for most errors; throttling errors override it. */
    public Optional<Duration> retryAfter() {
        return Optional.empty();
    }
}
