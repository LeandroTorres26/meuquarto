package com.meuquarto.profile.domain;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;

/** An avatar upload was refused, or the avatar cannot be served; {@link #problem()} is also the API error code. */
public final class AvatarException extends DomainException {

    public enum Problem {
        /** No file, or a file with no bytes. */
        AVATAR_EMPTY(ErrorKind.INVALID),
        /** More than {@link AvatarProcessor#MAX_BYTES} bytes. */
        AVATAR_TOO_LARGE(ErrorKind.INVALID),
        /** The content is not a JPEG or a PNG (the file name and the declared type are never trusted). */
        AVATAR_UNSUPPORTED_TYPE(ErrorKind.INVALID),
        /** The picture is wider or taller than {@link AvatarProcessor#MAX_SIDE} pixels. */
        AVATAR_DIMENSIONS_TOO_LARGE(ErrorKind.INVALID),
        /** Truncated, corrupt or in a variant the decoder cannot read. */
        AVATAR_UNREADABLE(ErrorKind.INVALID),
        /** No avatar under that key. */
        AVATAR_NOT_FOUND(ErrorKind.NOT_FOUND),
        /** The file storage cannot be reached right now. */
        STORAGE_UNAVAILABLE(ErrorKind.UNAVAILABLE);

        private final ErrorKind kind;

        Problem(ErrorKind kind) {
            this.kind = kind;
        }
    }

    private final Problem problem;

    public AvatarException(Problem problem) {
        super(problem.name(), problem.kind);
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}
