package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.NowException.Problem;
import com.meuquarto.shared.domain.MediaCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What the owner decided for the "Agora" slot of one category (FR-15, BR-06). The three modes are
 * exclusive: automatic (no pin, not hidden), pinned (an item of the collection) or hidden. This class only
 * keeps the choice and checks that it makes sense; what the profile shows is decided by {@link NowBlock}.
 */
@Entity
@Table(name = "now_entries")
public class NowEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private long profileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private MediaCategory category;

    @Column(name = "collection_item_id")
    private Long collectionItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private NowSource source = NowSource.AUTO;

    @Column(nullable = false)
    private boolean hidden;

    protected NowEntry() {
        // for JPA
    }

    private NowEntry(long profileId, MediaCategory category) {
        this.profileId = profileId;
        this.category = category;
    }

    /** A slot in the default state: automatic and visible. */
    public static NowEntry automatic(long profileId, MediaCategory category) {
        return new NowEntry(profileId, category);
    }

    /**
     * Moves the slot to {@code mode}. {@code item} is the collection item to pin: required for
     * {@link NowMode#PINNED}, refused for the others.
     */
    public void change(NowMode mode, CollectionItem item) {
        if (mode == NowMode.PINNED) {
            pin(item);
            return;
        }
        if (item != null) {
            throw new NowException(Problem.UNEXPECTED_ITEM);
        }
        if (mode == NowMode.HIDDEN) {
            hide();
        } else {
            useAutomatic();
        }
    }

    /** The pinned item must be the owner's, visible and of this category. */
    private void pin(CollectionItem item) {
        if (item == null) {
            throw new NowException(Problem.ITEM_REQUIRED);
        }
        if (item.profileId() != profileId) {
            throw new NowException(Problem.NOT_IN_COLLECTION);
        }
        if (item.category() != category) {
            throw new NowException(Problem.CATEGORY_MISMATCH);
        }
        if (item.hidden()) {
            throw new NowException(Problem.ITEM_HIDDEN);
        }
        source = NowSource.MANUAL;
        collectionItemId = item.id();
        hidden = false;
    }

    private void hide() {
        source = NowSource.AUTO;
        collectionItemId = null;
        hidden = true;
    }

    private void useAutomatic() {
        source = NowSource.AUTO;
        collectionItemId = null;
        hidden = false;
    }

    /** The collection item {@code itemId} was hidden by the owner: a pin on it goes back to automatic. */
    public void releaseItem(long itemId) {
        if (source == NowSource.MANUAL && collectionItemId != null && collectionItemId == itemId) {
            useAutomatic();
        }
    }

    public NowMode mode() {
        if (hidden) {
            return NowMode.HIDDEN;
        }
        return source == NowSource.MANUAL ? NowMode.PINNED : NowMode.AUTOMATIC;
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

    public NowSource source() {
        return source;
    }

    public Long collectionItemId() {
        return collectionItemId;
    }

    public boolean hidden() {
        return hidden;
    }
}
