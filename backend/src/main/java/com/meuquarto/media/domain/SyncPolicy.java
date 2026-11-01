package com.meuquarto.media.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The timing rules of the sync, in plain Java with the clock passed in, so they are tested without
 * Spring and without waiting: the manual cooldown (BR-03), when an account is stale enough for the
 * scheduler (BR-03) and the backoff after failures (ADR 0007).
 */
public final class SyncPolicy {

    /** BR-03: a linked account cannot be synced again within 15 minutes of the previous sync start. */
    public static final Duration MANUAL_COOLDOWN = Duration.ofMinutes(15);
    /** BR-03: the scheduler picks accounts whose last successful sync is older than 6 hours. */
    public static final Duration STALE_AFTER = Duration.ofHours(6);
    /** First step of the backoff; it doubles with every consecutive failure. */
    public static final Duration BASE_BACKOFF = Duration.ofMinutes(15);
    public static final Duration MAX_BACKOFF = Duration.ofHours(24);

    private SyncPolicy() {
    }

    /**
     * BR-03: how long the owner still has to wait to sync by hand, counted from the start of the last
     * sync; empty when the sync is allowed now (never synced, or {@code MANUAL_COOLDOWN} has passed).
     */
    public static Optional<Duration> cooldownRemaining(Instant lastSyncStartedAt, Instant now) {
        if (lastSyncStartedAt == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(now, lastSyncStartedAt.plus(MANUAL_COOLDOWN));
        return remaining.isPositive() ? Optional.of(remaining) : Optional.empty();
    }

    /** When the sync button works again; null when the account was never synced. */
    public static Instant nextManualSyncAt(Instant lastSyncStartedAt) {
        return lastSyncStartedAt == null ? null : lastSyncStartedAt.plus(MANUAL_COOLDOWN);
    }

    /** The scheduler's test: never synced, or the last success is {@code STALE_AFTER} old or older. */
    public static boolean isStale(Instant lastSyncedAt, Instant now) {
        return lastSyncedAt == null || !lastSyncedAt.plus(STALE_AFTER).isAfter(now);
    }

    /**
     * Wait after {@code consecutiveFailures} failed runs in a row: 15 min times 2 to the number of
     * failures (30 min, 1 h, 2 h...), never more than {@code MAX_BACKOFF}. Zero failures: no wait.
     */
    public static Duration backoff(int consecutiveFailures) {
        if (consecutiveFailures <= 0) {
            return Duration.ZERO;
        }
        int doublings = Math.min(consecutiveFailures, 16); // far above the cap already: no overflow
        Duration wait = BASE_BACKOFF.multipliedBy(1L << doublings);
        return wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait;
    }
}
