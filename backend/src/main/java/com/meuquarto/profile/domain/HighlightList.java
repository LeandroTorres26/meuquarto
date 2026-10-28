package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.HighlightException.Problem;
import com.meuquarto.shared.domain.MediaCategory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

/**
 * An ordered list of highlights of one profile: one list per category, plus one with no category,
 * "Favoritos de tudo" (FR-11), which takes items of any category. The list owns the rules (BR-02, BR-07):
 * the size limit comes from the {@link Plan} it is given, never from a test on who owns what.
 */
@Entity
@Table(name = "highlight_lists")
public class HighlightList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private long profileId;

    /** {@code null} is "Favoritos de tudo". */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private MediaCategory category;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "list_id", nullable = false)
    @OrderBy("position")
    @BatchSize(size = 50)
    private List<Highlight> highlights = new ArrayList<>();

    protected HighlightList() {
        // for JPA
    }

    private HighlightList(long profileId, MediaCategory category) {
        this.profileId = profileId;
        this.category = category;
    }

    /** An empty list; {@code category} {@code null} makes it the "Favoritos de tudo" list. */
    public static HighlightList forProfile(long profileId, MediaCategory category) {
        return new HighlightList(profileId, category);
    }

    /**
     * "Destacar": appends the item at the end. The item must be the owner's, visible and, in a category
     * list, of that category; it can be in the list once (BR-07); and the list cannot grow past the
     * plan's limit (BR-02).
     */
    public void highlight(CollectionItem item, Plan plan) {
        if (item.profileId() != profileId) {
            throw new HighlightException(Problem.NOT_IN_COLLECTION);
        }
        if (item.hidden()) {
            throw new HighlightException(Problem.ITEM_HIDDEN);
        }
        if (category != null && category != item.category()) {
            throw new HighlightException(Problem.CATEGORY_MISMATCH);
        }
        if (highlights.stream().anyMatch(existing -> existing.collectionItemId() == item.id())) {
            throw new HighlightException(Problem.ALREADY_HIGHLIGHTED);
        }
        if (!plan.hasRoomFor(highlights.size())) {
            throw new HighlightException(Problem.HIGHLIGHT_LIMIT_REACHED);
        }
        highlights.add(new Highlight(item.id(), highlights.size()));
    }

    /** "Remover dos destaques": the ones after it move up, so positions stay 0..n-1. */
    public void unhighlight(long highlightId) {
        boolean removed = highlights.removeIf(h -> h.id() != null && h.id() == highlightId);
        if (!removed) {
            throw new HighlightException(Problem.HIGHLIGHT_NOT_FOUND);
        }
        renumber();
    }

    /** The collection item was hidden or deleted: it leaves the list (nothing happens if it was not there). */
    public void dropItem(long collectionItemId) {
        if (highlights.removeIf(h -> h.collectionItemId() == collectionItemId)) {
            renumber();
        }
    }

    /**
     * Rewrites the positions to follow {@code orderedIds}, which must be exactly the ids of the
     * highlights now in the list: none missing, none extra, none repeated.
     */
    public void reorder(List<Long> orderedIds) {
        Set<Long> current = new HashSet<>();
        highlights.forEach(h -> current.add(h.id()));
        Set<Long> requested = new HashSet<>(orderedIds);
        if (orderedIds.size() != highlights.size() || !requested.equals(current)) {
            throw new HighlightException(Problem.INVALID_ORDER);
        }
        highlights.sort(Comparator.comparingInt(h -> orderedIds.indexOf(h.id())));
        renumber();
    }

    private void renumber() {
        for (int i = 0; i < highlights.size(); i++) {
            highlights.get(i).moveTo(i);
        }
    }

    public Long id() {
        return id;
    }

    public long profileId() {
        return profileId;
    }

    public MediaCategory category() {
        return category;
    }

    /** In order, position 0 first. */
    public List<Highlight> highlights() {
        return Collections.unmodifiableList(highlights);
    }
}
