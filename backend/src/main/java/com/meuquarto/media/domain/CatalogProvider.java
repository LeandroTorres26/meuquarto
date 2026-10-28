package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.MediaCategory;
import java.util.List;
import java.util.Optional;

/**
 * A catalog of works (TMDB, AniList, Open Library...), seen only through this contract (ADR 0018,
 * hierarchy 2). Callers hold a {@code CatalogProvider} and never ask which class it really is.
 * Implementations never throw for provider failures: they answer "nothing" and stay quiet.
 */
public interface CatalogProvider {

    /** Canonical catalog name stored in {@code media_items.canonical_provider}, e.g. {@code TMDB}. */
    String id();

    /** Whether this catalog is the canonical one for the category. */
    boolean serves(MediaCategory category);

    /** False when the provider cannot work now (for instance, no API key configured). */
    boolean isAvailable();

    /** Normalized results for a text search; empty when unavailable, failing or without matches. */
    List<CatalogEntry> search(MediaCategory category, String query);

    /** One work by its id in this catalog; empty when unavailable, failing or unknown. */
    Optional<CatalogEntry> fetch(MediaCategory category, String externalId);
}
