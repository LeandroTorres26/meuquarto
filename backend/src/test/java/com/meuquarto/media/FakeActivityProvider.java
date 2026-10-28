package com.meuquarto.media;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.ActivitySnapshot;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.media.domain.UsernameProblem;
import com.meuquarto.media.domain.UsernameRules;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A provider for tests, no network: 2 to 15 characters of a-z, 0-9 and underscore, case-insensitive.
 * The account "ghost" does not exist and "down" makes the platform unavailable. Counts the existence
 * checks, so a test can prove that an invalid username never reached the "network".
 * For the sync tests it also answers {@link #fetchActivity} with whatever the test put in {@link #activity}
 * (empty by default), fails for the usernames in {@link #failing} and remembers every fetch.
 */
public final class FakeActivityProvider implements ActivityProvider {

    public final AtomicInteger checks = new AtomicInteger();
    public final Map<String, ActivitySnapshot> activity = new ConcurrentHashMap<>();
    public final java.util.Set<String> failing = ConcurrentHashMap.newKeySet();
    public final List<String> fetched = new CopyOnWriteArrayList<>();

    public void reset() {
        activity.clear();
        failing.clear();
        fetched.clear();
    }

    @Override
    public ProviderId id() {
        return ProviderId.LETTERBOXD;
    }

    @Override
    public Set<MediaCategory> categories() {
        return Set.of(MediaCategory.MOVIE);
    }

    @Override
    public List<ValidationRule<String, UsernameProblem>> usernameRules() {
        return UsernameRules.of(2, 15, "[a-z0-9_]+");
    }

    @Override
    public String normalizeUsername(String raw) {
        return ActivityProvider.super.normalizeUsername(raw).toLowerCase(Locale.ROOT);
    }

    @Override
    public AccountCheck checkAccountExists(String username) {
        checks.incrementAndGet();
        return switch (username) {
            case "ghost" -> AccountCheck.NOT_FOUND;
            case "down" -> AccountCheck.UNAVAILABLE;
            default -> AccountCheck.EXISTS;
        };
    }

    @Override
    public ActivitySnapshot fetchActivity(String username) {
        fetched.add(username);
        if (failing.contains(username)) {
            throw new LinkedAccountException(LinkedAccountException.Problem.PROVIDER_UNAVAILABLE);
        }
        return activity.getOrDefault(username, new ActivitySnapshot(List.of()));
    }
}
