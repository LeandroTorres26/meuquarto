package com.meuquarto.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meuquarto.media.application.MediaSnapshots;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.profile.application.ProfileCollection;
import com.meuquarto.profile.domain.CollectionException;
import com.meuquarto.profile.domain.CollectionSort;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-09 and BR-10 from the sync side: a fake "sync" calls the same application method the real
 * providers will, and the owner's choices (hidden items) survive it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CollectionSyncTests {

    private static final CatalogEntry MATRIX =
            new CatalogEntry(MediaCategory.MOVIE, "FAKE", "603", "Matrix", 1999, null, false);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProfileCollection collection;

    @Autowired
    MediaSnapshots snapshots;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    private long signUp() throws Exception {
        mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"cavalo bateria grampo\",\"slug\":\"leandro\"}"))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from users where email = 'a@example.com'", Long.class);
    }

    private long profileOf(long userId) {
        return jdbc.queryForObject("select id from profiles where user_id = ?", Long.class, userId);
    }

    private ProfileCollection.Listing visible(long userId) {
        return collection.list(userId, MediaCategory.MOVIE, CollectionSort.RECENT, 0, 50, false);
    }

    @Test
    void aSyncedItemAppearsOnceEvenIfTheSyncRunsAgain() throws Exception {
        long userId = signUp();
        long profileId = profileOf(userId);
        long mediaId = snapshots.snapshot(MATRIX).id();

        collection.recordSyncedItem(profileId, MediaCategory.MOVIE, mediaId, "LETTERBOXD", 7L);
        collection.recordSyncedItem(profileId, MediaCategory.MOVIE, mediaId, "LETTERBOXD", 7L);

        ProfileCollection.Listing listing = visible(userId);
        assertThat(listing.total()).isEqualTo(1);
        assertThat(listing.items().getFirst().sources()).containsExactly("LETTERBOXD");
        assertThat(listing.items().getFirst().removable()).isFalse();
    }

    @Test
    void aHiddenSyncedItemStaysHiddenAfterTheNextSync() throws Exception {
        long userId = signUp();
        long profileId = profileOf(userId);
        long mediaId = snapshots.snapshot(MATRIX).id();
        collection.recordSyncedItem(profileId, MediaCategory.MOVIE, mediaId, "LETTERBOXD", 7L);
        long itemId = visible(userId).items().getFirst().id();

        collection.update(userId, itemId, null, true);
        collection.recordSyncedItem(profileId, MediaCategory.MOVIE, mediaId, "LETTERBOXD", 7L);
        collection.recordSyncedItem(profileId, MediaCategory.MOVIE, mediaId, "LETTERBOXD", 7L);

        assertThat(visible(userId).total()).isZero();
        ProfileCollection.Listing withHidden = collection.list(userId, MediaCategory.MOVIE, CollectionSort.RECENT, 0, 50, true);
        assertThat(withHidden.total()).isEqualTo(1);
        assertThat(withHidden.items().getFirst().hidden()).isTrue();
    }

    @Test
    void aWorkAddedByHandThenSyncedBecomesOneItemWithBothSources() throws Exception {
        long userId = signUp();
        long profileId = profileOf(userId);
        long mediaId = snapshots.snapshot(MATRIX).id();
        jdbc.update("insert into collection_items (profile_id, media_item_id, category) values (?, ?, 'MOVIE')", profileId, mediaId);
        long itemId = jdbc.queryForObject("select id from collection_items", Long.class);
        jdbc.update("insert into collection_item_sources (collection_item_id, source) values (?, 'MANUAL')", itemId);

        collection.recordSyncedItem(profileId, MediaCategory.MOVIE, mediaId, "LETTERBOXD", 7L);

        ProfileCollection.Listing listing = visible(userId);
        assertThat(listing.total()).isEqualTo(1);
        assertThat(listing.items().getFirst().sources()).containsExactly("LETTERBOXD", "MANUAL");
        assertThat(List.of(listing.items().getFirst().removable())).containsExactly(false);
    }

    @Test
    void anOwnerWithoutAnItemCannotTouchAnotherOnesThroughTheService() throws Exception {
        long userId = signUp();
        long profileId = profileOf(userId);
        long mediaId = snapshots.snapshot(MATRIX).id();
        collection.recordSyncedItem(profileId, MediaCategory.MOVIE, mediaId, "LETTERBOXD", 7L);
        long itemId = visible(userId).items().getFirst().id();

        long strangerUserId = userId + 1000;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> collection.update(strangerUserId, itemId, "x", null))
                .isInstanceOf(CollectionException.class);
    }
}
