package com.meuquarto.profile.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

/** A collection rule was broken; {@link #problem()} says which one, and it is also the API error code. */
public final class CollectionException extends DomainException {

    public enum Problem {
        /** The item does not exist or belongs to someone else (the two answers are the same on purpose). */
        ITEM_NOT_FOUND(ErrorKind.NOT_FOUND),
        /** FR-09: an item that also came from a sync cannot be removed, only hidden. */
        CANNOT_REMOVE_SYNCED(ErrorKind.CONFLICT),
        /** The category has no catalog now, or the catalog could not return the work. */
        CATALOG_UNAVAILABLE(ErrorKind.UNAVAILABLE),
        /** BR-08: adult-flagged works are not accepted in P2. */
        ADULT_NOT_ALLOWED(ErrorKind.INVALID),
        NOTE_TOO_LONG(ErrorKind.INVALID),
        NOTE_INVALID(ErrorKind.INVALID),
        INVALID_SORT(ErrorKind.INVALID),
        /** A change request that changes nothing. */
        EMPTY_UPDATE(ErrorKind.INVALID);

        private final ErrorKind kind;

        Problem(ErrorKind kind) {
            this.kind = kind;
        }
    }

    private final Problem problem;

    public CollectionException(Problem problem) {
        super(problem.name(), problem.kind);
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}
