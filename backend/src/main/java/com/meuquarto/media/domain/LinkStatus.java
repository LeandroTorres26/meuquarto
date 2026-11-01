package com.meuquarto.media.domain;

/** Health of a linked account as the sync last saw it (FR-14). */
public enum LinkStatus {
    /** Linked, and the last sync (if any) worked. */
    ACTIVE,
    /** The last sync failed; {@code lastError} says why. */
    ERROR
}
