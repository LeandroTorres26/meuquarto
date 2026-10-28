package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;
import java.time.Duration;
import java.util.Optional;

/** BR-03: the account was synced too recently; the owner must wait {@link #retryAfter()}. Answers 429. */
public final class SyncCooldownException extends DomainException {

    private final Duration retryAfter;

    public SyncCooldownException(Duration retryAfter) {
        super("SYNC_COOLDOWN", ErrorKind.TOO_MANY);
        this.retryAfter = retryAfter;
    }

    @Override
    public Optional<Duration> retryAfter() {
        return Optional.of(retryAfter);
    }
}
