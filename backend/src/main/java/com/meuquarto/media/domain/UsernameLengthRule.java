package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.LengthRule;

/** Username length in characters (all allowed characters are ASCII, so chars and code points agree). */
final class UsernameLengthRule extends LengthRule<String, UsernameProblem> {

    UsernameLengthRule(int min, int max) {
        super(min, max);
    }

    @Override
    protected int lengthOf(String value) {
        return value.codePointCount(0, value.length());
    }

    @Override
    protected UsernameProblem tooShort() {
        return UsernameProblem.TOO_SHORT;
    }

    @Override
    protected UsernameProblem tooLong() {
        return UsernameProblem.TOO_LONG;
    }
}
