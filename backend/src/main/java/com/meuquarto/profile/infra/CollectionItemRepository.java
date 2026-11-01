package com.meuquarto.profile.infra;

import com.meuquarto.profile.domain.CollectionItem;
import com.meuquarto.shared.domain.MediaCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, Long> {

    Optional<CollectionItem> findByProfileIdAndMediaItemId(long profileId, long mediaItemId);

    /** Filtering by the profile here is what makes someone else's item look like a missing one. */
    Optional<CollectionItem> findByIdAndProfileId(long id, long profileId);

    @Query("""
            select i from CollectionItem i
            where i.profileId = :profileId and i.category = :category and (:includeHidden = true or i.hidden = false)
            """)
    Page<CollectionItem> findPage(@Param("profileId") long profileId, @Param("category") MediaCategory category,
            @Param("includeHidden") boolean includeHidden, Pageable pageable);

    /** Every item of the profile that one linked account brought (FR-16). */
    @Query("""
            select distinct i from CollectionItem i join i.sources s
            where i.profileId = :profileId and s.linkedAccountId = :accountId
            """)
    List<CollectionItem> findFromAccount(@Param("profileId") long profileId, @Param("accountId") long accountId);

    /** Only the ids, for sorts that depend on media metadata (title, year) and are resolved by the media module. */
    @Query("""
            select new com.meuquarto.profile.infra.ItemRef(i.id, i.mediaItemId) from CollectionItem i
            where i.profileId = :profileId and i.category = :category and (:includeHidden = true or i.hidden = false)
            """)
    List<ItemRef> findRefs(@Param("profileId") long profileId, @Param("category") MediaCategory category,
            @Param("includeHidden") boolean includeHidden);

    /** Every visible item of the profile, most recent activity first; what the public read model is built from. */
    @Query("""
            select new com.meuquarto.profile.infra.VisibleRef(i.id, i.mediaItemId, i.category) from CollectionItem i
            where i.profileId = :profileId and i.hidden = false
            order by i.lastActivityAt desc, i.id desc
            """)
    List<VisibleRef> findVisibleRefs(@Param("profileId") long profileId);

    /**
     * The most recently active visible items of one category that a sync brought (at least one source other
     * than MANUAL); "Agora" picks the first of them it may show (BR-06).
     */
    @Query("""
            select i from CollectionItem i
            where i.profileId = :profileId and i.category = :category and i.hidden = false
              and exists (select 1 from i.sources s where s.source <> 'MANUAL')
            order by i.lastActivityAt desc, i.id desc
            """)
    List<CollectionItem> findVisibleSynced(@Param("profileId") long profileId,
            @Param("category") MediaCategory category, Pageable pageable);

    /** The visible items of one category, most recent activity first (index: profile_id, category, last_activity_at). */
    @Query("""
            select new com.meuquarto.profile.infra.VisibleRef(i.id, i.mediaItemId, i.category) from CollectionItem i
            where i.profileId = :profileId and i.category = :category and i.hidden = false
            order by i.lastActivityAt desc, i.id desc
            """)
    List<VisibleRef> findVisibleRefs(@Param("profileId") long profileId, @Param("category") MediaCategory category);
}
