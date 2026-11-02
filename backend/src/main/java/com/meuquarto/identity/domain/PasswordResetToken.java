package com.meuquarto.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * A reset link that was sent: the hash of its token, who it is for, and when it stops working.
 * Valid for 30 minutes and for a single use (FR-01a, ADR 0012).
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    public static final Duration LIFETIME = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordResetToken() {
        // for JPA
    }

    public PasswordResetToken(long userId, ResetToken token, Instant now) {
        this.userId = userId;
        this.tokenHash = token.hash();
        this.createdAt = now;
        this.expiresAt = now.plus(LIFETIME);
    }

    public Long id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    /** Not spent and not expired at {@code now}. */
    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
