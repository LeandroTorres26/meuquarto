package com.meuquarto.identity.application;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;
import java.time.Duration;
import java.util.Optional;

/** Sign-in or a sensitive action was throttled; the client must wait {@link #retryAfter()}. */
public final class TooManyAttemptsException extends DomainException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("TOO_MANY_ATTEMPTS", ErrorKind.TOO_MANY);
        this.retryAfter = retryAfter;
    }

    @Override
    public Optional<Duration> retryAfter() {
        return Optional.of(retryAfter);
    }
}
