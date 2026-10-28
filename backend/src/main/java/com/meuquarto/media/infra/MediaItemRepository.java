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
     * Insert or refresh in one atomic statement on the canonical key (BR-04): two requests adding the
     * same work at once end with one row, and no unique violation can poison a caller's transaction.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO media_items (category, canonical_provider, external_id, title, year, image_url, adult, fetched_at)
            VALUES (:category, :provider, :externalId, :title, CAST(:year AS integer), CAST(:imageUrl AS text), :adult, :now)
            ON CONFLICT (category, canonical_provider, external_id) DO UPDATE SET
                title = EXCLUDED.title, year = EXCLUDED.year, image_url = EXCLUDED.image_url,
                adult = EXCLUDED.adult, fetched_at = EXCLUDED.fetched_at
            """)
    void upsert(@Param("category") String category, @Param("provider") String provider,
            @Param("externalId") String externalId, @Param("title") String title, @Param("year") Integer year,
            @Param("imageUrl") String imageUrl, @Param("adult") boolean adult, @Param("now") Instant now);

    @Query("select m.id from MediaItem m where m.id in :ids order by lower(m.title), m.id")
    List<Long> idsByTitle(@Param("ids") Collection<Long> ids);

    @Query("select m.id from MediaItem m where m.id in :ids order by m.year desc nulls last, lower(m.title), m.id")
    List<Long> idsByYearDesc(@Param("ids") Collection<Long> ids);
}
