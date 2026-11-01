package com.meuquarto.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/**
 * Why an item is in a collection: added by hand ({@link #MANUAL}) or seen by a sync of one provider.
 * A value object: two sources are the same when all fields match.
 */
@Embeddable
public record ItemSource(
        @Column(name = "source", nullable = false, updatable = false, length = 32) String source,
        @Column(name = "linked_account_id", updatable = false) Long linkedAccountId,
        @Column(name = "first_seen_at", nullable = false, updatable = false) Instant firstSeenAt) {

    public static final String MANUAL = "MANUAL";

    public static ItemSource manual(Instant now) {
        return new ItemSource(MANUAL, null, now);
    }

    /** {@code provider} is the platform name, e.g. {@code LETTERBOXD}; it can never be the manual marker. */
    public static ItemSource synced(String provider, Long linkedAccountId, Instant now) {
        if (provider == null || provider.isBlank() || MANUAL.equals(provider)) {
            throw new IllegalArgumentException("A sync source needs a provider name other than " + MANUAL);
        }
        return new ItemSource(provider, linkedAccountId, now);
    }

    public boolean isManual() {
        return MANUAL.equals(source);
    }
}
