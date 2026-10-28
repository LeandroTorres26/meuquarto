package com.meuquarto.profile.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

public final class InvalidSlugException extends DomainException {

    private final Slug.Problem problem;

    public InvalidSlugException(Slug.Problem problem) {
        super("SLUG_" + problem.name(), ErrorKind.INVALID);
        this.problem = problem;
    }

    public Slug.Problem problem() {
        return problem;
    }
}
