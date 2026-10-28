package com.meuquarto.media.domain;

/**
 * Canonical key of a media item (BR-04): category + canonical catalog + id in that catalog. Two works
 * with the same key are the same item, whatever source they came from. As a record, equality is by value.
 */
public record MediaKey(MediaCategory category, String provider, String externalId) {

    public MediaKey {
        if (category == null || provider == null || provider.isBlank() || externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("A media key needs category, provider and external id");
        }
    }
}
