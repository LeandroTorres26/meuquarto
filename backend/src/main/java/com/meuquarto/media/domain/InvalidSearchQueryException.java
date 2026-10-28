package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

public final class InvalidSearchQueryException extends DomainException {

    public InvalidSearchQueryException() {
        super("INVALID_QUERY", ErrorKind.INVALID);
    }
}
