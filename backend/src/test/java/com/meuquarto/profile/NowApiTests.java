package com.meuquarto.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.media.application.MediaSnapshots;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.profile.application.ProfileCollection;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The "Agora" block (FR-15, BR-06) against a real PostgreSQL: the owner's S10 API, the public profile that
 * shows it, and the sync side (a fake sync calls the same application method the real providers do).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, NowApiTests.Fakes.class})
class NowApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";
    private static final Instant T1 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-05T10:00:00Z");

    /** Serves every category, any id, never adult. */
    private record FakeCatalog() implements CatalogProvider {

        @Override
        public String catalogName() {
            return "FAKE";
        }

        @Override
        public boolean serves(MediaCategory category) {
            return true;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public List<CatalogEntry> search(MediaCategory category, String query) {
            return List.of();
        }

        @Override
        public Optional<CatalogEntry> fetch(MediaCategory category, String externalId) {
            return Optional.of(entry(category, externalId));
        }
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        CatalogRegistry fakeRegistry() {
            return new CatalogRegistry(List.of(new FakeCatalog()));
        }
    }

    private static CatalogEntry entry(MediaCategory category, String externalId) {
        return new CatalogEntry(category, "FAKE", externalId, "Title " + externalId, 2000,
                "https://img.example/" + externalId + ".jpg", false);
    }

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

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private long profileOf(String slug) {
        return jdbc.queryForObject("select id from profiles where slug = ?", Long.class, slug);
    }

    private long accountOf(long profileId) {
        jdbc.update("insert into linked_accounts (profile_id, provider, username) values (?, 'LETTERBOXD', 'someone') on conflict do nothing", profileId);
        return jdbc.queryForObject("select id from linked_accounts where profile_id = ?", Long.class, profileId);
    }

    /** What a sync does for one activity; returns the collection item id. */
    private long synced(String slug, MediaCategory category, String externalId, Instant occurredAt) {
        long profileId = profileOf(slug);
        long mediaId = snapshots.snapshot(entry(category, externalId)).id();
        collection.recordSyncedItem(profileId, category, mediaId, "LETTERBOXD", accountOf(profileId), occurredAt);
        return jdbc.queryForObject("select id from collection_items where profile_id = ? and media_item_id = ?",
                Long.class, profileId, mediaId);
    }

    private long addByHand(Cookie session, String category, String externalId) throws Exception {
        String body = mvc.perform(post("/api/collection").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"%s\",\"externalId\":\"%s\"}".formatted(category, externalId)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions change(Cookie session, String category, String mode, Long itemId) throws Exception {
        String body = itemId == null ? "{\"mode\":\"%s\"}".formatted(mode)
                : "{\"mode\":\"%s\",\"collectionItemId\":%d}".formatted(mode, itemId);
        return mvc.perform(put("/api/now/" + category).cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions overview(Cookie session) throws Exception {
        return mvc.perform(get("/api/now").cookie(session)).andExpect(status().isOk());
    }

    private ResultActions publicProfile(String slug) throws Exception {
        return mvc.perform(get("/api/public/profiles/" + slug)).andExpect(status().isOk());
    }

    // --- BR-06: automatic ---

    @Test
    void theLatestSyncedItemOfEachCategoryIsShownAutomatically_BR06() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        synced("leandro", MediaCategory.MOVIE, "old", T1);
        long latest = synced("leandro", MediaCategory.MOVIE, "new", T2);
        synced("leandro", MediaCategory.GAME, "g1", T1);

        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(2))
                .andExpect(jsonPath("$.now[0].category").value("MOVIE"))
                .andExpect(jsonPath("$.now[0].title").value("Title new"))
                .andExpect(jsonPath("$.now[0].ref").value(latest))
                .andExpect(jsonPath("$.now[0].source").value("LETTERBOXD"))
                .andExpect(jsonPath("$.now[0].at").exists())
                .andExpect(jsonPath("$.now[1].category").value("GAME"));

        overview(session).andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].category").value("MOVIE"))
                .andExpect(jsonPath("$[0].mode").value("AUTOMATIC"))
                .andExpect(jsonPath("$[0].automaticAvailable").value(true))
                .andExpect(jsonPath("$[0].shown.collectionItemId").value(latest))
                .andExpect(jsonPath("$[0].shown.source").value("LETTERBOXD"));
    }

    @Test
    void aNewSyncChangesTheAutomaticItemAndTheCachedProfileFollows_BR06() throws Exception {
        signUp("a@example.com", "leandro");
        synced("leandro", MediaCategory.MOVIE, "first", T1);
        publicProfile("leandro").andExpect(jsonPath("$.now[0].title").value("Title first"));

        synced("leandro", MediaCategory.MOVIE, "second", T2);

        publicProfile("leandro").andExpect(jsonPath("$.now[0].title").value("Title second"));
    }

    @Test
    void seriesHasNoAutomaticItemEvenIfSomethingSyncedIntoIt_BR06() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        synced("leandro", MediaCategory.SERIES, "s1", T1);

        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(0));
        overview(session).andExpect(jsonPath("$[1].category").value("SERIES"))
                .andExpect(jsonPath("$[1].automaticAvailable").value(false))
                .andExpect(jsonPath("$[1].shown").doesNotExist());
    }

    @Test
    void manuallyAddedItemsAreNeverPickedAutomatically_BR06() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        addByHand(session, "MOVIE", "by-hand");

        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(0));
    }

    @Test
    void aHiddenItemIsSkippedAndAdultWorksNeverAppear_BR06() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        synced("leandro", MediaCategory.MOVIE, "kept", T1);
        long hiddenOne = synced("leandro", MediaCategory.MOVIE, "hidden-one", T2);
        mvc.perform(patch("/api/collection/" + hiddenOne).cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")).andExpect(status().isOk());
        publicProfile("leandro").andExpect(jsonPath("$.now[0].title").value("Title kept"));

        synced("leandro", MediaCategory.MOVIE, "adult", Instant.parse("2026-10-09T10:00:00Z"));
        jdbc.update("update media_items set adult = true where external_id = 'adult'");
        // the profile is cached for a short time: a change by the owner evicts it
        change(session, "MOVIE", "AUTOMATIC", null).andExpect(status().isOk());

        publicProfile("leandro").andExpect(jsonPath("$.now[0].title").value("Title kept"));
    }

    // --- BR-06: pin, hide, back to automatic ---

    @Test
    void pinningWinsOverTheAutomaticItemAndClearingGoesBack_BR06() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        synced("leandro", MediaCategory.MOVIE, "auto", T2);
        long mine = addByHand(session, "MOVIE", "mine");
        publicProfile("leandro").andExpect(jsonPath("$.now[0].title").value("Title auto"));

        change(session, "MOVIE", "PINNED", mine).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PINNED"))
                .andExpect(jsonPath("$.shown.collectionItemId").value(mine))
                .andExpect(jsonPath("$.shown.source").value("MANUAL"))
                .andExpect(jsonPath("$.shown.at").doesNotExist());
        publicProfile("leandro").andExpect(jsonPath("$.now[0].title").value("Title mine"))
                .andExpect(jsonPath("$.now[0].source").value("MANUAL"));

        change(session, "MOVIE", "AUTOMATIC", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("AUTOMATIC"));
        publicProfile("leandro").andExpect(jsonPath("$.now[0].title").value("Title auto"));
        assertThat(rows("now_entries where collection_item_id is not null")).isZero();
    }

    @Test
    void seriesCanBePinnedByHand() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long series = addByHand(session, "SERIES", "s9");

        change(session, "SERIES", "PINNED", series).andExpect(status().isOk());

        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(1))
                .andExpect(jsonPath("$.now[0].category").value("SERIES"))
                .andExpect(jsonPath("$.now[0].title").value("Title s9"));
    }

    @Test
    void hidingACategoryRemovesItFromTheProfileAndShowingItAgainBringsTheAutomaticItemBack_BR06() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        synced("leandro", MediaCategory.MUSIC, "album", T1);
        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(1));

        change(session, "MUSIC", "HIDDEN", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("HIDDEN")).andExpect(jsonPath("$.shown").doesNotExist());
        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(0));

        change(session, "MUSIC", "AUTOMATIC", null).andExpect(status().isOk());
        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(1));
    }

    @Test
    void hidingThePinnedCollectionItemPutsTheCategoryBackOnAutomatic_BR06() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        synced("leandro", MediaCategory.MOVIE, "auto", T1);
        long mine = addByHand(session, "MOVIE", "mine");
        change(session, "MOVIE", "PINNED", mine).andExpect(status().isOk());

        mvc.perform(patch("/api/collection/" + mine).cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")).andExpect(status().isOk());

        overview(session).andExpect(jsonPath("$[0].mode").value("AUTOMATIC"))
                .andExpect(jsonPath("$[0].shown.title").value("Title auto"));
        publicProfile("leandro").andExpect(jsonPath("$.now[0].title").value("Title auto"));
    }

    @Test
    void removingThePinnedCollectionItemPutsTheCategoryBackOnAutomatic_BR06() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long mine = addByHand(session, "SERIES", "s1");
        change(session, "SERIES", "PINNED", mine).andExpect(status().isOk());
        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(1));

        mvc.perform(delete("/api/collection/" + mine).cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());

        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(0));
        overview(session).andExpect(jsonPath("$[1].mode").value("AUTOMATIC"));
        assertThat(rows("now_entries")).isZero();
    }

    // --- validation ---

    @Test
    void invalidChangesAreRefusedWithAReasonAndChangeNothing() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long movie = addByHand(session, "MOVIE", "m1");
        long book = addByHand(session, "BOOK", "b1");
        mvc.perform(patch("/api/collection/" + movie).cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")).andExpect(status().isOk());

        change(session, "MOVIE", "PINNED", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ITEM_REQUIRED"));
        change(session, "MOVIE", "HIDDEN", book).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNEXPECTED_ITEM"));
        change(session, "MOVIE", "PINNED", book).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CATEGORY_MISMATCH"));
        change(session, "MOVIE", "PINNED", movie).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ITEM_HIDDEN"));
        change(session, "MOVIE", "PINNED", 999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_IN_COLLECTION"));
        change(session, "MOVIE", "SIDEWAYS", null).andExpect(status().isBadRequest());
        change(session, "MOVIE", "PINNED", -1L).andExpect(status().isBadRequest());
        change(session, "GAMES", "AUTOMATIC", null).andExpect(status().isBadRequest());
        mvc.perform(put("/api/now/MOVIE").cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());

        assertThat(rows("now_entries where collection_item_id is not null or hidden")).isZero();
    }

    // --- authorization ---

    @Test
    void anonymousVisitorsAreRejectedOnEveryEndpoint() throws Exception {
        mvc.perform(get("/api/now")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/now/MOVIE").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"HIDDEN\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void changesWithoutTheCsrfTokenAreRejected() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        mvc.perform(put("/api/now/MOVIE").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"HIDDEN\"}")).andExpect(status().isForbidden());
        assertThat(rows("now_entries")).isZero();
    }

    @Test
    void anotherUsersItemLooksMissingAndTheirSettingsAreNeverTouched() throws Exception {
        Cookie owner = signUp("a@example.com", "leandro");
        Cookie other = signUp("b@example.com", "maria");
        long ownersItem = addByHand(owner, "MOVIE", "m1");
        long othersItem = addByHand(other, "MOVIE", "m2");
        change(other, "MOVIE", "PINNED", othersItem).andExpect(status().isOk());

        change(owner, "MOVIE", "PINNED", othersItem).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_IN_COLLECTION"));
        change(other, "MOVIE", "PINNED", ownersItem).andExpect(status().isNotFound());

        // the owner is untouched and the other user still shows their own pin
        overview(owner).andExpect(jsonPath("$[0].mode").value("AUTOMATIC"));
        overview(other).andExpect(jsonPath("$[0].mode").value("PINNED"))
                .andExpect(jsonPath("$[0].shown.collectionItemId").value(othersItem));
        publicProfile("leandro").andExpect(jsonPath("$.now.length()").value(0));
        publicProfile("maria").andExpect(jsonPath("$.now[0].title").value("Title m2"));
    }

    @Test
    void thePublicProfileNeverExposesHiddenEntriesOrOwnerData() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        synced("leandro", MediaCategory.MOVIE, "m1", T1);
        change(session, "MOVIE", "HIDDEN", null).andExpect(status().isOk());

        String json = publicProfile("leandro").andReturn().getResponse().getContentAsString();

        // the item stays in its category shelf; only the "Agora" entry is gone, and no flag or e-mail leaks
        assertThat(JsonPath.<List<Object>>read(json, "$.now")).isEmpty();
        assertThat(json).doesNotContain("hidden").doesNotContain("a@example.com").doesNotContain("MANUAL");
    }

    private long rows(String tableAndFilter) {
        return jdbc.queryForObject("select count(*) from " + tableAndFilter, Long.class);
    }
}
