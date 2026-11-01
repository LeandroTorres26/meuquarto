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

/** One sync attempt of a linked account, kept for observability (ADR 0007). The error is a code, never provider text. */
@Entity
@Table(name = "sync_runs")
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "linked_account_id", nullable = false, updatable = false)
    private long linkedAccountId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false, updatable = false)
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private SyncStatus status;

    @Column(name = "items_count", nullable = false, updatable = false)
    private int itemsCount;

    @Column(length = 64, updatable = false)
    private String error;

    protected SyncRun() {
        // for JPA
    }

    public SyncRun(long linkedAccountId, Instant startedAt, Instant finishedAt, SyncOutcome outcome) {
        this.linkedAccountId = linkedAccountId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = outcome.status();
        this.itemsCount = outcome.itemsCount();
        this.error = outcome.error();
    }

    public Long id() {
        return id;
    }

    public long linkedAccountId() {
        return linkedAccountId;
    }

    public SyncStatus status() {
        return status;
    }

    public int itemsCount() {
        return itemsCount;
    }

    public String error() {
        return error;
    }
}
