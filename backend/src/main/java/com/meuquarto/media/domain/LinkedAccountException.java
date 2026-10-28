package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

/** A linked-account rule was broken; {@link #problem()} is also the API error code. */
public final class LinkedAccountException extends DomainException {

    public enum Problem {
        /** The username does not fit the format of the platform. */
        INVALID_USERNAME(ErrorKind.INVALID),
        /** The platform answered and there is no such public account. */
        ACCOUNT_NOT_FOUND_ON_PROVIDER(ErrorKind.INVALID),
        /** No provider is registered for the platform, or it could not answer now. */
        PROVIDER_UNAVAILABLE(ErrorKind.UNAVAILABLE),
        /** The profile already has an account on this platform. */
        ALREADY_LINKED(ErrorKind.CONFLICT),
        /** The account does not exist or belongs to someone else (the two answers are the same on purpose). */
        LINKED_ACCOUNT_NOT_FOUND(ErrorKind.NOT_FOUND);

        private final ErrorKind kind;

        Problem(ErrorKind kind) {
            this.kind = kind;
        }
    }

    private final Problem problem;

    public LinkedAccountException(Problem problem) {
        super(problem.name(), problem.kind);
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}
