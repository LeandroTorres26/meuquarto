package com.meuquarto.identity.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after {@code maxFailures} failed sign-ins for the same key
 * (e-mail and client address) inside {@code window}, further attempts are refused until the oldest
 * failure leaves the window. A success clears the key. In memory: one API instance (ADR 0007).
 */
public class LoginThrottle {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginThrottle(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** Time to wait before trying again, or empty if an attempt is allowed now. */
    public Optional<Duration> retryAfter(String key) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return Optional.empty();
        }
        synchronized (recent) {
            prune(recent);
            if (recent.size() < maxFailures) {
                return Optional.empty();
            }
            Duration wait = Duration.between(clock.instant(), recent.peekFirst().plus(window));
            return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> recent = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent);
            recent.addLast(clock.instant());
        }
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(window);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.removeFirst();
        }
    }
}
