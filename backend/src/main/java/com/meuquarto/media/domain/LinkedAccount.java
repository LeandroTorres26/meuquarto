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
 * An external account linked to a profile by its public username (FR-12, ADR 0004). Only the
 * username is stored, never a token. A profile has at most one account per platform.
 */
@Entity
@Table(name = "linked_accounts")
public class LinkedAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private long profileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private ProviderId provider;

    @Column(nullable = false, updatable = false, length = 64)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LinkStatus status;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_error", length = 200)
    private String lastError;

    @Column(name = "last_sync_started_at")
    private Instant lastSyncStartedAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LinkedAccount() {
        // for JPA
    }

    private LinkedAccount(long profileId, ProviderId provider, String username, Instant now) {
        this.profileId = profileId;
        this.provider = provider;
        this.username = username;
        this.status = LinkStatus.ACTIVE;
        this.createdAt = now;
    }

    /** {@code username} must already be normalized and validated by the provider. */
    public static LinkedAccount link(long profileId, ProviderId provider, String username, Instant now) {
        if (provider == null || username == null || username.isBlank()) {
            throw new IllegalArgumentException("A linked account needs a provider and a username");
        }
        return new LinkedAccount(profileId, provider, username, now);
    }

    public Long id() {
        return id;
    }

    public long profileId() {
        return profileId;
    }

    public ProviderId provider() {
        return provider;
    }

    public String username() {
        return username;
    }

    public LinkStatus status() {
        return status;
    }

    public Instant lastSyncedAt() {
        return lastSyncedAt;
    }

    public String lastError() {
        return lastError;
    }

    /** When the last sync began (manual or scheduled); the BR-03 cooldown counts from here. */
    public Instant lastSyncStartedAt() {
        return lastSyncStartedAt;
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    /** The scheduler leaves the account alone until this moment (backoff); null when it is not backing off. */
    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    /** BR-03: when the owner can sync by hand again; null if it was never synced. */
    public Instant nextManualSyncAt() {
        return SyncPolicy.nextManualSyncAt(lastSyncStartedAt);
    }

    /** A run worked: the account is healthy again and the failure streak ends. */
    public void syncSucceeded(Instant now) {
        lastSyncedAt = now;
        status = LinkStatus.ACTIVE;
        lastError = null;
        consecutiveFailures = 0;
        nextAttemptAt = null;
    }

    /** A run failed: only our error code is kept, and the next scheduled attempt moves back (ADR 0007). */
    public void syncFailed(String errorCode, Instant now) {
        status = LinkStatus.ERROR;
        lastError = errorCode;
        consecutiveFailures++;
        nextAttemptAt = now.plus(SyncPolicy.backoff(consecutiveFailures));
    }

    /** A run had nothing to do (no provider): try again after the normal stale period, without marking an error. */
    public void syncSkipped(Instant now) {
        nextAttemptAt = now.plus(SyncPolicy.STALE_AFTER);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
