package com.meuquarto.profile.application;

import com.meuquarto.media.application.MediaSummaries;
import com.meuquarto.profile.domain.CollectionItem;
import com.meuquarto.profile.domain.Highlight;
import com.meuquarto.profile.domain.HighlightException;
import com.meuquarto.profile.domain.HighlightException.Problem;
import com.meuquarto.profile.domain.HighlightList;
import com.meuquarto.profile.domain.Plan;
import com.meuquarto.profile.domain.Profile;
import com.meuquarto.profile.infra.CollectionItemRepository;
import com.meuquarto.profile.infra.HighlightListRepository;
import com.meuquarto.profile.infra.ProfileRepository;
import com.meuquarto.shared.domain.MediaCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The owner's highlight lists (FR-09a, FR-11, BR-02, BR-07). The owner always comes from the session;
 * a highlight or item that is not the owner's behaves like one that does not exist. A list with
 * category {@code null} is "Favoritos de tudo". The plan comes from {@link PlanResolver} and is only
 * ever used through the {@link Plan} interface.
 */
@Service
public class ProfileHighlights {

    private final ProfileRepository profiles;
    private final CollectionItemRepository items;
    private final HighlightListRepository lists;
    private final MediaSummaries summaries;
    private final PlanResolver plans;

    public ProfileHighlights(ProfileRepository profiles, CollectionItemRepository items, HighlightListRepository lists,
            MediaSummaries summaries, PlanResolver plans) {
        this.profiles = profiles;
        this.items = items;
        this.lists = lists;
        this.summaries = summaries;
        this.plans = plans;
    }

    /** Every list (favorites first, then one per category, empty ones included) and the plan's limit. */
    @Transactional(readOnly = true)
    public Overview overview(long userId) {
        long profileId = profileIdOf(userId);
        Plan plan = plans.planOf(userId);
        List<HighlightList> stored = lists.findByProfileId(profileId);
        Map<Optional<MediaCategory>, HighlightList> byCategory = new HashMap<>();
        stored.forEach(list -> byCategory.put(Optional.ofNullable(list.category()), list));

        Views views = viewsOf(stored);
        List<ListView> result = new ArrayList<>();
        result.add(views.listView(null, byCategory.get(Optional.<MediaCategory>empty())));
        for (MediaCategory category : MediaCategory.values()) {
            result.add(views.listView(category, byCategory.get(Optional.of(category))));
        }
        return new Overview(plan.code(), plan.highlightLimit(), result);
    }

    /** "Destacar": appends the collection item to the list of {@code category} ({@code null}: favorites). */
    @Transactional
    public HighlightView add(long userId, MediaCategory category, long collectionItemId) {
        long profileId = lockedProfileIdOf(userId);
        CollectionItem item = items.findByIdAndProfileId(collectionItemId, profileId)
                .orElseThrow(() -> new HighlightException(Problem.NOT_IN_COLLECTION));
        HighlightList list = findList(profileId, category)
                .orElseGet(() -> HighlightList.forProfile(profileId, category));
        list.highlight(item, plans.planOf(userId));
        HighlightList saved = lists.saveAndFlush(list);
        Highlight added = saved.highlights().stream()
                .filter(h -> h.collectionItemId() == collectionItemId).findFirst().orElseThrow();
        return viewsOf(List.of(saved)).highlightView(added);
    }

    /** "Remover dos destaques". */
    @Transactional
    public void remove(long userId, long highlightId) {
        long profileId = lockedProfileIdOf(userId);
        HighlightList list = lists.findByHighlightIdAndProfileId(highlightId, profileId)
                .orElseThrow(() -> new HighlightException(Problem.HIGHLIGHT_NOT_FOUND));
        list.unhighlight(highlightId);
        lists.saveAndFlush(list);
    }

    /** Saves the order the client sent: it must be exactly the ids now in the list. */
    @Transactional
    public ListView reorder(long userId, MediaCategory category, List<Long> orderedIds) {
        long profileId = lockedProfileIdOf(userId);
        Optional<HighlightList> found = findList(profileId, category);
        if (found.isEmpty()) {
            // No list yet means nothing to order: only the empty order is right.
            if (!orderedIds.isEmpty()) {
                throw new HighlightException(Problem.INVALID_ORDER);
            }
            return viewsOf(List.of()).listView(category, null);
        }
        HighlightList list = found.get();
        list.reorder(orderedIds);
        lists.saveAndFlush(list);
        return viewsOf(List.of(list)).listView(category, list);
    }

    private Optional<HighlightList> findList(long profileId, MediaCategory category) {
        return category == null
                ? lists.findByProfileIdAndCategoryIsNull(profileId)
                : lists.findByProfileIdAndCategory(profileId, category);
    }

    private long profileIdOf(long userId) {
        return profiles.findByUserId(userId).map(Profile::id)
                .orElseThrow(() -> new HighlightException(Problem.NOT_IN_COLLECTION));
    }

    /** Writers of one profile's lists go through the profile row lock, so the limit check cannot be raced. */
    private long lockedProfileIdOf(long userId) {
        long profileId = profileIdOf(userId);
        profiles.lockById(profileId).orElseThrow(() -> new HighlightException(Problem.NOT_IN_COLLECTION));
        return profileId;
    }

    /** Loads the media of every highlight in {@code stored} with two queries, however many lists there are. */
    private Views viewsOf(List<HighlightList> stored) {
        List<Long> itemIds = stored.stream().flatMap(list -> list.highlights().stream())
                .map(Highlight::collectionItemId).toList();
        Map<Long, Long> mediaIdByItemId = new HashMap<>();
        items.findAllById(itemIds).forEach(item -> mediaIdByItemId.put(item.id(), item.mediaItemId()));
        Map<Long, MediaSummaries.Summary> media = summaries.byId(mediaIdByItemId.values());
        return new Views(mediaIdByItemId, media);
    }

    private record Views(Map<Long, Long> mediaIdByItemId, Map<Long, MediaSummaries.Summary> media) {

        HighlightView highlightView(Highlight highlight) {
            return new HighlightView(highlight.id(), highlight.collectionItemId(), highlight.position(),
                    media.get(mediaIdByItemId.get(highlight.collectionItemId())));
        }

        ListView listView(MediaCategory category, HighlightList list) {
            List<HighlightView> highlights = list == null ? List.of()
                    : list.highlights().stream().map(this::highlightView).toList();
            return new ListView(category, highlights);
        }
    }

    public record Overview(String plan, int limit, List<ListView> lists) {}

    /** {@code category} {@code null} is "Favoritos de tudo"; {@code items} are in order. */
    public record ListView(MediaCategory category, List<HighlightView> items) {}

    public record HighlightView(long id, long collectionItemId, int position, MediaSummaries.Summary media) {}
}
