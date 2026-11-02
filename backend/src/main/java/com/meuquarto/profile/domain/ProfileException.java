package com.meuquarto.profile.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

/** A rule of the profile editor was broken (FR-05); {@link #problem()} is also the API error code. */
public final class ProfileException extends DomainException {

    public enum Problem {
        /** The display name is empty after trimming. */
        DISPLAY_NAME_REQUIRED(ErrorKind.INVALID),
        DISPLAY_NAME_TOO_LONG(ErrorKind.INVALID),
        /** Control characters (line breaks, tabs...) do not belong in a name. */
        DISPLAY_NAME_INVALID(ErrorKind.INVALID),
        BIO_TOO_LONG(ErrorKind.INVALID),
        /** Control characters other than a line break do not belong in a bio. */
        BIO_INVALID(ErrorKind.INVALID),
        /** There is no theme with that id. */
        THEME_UNKNOWN(ErrorKind.INVALID),
        /** The theme exists but the user's plan does not include it (BR-05). */
        THEME_LOCKED(ErrorKind.INVALID),
        /** The user has no profile (should not happen for a signed-in user). */
        PROFILE_NOT_FOUND(ErrorKind.NOT_FOUND);

        private final ErrorKind kind;

        Problem(ErrorKind kind) {
            this.kind = kind;
        }
    }

    private final Problem problem;

    public ProfileException(Problem problem) {
        super(problem.name(), problem.kind);
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}
