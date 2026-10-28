package com.meuquarto.media.application;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.media.domain.MediaCategory;
import com.meuquarto.media.domain.MediaItem;
import com.meuquarto.media.infra.MediaItemRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves the metadata of a work into {@code media_items} (ADR 0006), one row per canonical key (BR-04).
 * Used when a user adds an item (#23). Metadata comes from the catalog, never from the request, so a
 * client cannot plant its own title or image URL.
 */
@Service
public class MediaSnapshots {

    private final MediaItemRepository items;
    private final CatalogRegistry registry;
    private final Clock clock = Clock.systemUTC();

    public MediaSnapshots(MediaItemRepository items, CatalogRegistry registry) {
        this.items = items;
        this.registry = registry;
    }

    /** Looks the work up in its canonical catalog and snapshots it; empty if the catalog does not know it. */
    @Transactional
    public Optional<Snapshot> snapshotFromCatalog(MediaCategory category, String externalId) {
        return registry.forCategory(category)
                .flatMap(provider -> provider.fetch(category, externalId))
                .map(this::snapshot);
    }

    /** Inserts the entry or refreshes the existing row with the same canonical key. */
    @Transactional
    public Snapshot snapshot(CatalogEntry entry) {
        items.upsert(entry.category().name(), entry.provider(), entry.externalId(), entry.title(), entry.year(),
                entry.imageUrl(), entry.adult(), clock.instant());
        MediaItem item = items.findByCategoryAndCanonicalProviderAndExternalId(
                entry.category(), entry.provider(), entry.externalId()).orElseThrow();
        return new Snapshot(item.id(), entry.category(), entry.provider(), entry.externalId(), item.title(),
                item.year(), item.imageUrl(), item.adult());
    }

    public record Snapshot(long id, MediaCategory category, String provider, String externalId, String title,
            Integer year, String imageUrl, boolean adult) {}
}
