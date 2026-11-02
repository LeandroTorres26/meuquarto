package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.ProfileException.Problem;
import com.meuquarto.shared.domain.LengthRule;

/** Bio length: 0 to 160 characters (the bio is optional), counted by code points. */
final class BioLengthRule extends LengthRule<String, Problem> {

    BioLengthRule() {
        super(0, Bio.MAX_LENGTH);
    }

    @Override
    protected int lengthOf(String value) {
        return value.codePointCount(0, value.length());
    }

    @Override
    protected Problem tooShort() {
        // The minimum is 0, so the template never asks for this.
        throw new IllegalStateException("a bio has no minimum length");
    }

    @Override
    protected Problem tooLong() {
        return Problem.BIO_TOO_LONG;
    }
}
