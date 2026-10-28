package com.meuquarto.media.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** BR-03 (cooldown and staleness) and the backoff of ADR 0007, with a fixed clock and no Spring. */
class SyncPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void br03_anAccountNeverSyncedCanBeSyncedNow() {
        assertThat(SyncPolicy.cooldownRemaining(null, NOW)).isEmpty();
        assertThat(SyncPolicy.nextManualSyncAt(null)).isNull();
    }

    @Test
    void br03_aSyncJustStartedMakesTheOwnerWaitTheWholeCooldown() {
        assertThat(SyncPolicy.cooldownRemaining(NOW, NOW)).contains(Duration.ofMinutes(15));
    }

    @Test
    void br03_oneSecondBeforeTheCooldownEndsTheOwnerStillWaits() {
        Instant started = NOW.minus(Duration.ofMinutes(15)).plusSeconds(1);

        assertThat(SyncPolicy.cooldownRemaining(started, NOW)).contains(Duration.ofSeconds(1));
    }

    @Test
    void br03_exactlyFifteenMinutesAfterTheStartTheSyncIsAllowed() {
        Instant started = NOW.minus(Duration.ofMinutes(15));

        assertThat(SyncPolicy.cooldownRemaining(started, NOW)).isEmpty();
    }

    @Test
    void br03_afterTheCooldownTheSyncIsAllowed() {
        assertThat(SyncPolicy.cooldownRemaining(NOW.minus(Duration.ofHours(1)), NOW)).isEmpty();
    }

    @Test
    void br03_theNextManualSyncIsFifteenMinutesAfterTheLastStart() {
        assertThat(SyncPolicy.nextManualSyncAt(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void br03_theSchedulerTakesNeverSyncedAccountsAndThoseOlderThanSixHours() {
        assertThat(SyncPolicy.isStale(null, NOW)).isTrue();
        assertThat(SyncPolicy.isStale(NOW.minus(Duration.ofHours(6)), NOW)).isTrue();
        assertThat(SyncPolicy.isStale(NOW.minus(Duration.ofHours(7)), NOW)).isTrue();
        assertThat(SyncPolicy.isStale(NOW.minus(Duration.ofHours(6)).plusSeconds(1), NOW)).isFalse();
        assertThat(SyncPolicy.isStale(NOW, NOW)).isFalse();
    }

    @Test
    void backoffDoublesFromThirtyMinutesWithEveryFailure() {
        assertThat(SyncPolicy.backoff(0)).isEqualTo(Duration.ZERO);
        assertThat(SyncPolicy.backoff(1)).isEqualTo(Duration.ofMinutes(30));
        assertThat(SyncPolicy.backoff(2)).isEqualTo(Duration.ofHours(1));
        assertThat(SyncPolicy.backoff(3)).isEqualTo(Duration.ofHours(2));
        assertThat(SyncPolicy.backoff(4)).isEqualTo(Duration.ofHours(4));
        assertThat(SyncPolicy.backoff(5)).isEqualTo(Duration.ofHours(8));
        assertThat(SyncPolicy.backoff(6)).isEqualTo(Duration.ofHours(16));
    }

    @Test
    void backoffNeverPassesTwentyFourHoursEvenForAbsurdStreaks() {
        assertThat(SyncPolicy.backoff(7)).isEqualTo(Duration.ofHours(24));
        assertThat(SyncPolicy.backoff(50)).isEqualTo(Duration.ofHours(24));
        assertThat(SyncPolicy.backoff(Integer.MAX_VALUE)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void aFailedRunMovesTheNextAttemptBackAndASuccessClearsIt() {
        LinkedAccount account = LinkedAccount.link(1, ProviderId.LETTERBOXD, "leandro", NOW);

        account.syncFailed("PROVIDER_UNAVAILABLE", NOW);
        account.syncFailed("PROVIDER_UNAVAILABLE", NOW);

        assertThat(account.status()).isEqualTo(LinkStatus.ERROR);
        assertThat(account.lastError()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(account.consecutiveFailures()).isEqualTo(2);
        assertThat(account.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));

        account.syncSucceeded(NOW);

        assertThat(account.status()).isEqualTo(LinkStatus.ACTIVE);
        assertThat(account.lastError()).isNull();
        assertThat(account.consecutiveFailures()).isZero();
        assertThat(account.nextAttemptAt()).isNull();
        assertThat(account.lastSyncedAt()).isEqualTo(NOW);
    }

    @Test
    void theCooldownErrorCarriesTheWaitAndAStableCode() {
        SyncCooldownException error = new SyncCooldownException(Duration.ofMinutes(3));

        assertThat(error.code()).isEqualTo("SYNC_COOLDOWN");
        assertThat(error.retryAfter()).contains(Duration.ofMinutes(3));
    }
}
