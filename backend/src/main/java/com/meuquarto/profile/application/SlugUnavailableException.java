package com.meuquarto.profile.application;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

/** The slug asked for at sign-up cannot be used; the code is {@code SLUG_<reason>}. */
public final class SlugUnavailableException extends DomainException {

    private final SlugAvailability.Reason reason;

    public SlugUnavailableException(SlugAvailability.Reason reason) {
        super("SLUG_" + reason.name(),
                reason == SlugAvailability.Reason.TAKEN ? ErrorKind.CONFLICT : ErrorKind.INVALID);
        this.reason = reason;
    }

    public SlugAvailability.Reason reason() {
        return reason;
    }
}
