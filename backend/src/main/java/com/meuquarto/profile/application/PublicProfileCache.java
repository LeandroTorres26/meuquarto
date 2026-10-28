package com.meuquarto.profile.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meuquarto.profile.domain.Profile;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Read model cache of public profiles (NFR-02), keyed by slug. Two ways out: a short TTL, and an
 * explicit eviction by every use case that changes what a profile shows. Only found profiles are
 * cached, so a slug that is claimed later shows up at once.
 */
@Component
public class PublicProfileCache {

    private static final long MAX_PROFILES = 2_000;

    private final Cache<String, PublicProfile> cache;

    public PublicProfileCache(@Value("${meuquarto.public-profile.cache-ttl:60s}") Duration ttl) {
        this.cache = Caffeine.newBuilder().maximumSize(MAX_PROFILES).expireAfterWrite(ttl).build();
    }

    /** The cached model, or the one {@code loader} builds (once, even with concurrent readers). */
    PublicProfile get(String slug, Supplier<PublicProfile> loader) {
        return cache.get(slug, key -> loader.get());
    }

    /**
     * Drops the profile's model once the surrounding transaction commits (right away when there is none).
     * Evicting before the commit would let a reader cache the old data again.
     */
    void evictAfterCommit(Profile profile) {
        String slug = profile.slug();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.invalidate(slug);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.invalidate(slug);
            }
        });
    }
}
