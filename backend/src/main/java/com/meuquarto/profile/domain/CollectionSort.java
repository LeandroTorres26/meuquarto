package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.CollectionException.Problem;
import java.util.Locale;

/** How a collection listing is ordered (FR-10b): most recent activity, title A to Z, year newest first. */
public enum CollectionSort {
    RECENT, TITLE, YEAR;

    /** Accepts the API spelling ({@code recent}, {@code title}, {@code year}) in any case; blank means recent. */
    public static CollectionSort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return RECENT;
        }
        try {
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new CollectionException(Problem.INVALID_SORT);
        }
    }
}
