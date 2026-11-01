package com.meuquarto.profile.application;

import com.meuquarto.profile.application.PublicProfile.NowItem;
import com.meuquarto.profile.domain.CollectionItem;
import com.meuquarto.profile.domain.NowBlock;
import com.meuquarto.profile.domain.NowEntry;
import com.meuquarto.profile.domain.NowException;
import com.meuquarto.profile.domain.NowException.Problem;
import com.meuquarto.profile.domain.NowMode;
import com.meuquarto.profile.domain.Profile;
import com.meuquarto.profile.infra.CollectionItemRepository;
import com.meuquarto.profile.infra.NowEntryRepository;
import com.meuquarto.profile.infra.ProfileRepository;
import com.meuquarto.shared.domain.MediaCategory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The owner's "Agora" settings (S10, FR-15, BR-06). The owner always comes from the session; an item that
 * is not in the owner's collection behaves exactly like one that does not exist. Every change evicts the
 * public profile, since "Agora" is part of it.
 */
@Service
public class ProfileNow {

    private final ProfileRepository profiles;
    private final CollectionItemRepository items;
    private final NowEntryRepository entries;
    private final NowBlocks blocks;
    private final PublicProfileCache publicProfiles;

    public ProfileNow(ProfileRepository profiles, CollectionItemRepository items, NowEntryRepository entries,
            NowBlocks blocks, PublicProfileCache publicProfiles) {
        this.profiles = profiles;
        this.items = items;
        this.entries = entries;
        this.blocks = blocks;
        this.publicProfiles = publicProfiles;
    }

    /** One entry per category, in the order of {@link MediaCategory}, with what the profile shows now. */
    @Transactional(readOnly = true)
    public List<Entry> overview(long userId) {
        long profileId = profileIdOf(userId);
        return entriesOf(profileId, entries.findByProfileId(profileId));
    }

    /**
     * Sets the mode of one category: automatic, pinned to {@code collectionItemId} (required) or hidden.
     * Returns the new state of that category.
     */
    @Transactional
    public Entry change(long userId, MediaCategory category, NowMode mode, Long collectionItemId) {
        long profileId = profileIdOf(userId);
        // Same lock as the collection and the highlights: hiding an item cannot interleave with pinning it.
        Profile profile = profiles.lockById(profileId).orElseThrow(() -> new NowException(Problem.NOT_IN_COLLECTION));
        publicProfiles.evictAfterCommit(profile);
        CollectionItem item = collectionItemId == null ? null
                : items.findByIdAndProfileId(collectionItemId, profileId)
                        .orElseThrow(() -> new NowException(Problem.NOT_IN_COLLECTION));
        NowEntry entry = entries.findByProfileIdAndCategory(profileId, category)
                .orElseGet(() -> NowEntry.automatic(profileId, category));
        entry.change(mode, item);
        entries.saveAndFlush(entry);
        return entriesOf(profileId, entries.findByProfileId(profileId)).stream()
                .filter(e -> e.category() == category).findFirst().orElseThrow();
    }

    private List<Entry> entriesOf(long profileId, List<NowEntry> stored) {
        Map<MediaCategory, NowEntry> byCategory = new EnumMap<>(MediaCategory.class);
        stored.forEach(entry -> byCategory.put(entry.category(), entry));
        Map<MediaCategory, NowItem> shown = blocks.resolve(profileId, stored);
        List<Entry> result = new ArrayList<>();
        for (MediaCategory category : MediaCategory.values()) {
            NowEntry entry = byCategory.get(category);
            result.add(new Entry(category, entry == null ? NowMode.AUTOMATIC : entry.mode(),
                    NowBlock.hasAutomaticSource(category), shown.get(category)));
        }
        return result;
    }

    private long profileIdOf(long userId) {
        return profiles.findByUserId(userId).map(Profile::id)
                .orElseThrow(() -> new NowException(Problem.NOT_IN_COLLECTION));
    }

    /**
     * The state of one category. {@code automaticAvailable} is false where no platform feeds it (Séries);
     * {@code shown} is what the profile shows, or {@code null} when nothing is shown (hidden, or nothing synced yet).
     */
    public record Entry(MediaCategory category, NowMode mode, boolean automaticAvailable, NowItem shown) {}
}
