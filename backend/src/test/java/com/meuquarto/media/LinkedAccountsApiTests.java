package com.meuquarto.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.media.application.MediaSnapshots;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.profile.application.ProfileCollection;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Linked accounts against a real PostgreSQL, with a fake provider behind the registry (no real platform). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LinkedAccountsApiTests.FakeProviders.class})
class LinkedAccountsApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";

    @TestConfiguration
    static class FakeProviders {

        @Bean
        ActivityProvider fakeActivityProvider() {
            return new FakeActivityProvider();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProfileCollection collection;

    @Autowired
    MediaSnapshots snapshots;

    @Autowired
    ActivityProvider provider;

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

    private ResultActions link(Cookie session, String provider, String username) throws Exception {
        return mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"%s\",\"username\":\"%s\"}".formatted(provider, username)));
    }

    private long linkLetterboxd(Cookie session, String username) throws Exception {
        String body = link(session, "LETTERBOXD", username).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private int checks() {
        return ((FakeActivityProvider) provider).checks.get();
    }

    // --- link ---

    @Test
    void linkingValidatesNormalizesAndStoresOnlyTheUsername() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        link(session, "LETTERBOXD", "  Leandro_01 ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.provider").value("LETTERBOXD"))
                .andExpect(jsonPath("$.username").value("leandro_01"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lastSyncedAt").doesNotExist())
                .andExpect(jsonPath("$.lastError").doesNotExist());

        assertThat(jdbc.queryForObject("select count(*) from linked_accounts", Integer.class)).isEqualTo(1);
    }

    @Test
    void aProfileHasOneAccountPerProvider() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        linkLetterboxd(session, "leandro");
        int checksBefore = checks();

        link(session, "LETTERBOXD", "other_name").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_LINKED"));

        assertThat(checks()).isEqualTo(checksBefore); // refused before any outbound request
        assertThat(jdbc.queryForObject("select count(*) from linked_accounts", Integer.class)).isEqualTo(1);
    }

    @Test
    void twoUsersCanLinkTheSameUsername() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");

        linkLetterboxd(ana, "leandro");
        linkLetterboxd(bia, "leandro");
    }

    @Test
    void anAccountThatDoesNotExistOnThePlatformIsRefused() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        link(session, "LETTERBOXD", "ghost").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND_ON_PROVIDER"));
        assertThat(jdbc.queryForObject("select count(*) from linked_accounts", Integer.class)).isZero();
    }

    @Test
    void aPlatformThatCannotAnswerIsReportedAsUnavailable() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        link(session, "LETTERBOXD", "down").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));
    }

    @Test
    void aPlatformWithoutAProviderIsUnavailable() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        link(session, "STEAM", "leandro").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../admin", "a?b=c", "a#frag", "x@evil.com", "a/b", "a b", "a%2fb", "ＬＥＡＮ", "caf\\u00e9",
            "a", "this_username_is_way_too_long", "http://evil.example/x", "a\\nb", "a.b"})
    void aMalformedUsernameIsRefusedBeforeAnyRequestToThePlatform(String username) throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        int checksBefore = checks();

        link(session, "LETTERBOXD", username).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_USERNAME"));

        assertThat(checks()).isEqualTo(checksBefore);
        assertThat(jdbc.queryForObject("select count(*) from linked_accounts", Integer.class)).isZero();
    }

    @Test
    void aVeryLongOrBlankUsernameIsRefusedByValidation() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        int checksBefore = checks();

        link(session, "LETTERBOXD", "a".repeat(5000)).andExpect(status().isBadRequest());
        link(session, "LETTERBOXD", "   ").andExpect(status().isBadRequest());
        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"NOPE\",\"username\":\"leandro\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/linked-accounts").cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"leandro\"}"))
                .andExpect(status().isBadRequest());

        assertThat(checks()).isEqualTo(checksBefore);
    }

    @Test
    void theOwnerIsTheSessionNotTheBody() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        mvc.perform(post("/api/linked-accounts").cookie(ana).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"LETTERBOXD\",\"username\":\"leandro\",\"profileId\":999,\"userId\":1}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/linked-accounts").cookie(bia)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/linked-accounts").cookie(ana)).andExpect(jsonPath("$.length()").value(1));
    }

    // --- list ---

    @Test
    void listingShowsOnlyTheOwnersAccounts() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        linkLetterboxd(ana, "ana_l");
        linkLetterboxd(bia, "bia_l");

        mvc.perform(get("/api/linked-accounts").cookie(ana)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("ana_l"))
                .andExpect(jsonPath("$[0].provider").value("LETTERBOXD"));
    }

    // --- unlink ---

    @Test
    void unlinkingDeletesTheAccount() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long id = linkLetterboxd(session, "leandro");

        mvc.perform(delete("/api/linked-accounts/" + id).cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/linked-accounts").cookie(session)).andExpect(jsonPath("$.length()").value(0));
        linkLetterboxd(session, "leandro"); // and it can be linked again
    }

    @Test
    void anotherUsersAccountLooksLikeAMissingOne() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long id = linkLetterboxd(ana, "ana_l");

        mvc.perform(delete("/api/linked-accounts/" + id).cookie(bia).with(RealCsrf.token(mvc)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("LINKED_ACCOUNT_NOT_FOUND"));
        mvc.perform(delete("/api/linked-accounts/987654").cookie(bia).with(RealCsrf.token(mvc)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("LINKED_ACCOUNT_NOT_FOUND"));

        mvc.perform(get("/api/linked-accounts").cookie(ana)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void fr16UnlinkingKeepsHighlightedAndNotedItemsAsManualAndDropsTheRest() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        long userId = jdbc.queryForObject("select id from users where email = 'a@example.com'", Long.class);
        long profileId = jdbc.queryForObject("select id from profiles where user_id = ?", Long.class, userId);
        long accountId = linkLetterboxd(session, "leandro");

        long onlySynced = synced(profileId, accountId, "1");
        long highlighted = synced(profileId, accountId, "2");
        long noted = synced(profileId, accountId, "3");
        long alsoManual = synced(profileId, accountId, "4");
        jdbc.update("insert into collection_item_sources (collection_item_id, source) values (?, 'MANUAL')", alsoManual);
        mvc.perform(post("/api/highlights").cookie(session).with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"MOVIE\",\"collectionItemId\":" + itemId(profileId, "2") + "}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/collection/" + itemId(profileId, "3")).cookie(session).with(RealCsrf.token(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"rever\"}")).andExpect(status().isOk());

        mvc.perform(delete("/api/linked-accounts/" + accountId).cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());

        assertThat(sourcesOf(profileId, "1")).isNull(); // item gone
        assertThat(sourcesOf(profileId, "2")).containsExactly("MANUAL");
        assertThat(sourcesOf(profileId, "3")).containsExactly("MANUAL");
        assertThat(sourcesOf(profileId, "4")).containsExactly("MANUAL");
        assertThat(jdbc.queryForObject("select count(*) from collection_item_sources where linked_account_id is not null",
                Integer.class)).isZero();
        assertThat(onlySynced + highlighted + noted + alsoManual).isPositive();
    }

    @Test
    void fr16ItemsOfAnotherUsersSameWorkAreNotAffected() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        long anaProfile = profileOf("a@example.com");
        long biaProfile = profileOf("b@example.com");
        long anaAccount = linkLetterboxd(ana, "ana_l");
        long biaAccount = linkLetterboxd(bia, "bia_l");
        synced(anaProfile, anaAccount, "1");
        synced(biaProfile, biaAccount, "1");

        mvc.perform(delete("/api/linked-accounts/" + anaAccount).cookie(ana).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());

        assertThat(sourcesOf(anaProfile, "1")).isNull();
        assertThat(sourcesOf(biaProfile, "1")).containsExactly("LETTERBOXD");
    }

    @Test
    void deletingTheUserDeletesTheirLinkedAccounts() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        linkLetterboxd(session, "leandro");

        jdbc.update("delete from users");

        assertThat(jdbc.queryForObject("select count(*) from linked_accounts", Integer.class)).isZero();
    }

    // --- authorization ---

    @Test
    void anonymousCallsAreRefused() throws Exception {
        mvc.perform(get("/api/linked-accounts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/linked-accounts").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"LETTERBOXD\",\"username\":\"leandro\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/linked-accounts/1").with(RealCsrf.token(mvc))).andExpect(status().isUnauthorized());
    }

    @Test
    void changesWithoutACsrfTokenAreRefused() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        mvc.perform(post("/api/linked-accounts").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"LETTERBOXD\",\"username\":\"leandro\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/linked-accounts/1").cookie(session)).andExpect(status().isForbidden());
    }

    // --- helpers ---

    private long profileOf(String email) {
        return jdbc.queryForObject("select p.id from profiles p join users u on u.id = p.user_id where u.email = ?",
                Long.class, email);
    }

    /** What a sync does: snapshot the work, then record it with its source and account. */
    private long synced(long profileId, long accountId, String externalId) {
        long mediaId = snapshots.snapshot(
                new CatalogEntry(MediaCategory.MOVIE, "FAKE", externalId, "Work " + externalId, 2000, null, false)).id();
        collection.recordSyncedItem(profileId, MediaCategory.MOVIE, mediaId, "LETTERBOXD", accountId);
        return itemId(profileId, externalId);
    }

    private long itemId(long profileId, String externalId) {
        return jdbc.queryForObject("""
                select i.id from collection_items i join media_items m on m.id = i.media_item_id
                where i.profile_id = ? and m.external_id = ?""", Long.class, profileId, externalId);
    }

    /** The sources of the item, or null when the item no longer exists. */
    private List<String> sourcesOf(long profileId, String externalId) {
        List<Long> ids = jdbc.queryForList("""
                select i.id from collection_items i join media_items m on m.id = i.media_item_id
                where i.profile_id = ? and m.external_id = ?""", Long.class, profileId, externalId);
        if (ids.isEmpty()) {
            return null;
        }
        return jdbc.queryForList("select source from collection_item_sources where collection_item_id = ? order by source",
                String.class, ids.getFirst());
    }
}
