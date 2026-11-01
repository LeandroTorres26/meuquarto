package com.meuquarto.profile.domain;

import com.meuquarto.shared.domain.MediaCategory;
import java.util.Optional;

/**
 * BR-06: what the "Agora" block shows for one category. The hidden choice wins, then the item the owner
 * pinned, then the latest synced item. Séries has no platform to sync from, so it only ever shows a pin.
 * Plain logic, no framework: the caller passes in what it found in the database.
 */
public final class NowBlock {

    /** What the slot shows: a collection item and why (the owner's pin, or the latest sync). */
    public record Shown(long collectionItemId, NowSource source) {}

    private NowBlock() {
    }

    /** False for the categories no platform feeds (today only Séries). */
    public static boolean hasAutomaticSource(MediaCategory category) {
        return category != MediaCategory.SERIES;
    }

    /**
     * @param entry the owner's choice, or {@code null} when they never touched the slot (automatic)
     * @param pinnedShowable whether the pinned item still exists, is visible and may be shown publicly;
     *        when it is not, the pin is ignored and the slot falls back to automatic
     * @param latestSynced the most recent visible synced item of the category, if any
     */
    public static Optional<Shown> shown(MediaCategory category, NowEntry entry, boolean pinnedShowable,
            Optional<Long> latestSynced) {
        if (entry != null && entry.mode() == NowMode.HIDDEN) {
            return Optional.empty();
        }
        if (entry != null && entry.mode() == NowMode.PINNED && pinnedShowable) {
            return Optional.of(new Shown(entry.collectionItemId(), NowSource.MANUAL));
        }
        if (!hasAutomaticSource(category)) {
            return Optional.empty();
        }
        return latestSynced.map(id -> new Shown(id, NowSource.AUTO));
    }
}
