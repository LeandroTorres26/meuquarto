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
     * <p>
     * The link hints (FR-10a: {@code mal_id}, Last.fm names) are facts about the work, not metadata that
     * can disagree: a value that is offered is kept, and an absent one never erases what is stored. A
     * canonical writer's value wins over the stored one, any other writer only fills a null. This is how an
     * existing row gets its {@code mal_id} on the next sync or catalog read.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO media_items (category, canonical_provider, external_id, title, year, image_url, adult,
                                     fetched_at, metadata_source, mal_id, music_artist, music_album)
            VALUES (:category, :provider, :externalId, :title, CAST(:year AS integer), CAST(:imageUrl AS text), :adult,
                    :now, :source, CAST(:malId AS integer), CAST(:artist AS text), CAST(:album AS text))
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
                                       ELSE media_items.metadata_source END,
                mal_id = CASE WHEN CAST(:overwrite AS boolean) THEN COALESCE(EXCLUDED.mal_id, media_items.mal_id)
                              ELSE COALESCE(media_items.mal_id, EXCLUDED.mal_id) END,
                music_artist = CASE WHEN CAST(:overwrite AS boolean)
                                    THEN COALESCE(EXCLUDED.music_artist, media_items.music_artist)
                                    ELSE COALESCE(media_items.music_artist, EXCLUDED.music_artist) END,
                music_album = CASE WHEN CAST(:overwrite AS boolean)
                                   THEN COALESCE(EXCLUDED.music_album, media_items.music_album)
                                   ELSE COALESCE(media_items.music_album, EXCLUDED.music_album) END
            """)
    void upsert(@Param("category") String category, @Param("provider") String provider,
            @Param("externalId") String externalId, @Param("title") String title, @Param("year") Integer year,
            @Param("imageUrl") String imageUrl, @Param("adult") boolean adult, @Param("now") Instant now,
            @Param("source") String source, @Param("overwrite") boolean overwrite,
            @Param("malId") Integer malId, @Param("artist") String artist, @Param("album") String album);

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
