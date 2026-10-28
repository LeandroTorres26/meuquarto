package com.meuquarto.media.application;

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
}
