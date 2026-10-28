package com.meuquarto.media.domain;

import java.util.List;
import java.util.Optional;

/**
 * Holds every catalog and finds the one for a category by asking each of them ({@code serves}),
 * the same loop as the PetShop: no {@code instanceof}, no {@code switch} on the provider class.
 * Adding a catalog is a new class; this one does not change.
 */
public final class CatalogRegistry {

    private final List<CatalogProvider> providers;

    public CatalogRegistry(List<? extends CatalogProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public Optional<CatalogProvider> forCategory(MediaCategory category) {
        for (CatalogProvider provider : providers) {
            if (provider.serves(category)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
