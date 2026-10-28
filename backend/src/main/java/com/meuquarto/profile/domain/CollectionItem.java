package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.CollectionException.Problem;
import com.meuquarto.shared.domain.MediaCategory;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

/**
 * One work in a profile's collection (FR-08). It refers to the shared media item by id only: the
 * metadata lives in the media module. The item owns its {@link ItemSource}s and the rules that depend
 * on them: removal (FR-09), hiding and what a re-sync may change.
 */
@Entity
@Table(name = "collection_items")
public class CollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private long profileId;

    @Column(name = "media_item_id", nullable = false, updatable = false)
    private long mediaItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MediaCategory category;

    @Column(length = Note.MAX_LENGTH)
    private String note;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "collection_item_sources", joinColumns = @JoinColumn(name = "collection_item_id"))
    @BatchSize(size = 50)
    private Set<ItemSource> sources = new HashSet<>();

    protected CollectionItem() {
        // for JPA
    }

    private CollectionItem(long profileId, long mediaItemId, MediaCategory category, ItemSource first, Instant now) {
        this.profileId = profileId;
        this.mediaItemId = mediaItemId;
        this.category = category;
        this.addedAt = now;
        this.lastActivityAt = now;
        this.sources.add(first);
    }

    /** A new item added by the owner. */
    public static CollectionItem addedManually(long profileId, long mediaItemId, MediaCategory category, Instant now) {
        return new CollectionItem(profileId, mediaItemId, category, ItemSource.manual(now), now);
    }

    /** A new item brought by a sync. It starts visible; only the owner hides things. */
    public static CollectionItem foundBySync(long profileId, long mediaItemId, MediaCategory category,
            String provider, Long linkedAccountId, Instant now) {
        return new CollectionItem(profileId, mediaItemId, category, ItemSource.synced(provider, linkedAccountId, now), now);
    }

    /**
     * The owner adds the work again (BR-07: same item, never a second row). It becomes manual too and,
     * if it was hidden, visible again: adding is an explicit "I want this here".
     */
    public void addAgainByHand(Instant now) {
        addSourceIfMissing(ItemSource.manual(now));
        hidden = false;
        lastActivityAt = now;
    }

    /**
     * A sync saw the work again (FR-09). It records the source and the activity but never changes
     * {@code hidden}: an item the owner hid stays hidden after every later sync.
     */
    public void seenBySync(String provider, Long linkedAccountId, Instant now) {
        addSourceIfMissing(ItemSource.synced(provider, linkedAccountId, now));
        lastActivityAt = now;
    }

    /**
     * FR-16: the owner unlinked the account {@code linkedAccountId}. Its source goes away. If nothing else
     * keeps the item (no manual source, no other platform), it leaves, unless the owner highlighted or
     * noted it: then it stays and becomes manual. Returns true when the item must be deleted.
     */
    public boolean releaseAccount(long linkedAccountId, boolean highlighted, Instant now) {
        boolean wasFromIt = sources.removeIf(
                source -> source.linkedAccountId() != null && source.linkedAccountId() == linkedAccountId);
        if (!wasFromIt || !sources.isEmpty()) {
            return false;
        }
        if (highlighted || note != null) {
            sources.add(ItemSource.manual(now));
            return false;
        }
        return true;
    }

    public void hide() {
        hidden = true;
    }

    public void unhide() {
        hidden = false;
    }

    /** Blank clears the note (FR-10); see {@link Note} for the other rules. */
    public void changeNote(String raw) {
        note = Note.clean(raw);
    }

    /** True when the item came only from the owner's hand. */
    public boolean isRemovable() {
        return sources.stream().allMatch(ItemSource::isManual);
    }

    /** FR-09: removing a synced item would bring it back at the next sync, so only hiding is allowed. */
    public void ensureRemovable() {
        if (!isRemovable()) {
            throw new CollectionException(Problem.CANNOT_REMOVE_SYNCED);
        }
    }

    private void addSourceIfMissing(ItemSource candidate) {
        boolean present = sources.stream().anyMatch(existing -> existing.source().equals(candidate.source()));
        if (!present) {
            sources.add(candidate);
        }
    }

    public Long id() {
        return id;
    }

    public long profileId() {
        return profileId;
    }

    public long mediaItemId() {
        return mediaItemId;
    }

    public MediaCategory category() {
        return category;
    }

    public String note() {
        return note;
    }

    public boolean hidden() {
        return hidden;
    }

    public Instant addedAt() {
        return addedAt;
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    public Set<ItemSource> sources() {
        return Collections.unmodifiableSet(sources);
    }
}
