package com.meuquarto.profile.domain;

public class InvalidSlugException extends IllegalArgumentException {

    private final Slug.Problem problem;

    public InvalidSlugException(Slug.Problem problem) {
        super("Invalid slug: " + problem);
        this.problem = problem;
    }

    public Slug.Problem problem() {
        return problem;
    }
}
