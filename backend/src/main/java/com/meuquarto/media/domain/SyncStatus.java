package com.meuquarto.media.domain;

/** How one sync run ended; stored in {@code sync_runs.status}. */
public enum SyncStatus {
    /** The platform answered and the activity was applied to the collection. */
    SUCCESS,
    /** The platform could not answer (or the run failed); nothing changed in the collection. */
    FAILED,
    /** Nothing to do: no provider is registered for the platform right now. */
    SKIPPED
}
