package com.meuquarto.media.application;

import com.meuquarto.media.domain.MediaItem;
import com.meuquarto.media.infra.MediaItemRepository;
import com.meuquarto.shared.domain.MediaCategory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of {@code media_items} for other modules: other modules hold only media item ids and ask
 * here for what to show and in which order, so they never touch the media tables or entities.
 */
@Service
public class MediaSummaries {

    /** How a list of media items is ordered when the other module cannot do it itself. */
    public enum Order { TITLE, YEAR }

    private final MediaItemRepository items;

    public MediaSummaries(MediaItemRepository items) {
        this.items = items;
    }

    /** Summaries by media item id; ids that do not exist are simply missing from the map. */
    @Transactional(readOnly = true)
    public Map<Long, Summary> byId(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return items.findAllById(ids).stream().map(MediaSummaries::summary)
                .collect(Collectors.toMap(Summary::id, Function.identity()));
    }

    /** The same ids, ordered: title A to Z, or year newest first (works without a year last). */
    @Transactional(readOnly = true)
    public List<Long> sortedIds(Collection<Long> ids, Order order) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return order == Order.TITLE ? items.idsByTitle(ids) : items.idsByYearDesc(ids);
    }

    private static Summary summary(MediaItem item) {
        return new Summary(item.id(), item.key().category(), item.key().provider(), item.key().externalId(),
                item.title(), item.year(), item.imageUrl());
    }

    public record Summary(long id, MediaCategory category, String provider, String externalId, String title,
            Integer year, String imageUrl) {}
}
