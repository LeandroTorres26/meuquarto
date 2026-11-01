package com.meuquarto.media.domain;

import java.util.List;
import java.util.Optional;

/**
 * Holds every activity provider and finds one by id by asking each of them, the same loop as the
 * PetShop: no {@code instanceof}, no {@code switch} on the provider class. Adding a platform is a new
 * class and a new bean; this one does not change.
 */
public final class ActivityRegistry {

    private final List<ActivityProvider> providers;

    public ActivityRegistry(List<? extends ActivityProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public Optional<ActivityProvider> find(ProviderId id) {
        for (ActivityProvider provider : providers) {
            if (provider.id() == id) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    /** What the sync iterates (#29): the same call on every provider. */
    public List<ActivityProvider> all() {
        return providers;
    }
}
