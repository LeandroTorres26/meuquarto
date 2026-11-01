package com.meuquarto.profile.infra;

import com.meuquarto.profile.domain.HighlightList;
import com.meuquarto.shared.domain.MediaCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HighlightListRepository extends JpaRepository<HighlightList, Long> {

    List<HighlightList> findByProfileId(long profileId);

    Optional<HighlightList> findByProfileIdAndCategory(long profileId, MediaCategory category);

    /** The "Favoritos de tudo" list (no category). */
    Optional<HighlightList> findByProfileIdAndCategoryIsNull(long profileId);

    /** Filtering by the profile here is what makes someone else's highlight look like a missing one. */
    @Query("""
            select l from HighlightList l join l.highlights h
            where h.id = :highlightId and l.profileId = :profileId
            """)
    Optional<HighlightList> findByHighlightIdAndProfileId(@Param("highlightId") long highlightId,
            @Param("profileId") long profileId);

    /** Every list of the profile that holds the collection item. */
    @Query("""
            select distinct l from HighlightList l join l.highlights h
            where h.collectionItemId = :itemId and l.profileId = :profileId
            """)
    List<HighlightList> findContainingItem(@Param("itemId") long itemId, @Param("profileId") long profileId);
}
