package com.meuquarto.profile.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

/** An "Agora" rule was broken; {@link #problem()} says which one, and it is also the API error code. */
public final class NowException extends DomainException {

    public enum Problem {
        /** The item is not in the owner's collection (missing or someone else's: same answer on purpose). */
        NOT_IN_COLLECTION(ErrorKind.NOT_FOUND),
        /** A hidden item is out of the visible collection, so it cannot be pinned. */
        ITEM_HIDDEN(ErrorKind.CONFLICT),
        /** An "Agora" slot only takes items of its own category. */
        CATEGORY_MISMATCH(ErrorKind.INVALID),
        /** Pinning needs the item to pin. */
        ITEM_REQUIRED(ErrorKind.INVALID),
        /** Only pinning takes an item. */
        UNEXPECTED_ITEM(ErrorKind.INVALID);

        private final ErrorKind kind;

        Problem(ErrorKind kind) {
            this.kind = kind;
        }
    }

    private final Problem problem;

    public NowException(Problem problem) {
        super(problem.name(), problem.kind);
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}
