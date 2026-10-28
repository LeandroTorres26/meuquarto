package com.meuquarto.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.media.application.MediaSnapshots;
import com.meuquarto.media.application.MediaSnapshots.Snapshot;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.media.domain.MediaCategory;
import com.meuquarto.support.TestcontainersConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** BR-04 against a real PostgreSQL: the same canonical key never creates two rows. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MediaSnapshotsTests.FakeCatalogs.class})
class MediaSnapshotsTests {

    private static final CatalogEntry MATRIX =
            new CatalogEntry(MediaCategory.MOVIE, "TMDB", "603", "Matrix", 1999, "https://image.tmdb.org/t/p/w342/a.jpg", false);

    @TestConfiguration
    static class FakeCatalogs {

        @Bean
        @Primary
        CatalogRegistry fakeRegistry() {
            return new CatalogRegistry(List.of(new CatalogProvider() {
                @Override
                public String id() {
                    return "TMDB";
                }

                @Override
                public boolean serves(MediaCategory category) {
                    return category == MediaCategory.MOVIE;
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public List<CatalogEntry> search(MediaCategory category, String query) {
                    return List.of(MATRIX);
                }

                @Override
                public Optional<CatalogEntry> fetch(MediaCategory category, String externalId) {
                    return externalId.equals("603")
                            ? Optional.of(new CatalogEntry(MediaCategory.MOVIE, "TMDB", "603", "Matrix (fresh)", 1999, null, false))
                            : Optional.empty();
                }
            }));
        }
    }

    @Autowired
    MediaSnapshots snapshots;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from media_items");
    }

    private int rows() {
        return jdbc.queryForObject("select count(*) from media_items", Integer.class);
    }

    @Test
    void snapshottingTheSameTmdbIdTwiceKeepsOneRowAndRefreshesIt() {
        Snapshot first = snapshots.snapshot(MATRIX);
        Snapshot again = snapshots.snapshot(
                new CatalogEntry(MediaCategory.MOVIE, "TMDB", "603", "Matrix Reloaded?", 1999, null, false));

        assertThat(rows()).isEqualTo(1);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.title()).isEqualTo("Matrix Reloaded?");
        assertThat(again.imageUrl()).isNull();
        assertThat(first.year()).isEqualTo(1999);
    }

    @Test
    void differentCategoryOrIdAreDifferentItems() {
        snapshots.snapshot(MATRIX);
        snapshots.snapshot(new CatalogEntry(MediaCategory.SERIES, "TMDB", "603", "Other", null, null, false));
        snapshots.snapshot(new CatalogEntry(MediaCategory.MOVIE, "TMDB", "604", "Other", null, null, false));

        assertThat(rows()).isEqualTo(3);
    }

    @Test
    void concurrentSnapshotsOfTheSameWorkEndWithOneRow() {
        List<CompletableFuture<Snapshot>> calls = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> snapshots.snapshot(MATRIX))).toList();
        calls.forEach(CompletableFuture::join);

        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void snapshotFromCatalogTakesTheMetadataFromTheProviderNotTheCaller() {
        Optional<Snapshot> saved = snapshots.snapshotFromCatalog(MediaCategory.MOVIE, "603");

        assertThat(saved).get().extracting(Snapshot::title).isEqualTo("Matrix (fresh)");
        assertThat(snapshots.snapshotFromCatalog(MediaCategory.MOVIE, "1")).isEmpty();
        assertThat(snapshots.snapshotFromCatalog(MediaCategory.GAME, "603")).isEmpty();
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void databaseEnforcesTheCanonicalKeyAndCategoryEvenOutsideTheService() {
        jdbc.update("insert into media_items (category, canonical_provider, external_id, title) values ('MOVIE','TMDB','1','A')");

        assertThatThrownBy(() -> jdbc.update(
                "insert into media_items (category, canonical_provider, external_id, title) values ('MOVIE','TMDB','1','B')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into media_items (category, canonical_provider, external_id, title) values ('PODCAST','X','1','B')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void externalIdsMapOneSourceIdToOneItem() {
        long id = jdbc.queryForObject(
                "insert into media_items (category, canonical_provider, external_id, title) values ('MOVIE','TMDB','1','A') returning id",
                Long.class);
        jdbc.update("insert into media_external_ids (media_item_id, provider, external_id) values (?, 'LETTERBOXD', 'x')", id);

        assertThatThrownBy(() -> jdbc.update(
                "insert into media_external_ids (media_item_id, provider, external_id) values (?, 'LETTERBOXD', 'x')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("delete from media_items"); // cascades
        assertThat(jdbc.queryForObject("select count(*) from media_external_ids", Integer.class)).isZero();
    }
}
