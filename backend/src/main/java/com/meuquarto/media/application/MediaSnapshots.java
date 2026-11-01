package com.meuquarto.media.application;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.media.domain.MediaItem;
import com.meuquarto.media.domain.MetadataSource;
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

    /** A catalog writing its own entry (manual add): canonical, so it inserts or refreshes the row. */
    @Transactional
    public Snapshot snapshot(CatalogEntry entry) {
        return snapshot(entry, MetadataSource.catalog(entry));
    }

    /**
     * Inserts the entry, or updates the row with the same canonical key according to who writes (BR-04):
     * the canonical catalog refreshes title, year and image; any other source only fills the nulls of an
     * existing row. The returned snapshot shows what is stored, not what was offered.
     */
    @Transactional
    public Snapshot snapshot(CatalogEntry entry, MetadataSource source) {
        items.upsert(entry.category().name(), entry.provider(), entry.externalId(), entry.title(), entry.year(),
                entry.imageUrl(), entry.adult(), clock.instant(), source.name(), source.overwrites(entry));
        MediaItem item = items.findByCategoryAndCanonicalProviderAndExternalId(
                entry.category(), entry.provider(), entry.externalId()).orElseThrow();
        return new Snapshot(item.id(), entry.category(), entry.provider(), entry.externalId(), item.title(),
                item.year(), item.imageUrl(), item.adult());
    }

    public record Snapshot(long id, MediaCategory category, String provider, String externalId, String title,
            Integer year, String imageUrl, boolean adult) {}
}
