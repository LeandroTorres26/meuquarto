package com.meuquarto.profile.domain;

/** Where the "Agora" item of a category comes from, as stored in {@code now_entries.source}. */
public enum NowSource {
    /** The latest synced item of the category (computed on read). */
    AUTO,
    /** The owner pinned an item from their collection. */
    MANUAL
}
