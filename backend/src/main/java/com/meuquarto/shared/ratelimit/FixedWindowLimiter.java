package com.meuquarto.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory fixed-window counter per key. Small and dependency-free; state is per instance, which is
 * fine for the single API container of the deploy (docs/security.md). Expired windows are swept
 * lazily so the map stays bounded by the number of recently active keys.
 */
public class FixedWindowLimiter {

    private static final long SWEEP_EVERY_MILLIS = 60_000;

    private record Window(long endsAtMillis, int count) {}

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong nextSweepAt = new AtomicLong();

    public FixedWindowLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts one hit for {@code key}. Returns empty when it is within {@code limit} hits per
     * {@code window}, otherwise how long the caller must wait for the window to end.
     */
    public Optional<Duration> hit(String key, int limit, Duration window) {
        long now = clock.millis();
        sweep(now);
        Window updated = windows.compute(key, (k, current) -> {
            if (current == null || current.endsAtMillis() <= now) {
                return new Window(now + window.toMillis(), 1);
            }
            return new Window(current.endsAtMillis(), current.count() + 1);
        });
        if (updated.count() <= limit) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(Math.max(1, updated.endsAtMillis() - now)));
    }

    /** Forgets every counter. Used by tests to stay independent of execution order. */
    public void reset() {
        windows.clear();
    }

    int size() {
        return windows.size();
    }

    private void sweep(long now) {
        long due = nextSweepAt.get();
        if (now >= due && nextSweepAt.compareAndSet(due, now + SWEEP_EVERY_MILLIS)) {
            windows.entrySet().removeIf(e -> e.getValue().endsAtMillis() <= now);
        }
    }
}
