package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.MediaCategory;

/**
 * A work as a catalog describes it, already normalized: every provider answers with this same type,
 * so the rest of the code never sees a provider's JSON. {@code provider} is the canonical catalog
 * name (ADR 0005) and {@code externalId} the id inside it; {@code year} and {@code imageUrl} may be null.
 */
public record CatalogEntry(
        MediaCategory category,
        String provider,
        String externalId,
        String title,
        Integer year,
        String imageUrl,
        boolean adult) {

    /** The identity of this work in our database (BR-04). */
    public MediaKey key() {
        return new MediaKey(category, provider, externalId);
    }
}
