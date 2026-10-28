package com.meuquarto.profile.application;

import com.meuquarto.media.application.MediaSummaries;
import com.meuquarto.profile.application.PublicProfile.Detail;
import com.meuquarto.profile.application.PublicProfile.Entry;
import com.meuquarto.profile.application.PublicProfile.Link;
import com.meuquarto.profile.application.PublicProfile.Listing;
import com.meuquarto.profile.application.PublicProfile.Origin;
import com.meuquarto.profile.application.PublicProfile.PublicItem;
import com.meuquarto.profile.application.PublicProfile.Section;
import com.meuquarto.profile.domain.CollectionItem;
import com.meuquarto.profile.domain.CollectionSort;
import com.meuquarto.profile.domain.Highlight;
import com.meuquarto.profile.domain.HighlightList;
import com.meuquarto.profile.domain.ItemSource;
import com.meuquarto.profile.domain.Plan;
import com.meuquarto.profile.domain.Profile;
import com.meuquarto.profile.domain.PublicProfileException;
import com.meuquarto.profile.domain.PublicProfileException.Problem;
import com.meuquarto.profile.domain.Slug;
import com.meuquarto.profile.infra.CollectionItemRepository;
import com.meuquarto.profile.infra.HighlightListRepository;
import com.meuquarto.profile.infra.ProfileRepository;
import com.meuquarto.profile.infra.VisibleRef;
import com.meuquarto.shared.domain.MediaCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads of a public profile for anonymous visitors (FR-04, FR-10a, FR-10b). Everything comes from local
 * tables (snapshot in {@code media_items}, ADR 0006): no provider is ever called here. Hidden items
 * (FR-09) and adult works (BR-08) are never returned. Anything that does not exist or may not be seen
 * answers the same "not found".
 */
@Service
public class PublicProfiles {

    public static final int MAX_PAGE_SIZE = 48;
    public static final int MAX_QUERY_LENGTH = 100;

    private static final Pattern REF = Pattern.compile("\\d{1,18}");

    private final ProfileRepository profiles;
    private final CollectionItemRepository items;
    private final HighlightListRepository lists;
    private final MediaSummaries summaries;
    private final PlanResolver plans;
    private final PublicProfileCache cache;
    private final TransactionTemplate readOnly;

    public PublicProfiles(ProfileRepository profiles, CollectionItemRepository items, HighlightListRepository lists,
            MediaSummaries summaries, PlanResolver plans, PublicProfileCache cache,
            PlatformTransactionManager transactionManager) {
        this.profiles = profiles;
        this.items = items;
        this.lists = lists;
        this.summaries = summaries;
        this.plans = plans;
        this.cache = cache;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** The profile home (S4), from the cache when it is there. */
    public PublicProfile profile(String rawSlug) {
        String slug = Slug.normalize(rawSlug);
        if (slug.length() > Slug.MAX_LENGTH) {
            throw new PublicProfileException(Problem.PROFILE_NOT_FOUND);
        }
        return cache.get(slug, () -> readOnly.execute(status -> build(profileBySlug(slug))));
    }

    /** The item dialog (S17). The item must be visible, not adult and in this profile. */
    @Transactional(readOnly = true)
    public Detail item(String rawSlug, String rawRef) {
        Profile profile = profileBySlug(Slug.normalize(rawSlug));
        if (rawRef == null || !REF.matcher(rawRef).matches()) {
            throw new PublicProfileException(Problem.ITEM_NOT_FOUND);
        }
        CollectionItem item = items.findByIdAndProfileId(Long.parseLong(rawRef), profile.id())
                .filter(found -> !found.hidden())
                .orElseThrow(() -> new PublicProfileException(Problem.ITEM_NOT_FOUND));
        if (summaries.publicIds(List.of(item.mediaItemId()), "", null).isEmpty()) {
            throw new PublicProfileException(Problem.ITEM_NOT_FOUND);
        }
        MediaSummaries.Summary media = summaries.byId(List.of(item.mediaItemId())).get(item.mediaItemId());
        List<Origin> origin = item.sources().stream()
                .sorted(Comparator.comparing(ItemSource::source))
                .map(source -> new Origin(source.source(), source.firstSeenAt())).toList();
        List<Link> links = media.externalUrl().map(url -> new Link(media.provider(), url)).stream().toList();
        return new Detail(publicItem(item, media), origin, links);
    }

    /** One page of a category's visible collection (S18), searched by title and sorted. */
    @Transactional(readOnly = true)
    public Listing listing(String rawSlug, MediaCategory category, CollectionSort sort, String rawQuery, int page,
            int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query is longer than " + MAX_QUERY_LENGTH);
        }
        Profile profile = profileBySlug(Slug.normalize(rawSlug));

        List<VisibleRef> refs = items.findVisibleRefs(profile.id(), category); // most recent first
        Map<Long, Long> itemIdByMediaId = new HashMap<>();
        refs.forEach(ref -> itemIdByMediaId.put(ref.mediaItemId(), ref.itemId()));
        // The media module drops adult works and applies the title filter; it orders title and year itself.
        MediaSummaries.Order order = switch (sort) {
            case RECENT -> null;
            case TITLE -> MediaSummaries.Order.TITLE;
            case YEAR -> MediaSummaries.Order.YEAR;
        };
        List<Long> allowedMediaIds = summaries.publicIds(itemIdByMediaId.keySet(), query, order);
        List<Long> orderedItemIds;
        if (order == null) {
            Set<Long> allowed = new HashSet<>(allowedMediaIds);
            orderedItemIds = refs.stream().filter(ref -> allowed.contains(ref.mediaItemId()))
                    .map(VisibleRef::itemId).toList();
        } else {
            orderedItemIds = allowedMediaIds.stream().map(itemIdByMediaId::get).toList();
        }

        int from = (int) Math.min((long) page * size, orderedItemIds.size());
        int to = Math.min(from + size, orderedItemIds.size());
        List<Long> pageIds = orderedItemIds.subList(from, to);
        Map<Long, CollectionItem> loaded = new HashMap<>();
        items.findAllById(pageIds).forEach(item -> loaded.put(item.id(), item));
        Map<Long, MediaSummaries.Summary> media = summaries.byId(
                loaded.values().stream().map(CollectionItem::mediaItemId).toList());
        Set<Long> highlighted = new HashSet<>();
        lists.findByProfileIdAndCategory(profile.id(), category)
                .ifPresent(list -> list.highlights().forEach(h -> highlighted.add(h.collectionItemId())));
        List<Entry> entries = pageIds.stream().map(loaded::get)
                .map(item -> new Entry(publicItem(item, media.get(item.mediaItemId())), highlighted.contains(item.id())))
                .toList();
        return new Listing(entries, page, size, orderedItemIds.size());
    }

    private Profile profileBySlug(String normalizedSlug) {
        // A lookup even for text that can never be a slug (reserved words included), so every miss looks alike.
        if (normalizedSlug.length() > Slug.MAX_LENGTH) {
            throw new PublicProfileException(Problem.PROFILE_NOT_FOUND);
        }
        return profiles.findBySlug(normalizedSlug)
                .orElseThrow(() -> new PublicProfileException(Problem.PROFILE_NOT_FOUND));
    }

    private PublicProfile build(Profile profile) {
        Plan plan = plans.planOf(profile.userId());
        List<VisibleRef> visible = items.findVisibleRefs(profile.id()); // most recent first
        Set<Long> allowedMedia = new HashSet<>(summaries.publicIds(
                visible.stream().map(VisibleRef::mediaItemId).distinct().toList(), "", null));
        Map<Long, VisibleRef> shown = new HashMap<>();
        Map<MediaCategory, List<VisibleRef>> recentByCategory = new EnumMap<>(MediaCategory.class);
        for (VisibleRef ref : visible) {
            if (allowedMedia.contains(ref.mediaItemId())) {
                shown.put(ref.itemId(), ref);
                recentByCategory.computeIfAbsent(ref.category(), c -> new ArrayList<>()).add(ref);
            }
        }

        Map<Optional<MediaCategory>, HighlightList> highlightLists = new HashMap<>();
        lists.findByProfileId(profile.id())
                .forEach(list -> highlightLists.put(Optional.ofNullable(list.category()), list));

        List<Long> favoriteIds = highlightedIds(highlightLists.get(Optional.<MediaCategory>empty()), shown, null);
        Map<MediaCategory, List<Long>> sectionIds = new EnumMap<>(MediaCategory.class);
        Set<MediaCategory> mostRecent = new HashSet<>();
        for (MediaCategory category : MediaCategory.values()) {
            List<VisibleRef> all = recentByCategory.get(category);
            if (all == null) {
                continue;
            }
            List<Long> chosen = highlightedIds(highlightLists.get(Optional.of(category)), shown, category);
            if (chosen.isEmpty()) {
                chosen = all.stream().limit(plan.highlightLimit()).map(VisibleRef::itemId).toList();
                mostRecent.add(category);
            }
            sectionIds.put(category, chosen);
        }

        Set<Long> needed = new HashSet<>(favoriteIds);
        sectionIds.values().forEach(needed::addAll);
        Map<Long, CollectionItem> loaded = new HashMap<>();
        items.findAllById(needed).forEach(item -> loaded.put(item.id(), item));
        Map<Long, MediaSummaries.Summary> media = summaries.byId(
                loaded.values().stream().map(CollectionItem::mediaItemId).toList());

        List<PublicItem> favorites = favoriteIds.stream().map(loaded::get)
                .map(item -> publicItem(item, media.get(item.mediaItemId()))).toList();
        List<Section> sections = new ArrayList<>();
        sectionIds.forEach((category, ids) -> sections.add(new Section(category, recentByCategory.get(category).size(),
                mostRecent.contains(category),
                ids.stream().map(loaded::get).map(item -> publicItem(item, media.get(item.mediaItemId()))).toList())));
        // The display name is edited in #15; until then the slug stands in for it.
        return new PublicProfile(profile.slug(), profile.slug(), favorites, List.copyOf(sections));
    }

    /** The list's items in the owner's order, keeping only those still shown (and of the category, if the list has one). */
    private static List<Long> highlightedIds(HighlightList list, Map<Long, VisibleRef> shown, MediaCategory category) {
        if (list == null) {
            return List.of();
        }
        return list.highlights().stream().map(Highlight::collectionItemId)
                .filter(id -> shown.containsKey(id) && (category == null || shown.get(id).category() == category))
                .toList();
    }

    private static PublicItem publicItem(CollectionItem item, MediaSummaries.Summary media) {
        return new PublicItem(item.id(), item.category(), media.title(), media.year(), media.imageUrl(), item.note());
    }
}
