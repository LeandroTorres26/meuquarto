package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.ProfileException.Problem;
import com.meuquarto.shared.domain.LengthRule;

/** Display name length: 1 to 40 characters, counted by code points so an emoji counts as one. */
final class DisplayNameLengthRule extends LengthRule<String, Problem> {

    DisplayNameLengthRule() {
        super(DisplayName.MIN_LENGTH, DisplayName.MAX_LENGTH);
    }

    @Override
    protected int lengthOf(String value) {
        return value.codePointCount(0, value.length());
    }

    @Override
    protected Problem tooShort() {
        return Problem.DISPLAY_NAME_REQUIRED;
    }

    @Override
    protected Problem tooLong() {
        return Problem.DISPLAY_NAME_TOO_LONG;
    }
}
