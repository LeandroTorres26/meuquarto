package com.meuquarto.profile.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

/**
 * A public read found nothing to show. {@link #problem()} is also the API error code. Whatever the
 * reason (no such profile, hidden item, someone else's item, adult work) the visitor gets the same answer.
 */
public final class PublicProfileException extends DomainException {

    public enum Problem {
        PROFILE_NOT_FOUND,
        ITEM_NOT_FOUND
    }

    private final Problem problem;

    public PublicProfileException(Problem problem) {
        super(problem.name(), ErrorKind.NOT_FOUND);
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}
