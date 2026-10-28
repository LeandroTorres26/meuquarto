package com.meuquarto.media.application;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.media.domain.MediaCategory;
import com.meuquarto.media.domain.SearchQuery;
import java.util.List;
import org.springframework.stereotype.Service;

/** Searches the canonical catalog of a category (FR-08). Does not persist anything: snapshots happen on add. */
@Service
public class CatalogSearch {

    private final CatalogRegistry registry;

    public CatalogSearch(CatalogRegistry registry) {
        this.registry = registry;
    }

    /** {@code available} is false when no catalog serves the category or the catalog cannot work now. */
    public Result search(MediaCategory category, String rawQuery) {
        SearchQuery query = SearchQuery.of(rawQuery);
        return registry.forCategory(category)
                .filter(CatalogProvider::isAvailable)
                .map(provider -> new Result(true, visible(provider.search(category, query.text()))))
                .orElseGet(() -> new Result(false, List.of()));
    }

    /** BR-08: adult-flagged works are never offered, even if a provider forgot to filter them. */
    private static List<CatalogEntry> visible(List<CatalogEntry> entries) {
        return entries.stream().filter(entry -> !entry.adult()).toList();
    }

    public record Result(boolean available, List<CatalogEntry> results) {}
}
