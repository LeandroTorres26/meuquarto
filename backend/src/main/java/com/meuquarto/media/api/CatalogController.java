package com.meuquarto.media.api;

import com.meuquarto.media.application.CatalogSearch;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.shared.domain.MediaCategory;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Signed-in users only (anyRequest is authenticated): catalog search for adding items (FR-08). */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogSearch search;

    public CatalogController(CatalogSearch search) {
        this.search = search;
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam MediaCategory category, @RequestParam("q") String query) {
        CatalogSearch.Result result = search.search(category, query);
        return new SearchResponse(result.available(), result.results().stream().map(ResultItem::of).toList());
    }

    public record SearchResponse(boolean available, List<ResultItem> results) {}

    public record ResultItem(MediaCategory category, String provider, String externalId, String title, Integer year,
            String imageUrl) {

        static ResultItem of(CatalogEntry entry) {
            return new ResultItem(entry.category(), entry.provider(), entry.externalId(), entry.title(), entry.year(),
                    entry.imageUrl());
        }
    }
}
