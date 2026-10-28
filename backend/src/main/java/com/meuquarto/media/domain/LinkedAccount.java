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

    public Instant createdAt() {
        return createdAt;
    }
}
