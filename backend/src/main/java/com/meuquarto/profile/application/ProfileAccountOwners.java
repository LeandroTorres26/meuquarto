package com.meuquarto.profile.application;

import com.meuquarto.media.application.AccountOwners;
import com.meuquarto.profile.domain.Profile;
import com.meuquarto.profile.infra.ProfileRepository;
import com.meuquarto.shared.domain.MediaCategory;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The profile side of linking accounts: implements the port the media module declares ({@link AccountOwners}),
 * so media never imports profile (ADR 0020).
 */
@Service
class ProfileAccountOwners implements AccountOwners {

    private final ProfileRepository profiles;
    private final ProfileCollection collection;

    ProfileAccountOwners(ProfileRepository profiles, ProfileCollection collection) {
        this.profiles = profiles;
        this.collection = collection;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> profileIdOf(long userId) {
        return profiles.findByUserId(userId).map(Profile::id);
    }

    @Override
    public void releaseCollectionItems(long profileId, long linkedAccountId) {
        collection.releaseLinkedAccount(profileId, linkedAccountId);
    }

    @Override
    public void recordSyncedItem(long profileId, long linkedAccountId, String provider, MediaCategory category,
            long mediaItemId, Instant occurredAt) {
        collection.recordSyncedItem(profileId, category, mediaItemId, provider, linkedAccountId, occurredAt);
    }
}
