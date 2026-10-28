package com.meuquarto.media.domain;

import java.time.Instant;

/**
 * One thing the user did on a platform, already normalized: the work (as the canonical catalog names
 * it, BR-04), what was done and when. Every provider answers with this type, so the sync never sees
 * the format of a platform.
 */
public record ActivityEntry(CatalogEntry work, ActivityType type, Instant occurredAt) {

    public ActivityEntry {
        if (work == null || type == null || occurredAt == null) {
            throw new IllegalArgumentException("An activity entry needs a work, a type and a time");
        }
    }
}
