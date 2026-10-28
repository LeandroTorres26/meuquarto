package com.meuquarto.profile.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

/** A highlight rule was broken; {@link #problem()} says which one, and it is also the API error code. */
public final class HighlightException extends DomainException {

    public enum Problem {
        /** BR-02: the list already holds as many highlights as the plan allows. A state conflict, not a bad input. */
        HIGHLIGHT_LIMIT_REACHED(ErrorKind.CONFLICT),
        /** The item is not in the owner's collection (missing or someone else's: same answer on purpose). */
        NOT_IN_COLLECTION(ErrorKind.NOT_FOUND),
        /** A hidden item is out of the visible collection, so it cannot be highlighted. */
        ITEM_HIDDEN(ErrorKind.CONFLICT),
        /** A category list only takes items of its category. */
        CATEGORY_MISMATCH(ErrorKind.INVALID),
        /** BR-07: a work appears once per highlight list. */
        ALREADY_HIGHLIGHTED(ErrorKind.CONFLICT),
        /** The highlight does not exist or belongs to someone else. */
        HIGHLIGHT_NOT_FOUND(ErrorKind.NOT_FOUND),
        /** The new order is not exactly the current set of highlights of the list. */
        INVALID_ORDER(ErrorKind.INVALID);

        private final ErrorKind kind;

        Problem(ErrorKind kind) {
            this.kind = kind;
        }
    }

    private final Problem problem;

    public HighlightException(Problem problem) {
        super(problem.name(), problem.kind);
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}
