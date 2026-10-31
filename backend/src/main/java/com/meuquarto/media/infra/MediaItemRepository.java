package com.meuquarto.media.infra;

import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.media.domain.MediaItem;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    Optional<MediaItem> findByCategoryAndCanonicalProviderAndExternalId(
            MediaCategory category, String canonicalProvider, String externalId);

    /**
     * Insert or update in one atomic statement on the canonical key (BR-04): two requests adding the
     * same work at once end with one row, and no unique violation can poison a caller's transaction.
     * <p>
     * {@code overwrite} says whether the writer is the canonical catalog (see {@code MetadataSource}).
     * If so the stored metadata is replaced. If not, the existing row keeps its title and only has its
     * null year and image filled in; a missing row is created either way.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO media_items (category, canonical_provider, external_id, title, year, image_url, adult,
                                     fetched_at, metadata_source)
            VALUES (:category, :provider, :externalId, :title, CAST(:year AS integer), CAST(:imageUrl AS text), :adult,
                    :now, :source)
            ON CONFLICT (category, canonical_provider, external_id) DO UPDATE SET
                title = CASE WHEN CAST(:overwrite AS boolean) THEN EXCLUDED.title ELSE media_items.title END,
                year = CASE WHEN CAST(:overwrite AS boolean) THEN EXCLUDED.year
                            ELSE COALESCE(media_items.year, EXCLUDED.year) END,
                image_url = CASE WHEN CAST(:overwrite AS boolean) THEN EXCLUDED.image_url
                                 ELSE COALESCE(media_items.image_url, EXCLUDED.image_url) END,
                adult = CASE WHEN CAST(:overwrite AS boolean) THEN EXCLUDED.adult ELSE media_items.adult END,
                fetched_at = CASE WHEN CAST(:overwrite AS boolean) THEN EXCLUDED.fetched_at
                                  ELSE media_items.fetched_at END,
                metadata_source = CASE WHEN CAST(:overwrite AS boolean) THEN EXCLUDED.metadata_source
                                       ELSE media_items.metadata_source END
            """)
    void upsert(@Param("category") String category, @Param("provider") String provider,
            @Param("externalId") String externalId, @Param("title") String title, @Param("year") Integer year,
            @Param("imageUrl") String imageUrl, @Param("adult") boolean adult, @Param("now") Instant now,
            @Param("source") String source, @Param("overwrite") boolean overwrite);

    @Query("select m.id from MediaItem m where m.id in :ids order by lower(m.title), m.id")
    List<Long> idsByTitle(@Param("ids") Collection<Long> ids);

    @Query("select m.id from MediaItem m where m.id in :ids order by m.year desc nulls last, lower(m.title), m.id")
    List<Long> idsByYearDesc(@Param("ids") Collection<Long> ids);

    // Public listing reads (BR-08): adult works never come back, whatever the caller holds. The pattern is
    // built by the caller with '!' as the LIKE escape character.
    @Query("""
            select m.id from MediaItem m
            where m.id in :ids and m.adult = false and lower(m.title) like :pattern escape '!'
            order by m.id
            """)
    List<Long> publicIds(@Param("ids") Collection<Long> ids, @Param("pattern") String pattern);

    @Query("""
            select m.id from MediaItem m
            where m.id in :ids and m.adult = false and lower(m.title) like :pattern escape '!'
            order by lower(m.title), m.id
            """)
    List<Long> publicIdsByTitle(@Param("ids") Collection<Long> ids, @Param("pattern") String pattern);

    @Query("""
            select m.id from MediaItem m
            where m.id in :ids and m.adult = false and lower(m.title) like :pattern escape '!'
            order by m.year desc nulls last, lower(m.title), m.id
            """)
    List<Long> publicIdsByYearDesc(@Param("ids") Collection<Long> ids, @Param("pattern") String pattern);
}
