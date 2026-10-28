package com.meuquarto.profile.application;

import com.meuquarto.media.application.MediaSnapshots;
import com.meuquarto.media.application.MediaSummaries;
import com.meuquarto.profile.domain.CollectionException;
import com.meuquarto.profile.domain.CollectionException.Problem;
import com.meuquarto.profile.domain.CollectionItem;
import com.meuquarto.profile.domain.CollectionSort;
import com.meuquarto.profile.domain.ItemSource;
import com.meuquarto.profile.domain.Profile;
import com.meuquarto.profile.infra.CollectionItemRepository;
import com.meuquarto.profile.infra.ItemRef;
import com.meuquarto.profile.infra.ProfileRepository;
import com.meuquarto.shared.domain.MediaCategory;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The owner's collection (FR-08 to FR-10). The owner is always identified by the caller from the
 * session; an item id that is not the owner's behaves exactly like one that does not exist.
 * Metadata comes from the media module (snapshot on add, summaries on read), never from the request.
 */
@Service
public class ProfileCollection {

    public static final int MAX_PAGE_SIZE = 50;

    private final ProfileRepository profiles;
    private final CollectionItemRepository items;
    private final MediaSnapshots snapshots;
    private final MediaSummaries summaries;
    private final TransactionTemplate transaction;
    private final Clock clock = Clock.systemUTC();

    public ProfileCollection(ProfileRepository profiles, CollectionItemRepository items, MediaSnapshots snapshots,
            MediaSummaries summaries, PlatformTransactionManager transactionManager) {
        this.profiles = profiles;
        this.items = items;
        this.snapshots = snapshots;
        this.summaries = summaries;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Adds a work by hand. The catalog call (slow, outside our control) happens before the database
     * transaction opens. Adding a work that is already there returns the existing item (BR-07) and,
     * if it was hidden, shows it again.
     */
    public AddResult add(long userId, MediaCategory category, String externalId) {
        long profileId = profileIdOf(userId);
        MediaSnapshots.Snapshot work = snapshots.snapshotFromCatalog(category, externalId)
                .orElseThrow(() -> new CollectionException(Problem.CATALOG_UNAVAILABLE));
        if (work.adult()) {
            throw new CollectionException(Problem.ADULT_NOT_ALLOWED);
        }
        return transaction.execute(status -> {
            profiles.lockById(profileId).orElseThrow(() -> new CollectionException(Problem.ITEM_NOT_FOUND));
            Instant now = clock.instant();
            Optional<CollectionItem> existing = items.findByProfileIdAndMediaItemId(profileId, work.id());
            if (existing.isPresent()) {
                existing.get().addAgainByHand(now);
                return new AddResult(view(items.saveAndFlush(existing.get())), false);
            }
            CollectionItem created = CollectionItem.addedManually(profileId, work.id(), work.category(), now);
            return new AddResult(view(items.saveAndFlush(created)), true);
        });
    }

    /**
     * What a sync calls for each work it finds (BR-10): a new item is created visible; an existing one
     * only gets the source and the activity time. A hidden item stays hidden (FR-09).
     */
    @Transactional
    public void recordSyncedItem(long profileId, MediaCategory category, long mediaItemId, String provider,
            Long linkedAccountId) {
        profiles.lockById(profileId).orElseThrow(() -> new CollectionException(Problem.ITEM_NOT_FOUND));
        Instant now = clock.instant();
        Optional<CollectionItem> existing = items.findByProfileIdAndMediaItemId(profileId, mediaItemId);
        if (existing.isPresent()) {
            existing.get().seenBySync(provider, linkedAccountId, now);
            items.saveAndFlush(existing.get());
        } else {
            items.saveAndFlush(CollectionItem.foundBySync(profileId, mediaItemId, category, provider, linkedAccountId, now));
        }
    }

    /** One page of a category. Hidden items are listed only when the owner asks for them. */
    @Transactional(readOnly = true)
    public Listing list(long userId, MediaCategory category, CollectionSort sort, int page, int size,
            boolean includeHidden) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        long profileId = profileIdOf(userId);
        if (sort == CollectionSort.RECENT) {
            var result = items.findPage(profileId, category, includeHidden,
                    PageRequest.of(page, size, Sort.by(Sort.Order.desc("lastActivityAt"), Sort.Order.desc("id"))));
            return new Listing(views(result.getContent()), page, size, result.getTotalElements());
        }
        // Title and year live in the media module: it orders the ids, we cut the page and load only that page.
        List<ItemRef> refs = items.findRefs(profileId, category, includeHidden);
        Map<Long, Long> itemIdByMediaId = new HashMap<>();
        refs.forEach(ref -> itemIdByMediaId.put(ref.mediaItemId(), ref.itemId()));
        MediaSummaries.Order order = sort == CollectionSort.TITLE ? MediaSummaries.Order.TITLE : MediaSummaries.Order.YEAR;
        List<Long> orderedItemIds = summaries.sortedIds(itemIdByMediaId.keySet(), order).stream()
                .map(itemIdByMediaId::get).toList();
        int from = (int) Math.min((long) page * size, orderedItemIds.size());
        int to = Math.min(from + size, orderedItemIds.size());
        Map<Long, CollectionItem> loaded = new HashMap<>();
        items.findAllById(orderedItemIds.subList(from, to)).forEach(item -> loaded.put(item.id(), item));
        List<CollectionItem> pageItems = orderedItemIds.subList(from, to).stream().map(loaded::get).toList();
        return new Listing(views(pageItems), page, size, orderedItemIds.size());
    }

    /** Changes the note and/or the hidden flag; a field left {@code null} is not touched (an empty note clears it). */
    @Transactional
    public ItemView update(long userId, long itemId, String note, Boolean hidden) {
        if (note == null && hidden == null) {
            throw new CollectionException(Problem.EMPTY_UPDATE);
        }
        CollectionItem item = ownedItem(userId, itemId);
        if (note != null) {
            item.changeNote(note);
        }
        if (hidden != null) {
            if (hidden) {
                item.hide();
            } else {
                item.unhide();
            }
        }
        return view(items.saveAndFlush(item));
    }

    /** FR-09: only manual-only items are deleted; the rule is the item's own. */
    @Transactional
    public void remove(long userId, long itemId) {
        CollectionItem item = ownedItem(userId, itemId);
        item.ensureRemovable();
        items.delete(item);
    }

    private CollectionItem ownedItem(long userId, long itemId) {
        return items.findByIdAndProfileId(itemId, profileIdOf(userId))
                .orElseThrow(() -> new CollectionException(Problem.ITEM_NOT_FOUND));
    }

    private long profileIdOf(long userId) {
        return profiles.findByUserId(userId).map(Profile::id)
                .orElseThrow(() -> new CollectionException(Problem.ITEM_NOT_FOUND));
    }

    private ItemView view(CollectionItem item) {
        return views(List.of(item)).getFirst();
    }

    private List<ItemView> views(List<CollectionItem> pageItems) {
        Map<Long, MediaSummaries.Summary> media =
                summaries.byId(pageItems.stream().map(CollectionItem::mediaItemId).toList());
        return pageItems.stream().map(item -> ItemView.of(item, media.get(item.mediaItemId()))).toList();
    }

    public record AddResult(ItemView item, boolean created) {}

    public record Listing(List<ItemView> items, int page, int size, long total) {}

    /** An item as the API shows it; {@code sources} lists MANUAL and/or provider names, {@code removable} is FR-09. */
    public record ItemView(long id, MediaCategory category, MediaSummaries.Summary media, String note, boolean hidden,
            List<String> sources, boolean removable, Instant addedAt, Instant lastActivityAt) {

        static ItemView of(CollectionItem item, MediaSummaries.Summary media) {
            List<String> sources = item.sources().stream().map(ItemSource::source).sorted().toList();
            return new ItemView(item.id(), item.category(), media, item.note(), item.hidden(), sources,
                    item.isRemovable(), item.addedAt(), item.lastActivityAt());
        }
    }
}
