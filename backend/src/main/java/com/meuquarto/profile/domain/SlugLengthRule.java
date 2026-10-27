package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.Slug.Problem;
import com.meuquarto.shared.domain.LengthRule;

/** Slug length: 3 to 30 characters (BR-01). */
final class SlugLengthRule extends LengthRule<String, Problem> {

    SlugLengthRule() {
        super(Slug.MIN_LENGTH, Slug.MAX_LENGTH);
    }

    @Override
    protected int lengthOf(String value) {
        return value.length();
    }

    @Override
    protected Problem tooShort() {
        return Problem.TOO_SHORT;
    }

    @Override
    protected Problem tooLong() {
        return Problem.TOO_LONG;
    }
}
