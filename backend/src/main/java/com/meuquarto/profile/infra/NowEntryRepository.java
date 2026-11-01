package com.meuquarto.profile.infra;

import com.meuquarto.profile.domain.NowEntry;
import com.meuquarto.shared.domain.MediaCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NowEntryRepository extends JpaRepository<NowEntry, Long> {

    List<NowEntry> findByProfileId(long profileId);

    Optional<NowEntry> findByProfileIdAndCategory(long profileId, MediaCategory category);

    Optional<NowEntry> findByProfileIdAndCollectionItemId(long profileId, long collectionItemId);
}
