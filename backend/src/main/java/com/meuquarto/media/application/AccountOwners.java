package com.meuquarto.media.application;

import com.meuquarto.shared.domain.MediaCategory;
import java.time.Instant;
import java.util.Optional;

/**
 * What linking needs from whoever owns profiles and collections. It is an interface owned by the media
 * module and implemented by the profile module, so the dependency points one way only (profile uses
 * media, never the reverse; ADR 0020).
 */
public interface AccountOwners {

    /** The profile of a signed-in user; empty if there is none. */
    Optional<Long> profileIdOf(long userId);

    /**
     * FR-16, called inside the unlink transaction before the account row goes away: items that came only
     * from this account leave the collection, except highlighted or noted ones, which become manual.
     */
    void releaseCollectionItems(long profileId, long linkedAccountId);

    /**
     * BR-10, called by the sync once per activity it applies: the work enters the owner's collection
     * visible, or, if it is already there, only gets the source and the activity time. A hidden item
     * stays hidden (FR-09). Idempotent: the same call twice leaves the same collection.
     *
     * @param provider the platform name, stored as the item's source
     */
    void recordSyncedItem(long profileId, long linkedAccountId, String provider, MediaCategory category,
            long mediaItemId, Instant occurredAt);
}
