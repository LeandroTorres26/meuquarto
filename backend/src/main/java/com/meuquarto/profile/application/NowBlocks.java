package com.meuquarto.profile.application;

import com.meuquarto.media.application.MediaSummaries;
import com.meuquarto.profile.application.PublicProfile.NowItem;
import com.meuquarto.profile.domain.CollectionItem;
import com.meuquarto.profile.domain.NowBlock;
import com.meuquarto.profile.domain.NowBlock.Shown;
import com.meuquarto.profile.domain.NowEntry;
import com.meuquarto.profile.domain.NowMode;
import com.meuquarto.profile.domain.NowSource;
import com.meuquarto.profile.infra.CollectionItemRepository;
import com.meuquarto.shared.domain.MediaCategory;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Works out what "Agora" shows per category (BR-06) from local data: the owner's choices
 * ({@link NowEntry}), the pinned items and the latest synced ones. The decision itself is {@link NowBlock};
 * this class only loads what it needs and drops anything the public may not see (hidden, adult).
 * Used by the public profile and by the owner's S10, so both always agree.
 */
@Component
class NowBlocks {

    /** The latest synced items looked at per category; adult works never get in, so the first is almost always the one. */
    private static final int CANDIDATES_PER_CATEGORY = 10;

    private final CollectionItemRepository items;
    private final MediaSummaries summaries;

    NowBlocks(CollectionItemRepository items, MediaSummaries summaries) {
        this.items = items;
        this.summaries = summaries;
    }

    /** The item shown for each category that has one; a hidden or empty category is simply absent. */
    Map<MediaCategory, NowItem> resolve(long profileId, List<NowEntry> storedEntries) {
        Map<MediaCategory, NowEntry> entries = new EnumMap<>(MediaCategory.class);
        storedEntries.forEach(entry -> entries.put(entry.category(), entry));

        Map<Long, CollectionItem> known = new HashMap<>();
        List<Long> pinnedIds = storedEntries.stream().filter(entry -> entry.mode() == NowMode.PINNED)
                .map(NowEntry::collectionItemId).toList();
        items.findAllById(pinnedIds).stream().filter(item -> item.profileId() == profileId && !item.hidden())
                .forEach(item -> known.put(item.id(), item));

        Map<MediaCategory, List<CollectionItem>> candidates = new EnumMap<>(MediaCategory.class);
        for (MediaCategory category : MediaCategory.values()) {
            NowEntry entry = entries.get(category);
            boolean hidden = entry != null && entry.mode() == NowMode.HIDDEN;
            if (!hidden && NowBlock.hasAutomaticSource(category)) {
                List<CollectionItem> found = items.findVisibleSynced(profileId, category,
                        PageRequest.of(0, CANDIDATES_PER_CATEGORY));
                candidates.put(category, found);
                found.forEach(item -> known.put(item.id(), item));
            }
        }

        // BR-08: the media module drops adult works.
        Set<Long> allowedMedia = new HashSet<>(summaries.publicIds(
                known.values().stream().map(CollectionItem::mediaItemId).distinct().toList(), "", null));

        Map<MediaCategory, Shown> shown = new EnumMap<>(MediaCategory.class);
        for (MediaCategory category : MediaCategory.values()) {
            NowEntry entry = entries.get(category);
            CollectionItem pinned = entry != null && entry.mode() == NowMode.PINNED
                    ? known.get(entry.collectionItemId()) : null;
            boolean pinnedShowable = pinned != null && allowedMedia.contains(pinned.mediaItemId());
            Optional<Long> latest = candidates.getOrDefault(category, List.of()).stream()
                    .filter(item -> allowedMedia.contains(item.mediaItemId()))
                    .map(CollectionItem::id).findFirst();
            NowBlock.shown(category, entry, pinnedShowable, latest).ifPresent(s -> shown.put(category, s));
        }

        Map<Long, MediaSummaries.Summary> media = summaries.byId(shown.values().stream()
                .map(s -> known.get(s.collectionItemId()).mediaItemId()).toList());
        Map<MediaCategory, NowItem> result = new EnumMap<>(MediaCategory.class);
        for (Map.Entry<MediaCategory, Shown> found : shown.entrySet()) {
            MediaCategory category = found.getKey();
            Shown s = found.getValue();
            CollectionItem item = known.get(s.collectionItemId());
            result.put(category, nowItem(item, s.source(), media.get(item.mediaItemId())));
        }
        return result;
    }

    private static NowItem nowItem(CollectionItem item, NowSource source, MediaSummaries.Summary media) {
        if (source == NowSource.MANUAL) {
            return new NowItem(item.id(), item.category(), media.title(), media.year(), media.imageUrl(),
                    NowSource.MANUAL.name(), null);
        }
        return new NowItem(item.id(), item.category(), media.title(), media.year(), media.imageUrl(),
                item.syncedProvider().orElse(NowSource.AUTO.name()), item.lastActivityAt());
    }
}
