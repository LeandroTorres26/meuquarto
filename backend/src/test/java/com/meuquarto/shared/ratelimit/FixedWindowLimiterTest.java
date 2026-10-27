package com.meuquarto.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FixedWindowLimiterTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final FixedWindowLimiter limiter = new FixedWindowLimiter(clock);

    @Test
    void allowsUpToTheLimitThenReportsHowLongToWait() {
        Duration window = Duration.ofMinutes(1);
        assertThat(limiter.hit("k", 2, window)).isEmpty();
        assertThat(limiter.hit("k", 2, window)).isEmpty();
        clock.now = clock.now.plusSeconds(20);
        assertThat(limiter.hit("k", 2, window)).contains(Duration.ofSeconds(40));
    }

    @Test
    void startsANewWindowAfterItEnds() {
        Duration window = Duration.ofMinutes(1);
        limiter.hit("k", 1, window);
        assertThat(limiter.hit("k", 1, window)).isPresent();
        clock.now = clock.now.plus(window);
        assertThat(limiter.hit("k", 1, window)).isEmpty();
    }

    @Test
    void keysAreIndependentAndExpiredWindowsAreSwept() {
        Duration window = Duration.ofMinutes(1);
        limiter.hit("a", 1, window);
        assertThat(limiter.hit("b", 1, window)).isEmpty();
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        limiter.hit("c", 1, window);
        assertThat(limiter.size()).isEqualTo(1);
    }
}
