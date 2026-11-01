package com.meuquarto.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Raw activity read from a platform: this account did {@link #activityType()} with this work at this time.
 * Rows are written by the sync with an insert that ignores duplicates; this class only reads them.
 */
@Entity
@Table(name = "synced_entries")
public class SyncedEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "linked_account_id", nullable = false, updatable = false)
    private long linkedAccountId;

    @Column(name = "media_item_id", nullable = false, updatable = false)
    private long mediaItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, updatable = false, length = 16)
    private ActivityType activityType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected SyncedEntry() {
        // for JPA
    }

    public long linkedAccountId() {
        return linkedAccountId;
    }

    public long mediaItemId() {
        return mediaItemId;
    }

    public ActivityType activityType() {
        return activityType;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
