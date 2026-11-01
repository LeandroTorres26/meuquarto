package com.meuquarto.shared.domain;

import java.util.Optional;

/**
 * Template for "length between min and max" rules. It is abstract because it does not know how to
 * measure a value nor which problem to report: each subclass fills those in. The order of the
 * checks lives here, once (template method).
 */
public abstract class LengthRule<T, P> implements ValidationRule<T, P> {

    private final int min;
    private final int max;

    protected LengthRule(int min, int max) {
        if (min < 0 || max < min) {
            throw new IllegalArgumentException("Invalid length bounds");
        }
        this.min = min;
        this.max = max;
    }

    /** Length of a non-null value. Slugs count chars; passwords count code points. */
    protected abstract int lengthOf(T value);

    protected abstract P tooShort();

    protected abstract P tooLong();

    @Override
    public final Optional<P> check(T value) {
        int length = value == null ? 0 : lengthOf(value);
        if (length < min) {
            return Optional.of(tooShort());
        }
        if (length > max) {
            return Optional.of(tooLong());
        }
        return Optional.empty();
    }
}
