package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.shared.domain.MediaCategory;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The AniList class as catalog and as activity source, against a local stub; nothing here reaches the real AniList. */
class AniListProviderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AniListStub stub;
    private AniListProvider anilist;

    @BeforeEach
    void start() {
        stub = new AniListStub();
        anilist = new AniListProvider(HttpCatalogProvider.newClient(), Clock.systemUTC(), Duration.ofMinutes(10),
                stub.uri(), Duration.ofSeconds(2));
    }

    @AfterEach
    void stop() {
        stub.close();
    }

    // ---- one class, two interfaces ----

    @Test
    void oneObjectIsACatalogProviderAndAnActivityProvider() {
        CatalogProvider asCatalog = anilist;
        ActivityProvider asActivity = anilist;

        assertThat(asCatalog).isSameAs(asActivity).isInstanceOf(HttpCatalogProvider.class);
        assertThat(asCatalog.catalogName()).isEqualTo("ANILIST");
        assertThat(asActivity.id()).isEqualTo(ProviderId.ANILIST);
        assertThat(asCatalog.serves(MediaCategory.ANIME)).isTrue();
        assertThat(asCatalog.serves(MediaCategory.MANGA)).isTrue();
        assertThat(asCatalog.serves(MediaCategory.MOVIE)).isFalse();
        assertThat(asActivity.categories()).containsExactlyInAnyOrder(MediaCategory.ANIME, MediaCategory.MANGA);
        assertThat(asCatalog.isAvailable()).isTrue();
    }

    // ---- catalog ----

    @Test
    void searchNormalizesAnimeAndDropsAdultAndBrokenEntries() {
        List<CatalogEntry> results = anilist.search(MediaCategory.ANIME, "bebop");

        assertThat(results).extracting(CatalogEntry::externalId).containsExactly("1", "2", "4", "5");
        assertThat(results.get(0)).isEqualTo(new CatalogEntry(MediaCategory.ANIME, "ANILIST", "1", "Cowboy Bebop", 1998,
                "https://s4.anilist.co/file/anilistcdn/media/anime/cover/medium/bx1-GCsPm7waJ4kS.png", false));
    }

    @Test
    void theTitleFallsBackToEnglishAndTheYearToTheSeasonYear() {
        CatalogEntry onlyEnglish = anilist.search(MediaCategory.ANIME, "x").get(1);

        assertThat(onlyEnglish.title()).isEqualTo("Only English Title");
        assertThat(onlyEnglish.year()).isEqualTo(2005); // startDate.year is null, seasonYear is used
        assertThat(onlyEnglish.imageUrl()).isNull(); // missing cover
    }

    @Test
    void aCoverOutsideTheAllowedHostOrWithoutHttpsIsDropped() {
        List<CatalogEntry> results = anilist.search(MediaCategory.ANIME, "x");

        assertThat(results.get(2).imageUrl()).isNull(); // evil.example
        assertThat(results.get(3).imageUrl()).isNull(); // http, not https
        assertThat(results.get(3).title()).isEqualTo("<script>alert(1)</script>"); // plain text, the UI escapes
    }

    @Test
    void searchesMangaWithTheMangaType() {
        List<CatalogEntry> results = anilist.search(MediaCategory.MANGA, "berserk");

        assertThat(results).singleElement().satisfies(entry -> {
            assertThat(entry.category()).isEqualTo(MediaCategory.MANGA);
            assertThat(entry.externalId()).isEqualTo("30002");
        });
        assertThat(variables(0).path("type").asString("")).isEqualTo("MANGA");
    }

    @Test
    void fetchReturnsOneWorkAndRejectsIdsThatAreNotNumbers() {
        Optional<CatalogEntry> frieren = anilist.fetch(MediaCategory.ANIME, "154587");

        assertThat(frieren).get().extracting(CatalogEntry::title).isEqualTo("Sousou no Frieren");
        assertThat(variables(0).path("id").asLong()).isEqualTo(154587);

        stub.requests.clear();
        assertThat(anilist.fetch(MediaCategory.ANIME, "1 OR 1=1")).isEmpty();
        assertThat(anilist.fetch(MediaCategory.ANIME, "../x")).isEmpty();
        assertThat(stub.requests).isEmpty();
    }

    @Test
    void userInputTravelsOnlyInTheVariablesNeverInTheQuery() {
        String nasty = "\"){ id } } # \\ {{ $evil";

        anilist.search(MediaCategory.ANIME, nasty);

        JsonNode request = request(0);
        assertThat(request.path("query").asString("")).doesNotContain("evil");
        assertThat(request.path("variables").path("search").asString("")).isEqualTo(nasty);
        assertThat(stub.requests.get(0).method()).isEqualTo("POST");
        assertThat(stub.requests.get(0).path()).isEqualTo("/"); // fixed endpoint, nothing from the user in the URL
    }

    @Test
    void theSameSearchIsServedFromTheCache() {
        anilist.search(MediaCategory.ANIME, "bebop");
        anilist.search(MediaCategory.ANIME, "bebop");
        anilist.search(MediaCategory.MANGA, "bebop"); // another type is another request

        assertThat(stub.requests).hasSize(2);
    }

    @Test
    void failuresAnswerEmptyNeverAnException() {
        for (int status : new int[] {429, 500, 503}) {
            stub.status = status;
            assertThat(anilist.search(MediaCategory.ANIME, "s" + status)).isEmpty();
            assertThat(anilist.fetch(MediaCategory.ANIME, "1")).isEmpty();
        }
        stub.status = 200;
        stub.forcedBody = "{not json";
        assertThat(anilist.search(MediaCategory.ANIME, "malformed")).isEmpty();
        stub.forcedBody = "x".repeat(HttpCatalogProvider.MAX_BODY_BYTES + 10);
        assertThat(anilist.search(MediaCategory.ANIME, "huge")).isEmpty();
        stub.forcedBody = "{\"data\":null}";
        assertThat(anilist.search(MediaCategory.ANIME, "nodata")).isEmpty();
    }

    @Test
    void aCategoryItDoesNotServeNeverReachesTheNetwork() {
        assertThat(anilist.search(MediaCategory.MOVIE, "matrix")).isEmpty();
        assertThat(stub.requests).isEmpty();
    }

    // ---- activity: username ----

    @Test
    void usernameRulesAcceptTheUsualNamesAndRefuseTheRest() {
        assertThat(anilist.normalizeUsername("  Ana_L ")).isEqualTo("ana_l");
        assertThat(anilist.validateUsernameFormat("ana_l")).isEmpty();
        assertThat(anilist.validateUsernameFormat("a1")).isEmpty();
        assertThat(anilist.validateUsernameFormat("a".repeat(20))).isEmpty();

        for (String bad : new String[] {"", "a", "a".repeat(21), "ana l", "../../etc/passwd", "ana\"){", "ana-l",
                "anaç", "a%20b", "ana\nl", "<script>"}) {
            assertThat(anilist.validateUsernameFormat(bad)).as(bad).isPresent();
        }
    }

    @Test
    void maliciousUsernamesNeverReachTheServer() {
        for (String bad : new String[] {"../../x", "ana\"){ id }", "a b", "", "a".repeat(40), null}) {
            assertThat(anilist.checkAccountExists(bad)).isEqualTo(AccountCheck.UNAVAILABLE);
            assertThatThrownBy(() -> anilist.fetchActivity(bad)).isInstanceOf(LinkedAccountException.class);
        }
        assertThat(stub.requests).isEmpty();
    }

    // ---- activity: existence ----

    @Test
    void anExistingUserIsFoundWithOneRequestAndTheNameInTheVariables() {
        assertThat(anilist.checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS);

        assertThat(stub.requests).hasSize(1);
        assertThat(variables(0).path("name").asString("")).isEqualTo("ana_l");
        assertThat(request(0).path("query").asString("")).doesNotContain("ana_l");
    }

    @Test
    void aMissingOrPrivateUserIsNotFound() {
        assertThat(anilist.checkAccountExists("nobody")).isEqualTo(AccountCheck.NOT_FOUND);
    }

    @Test
    void aProviderFailureWhileCheckingIsUnavailableNotNotFound() {
        for (int status : new int[] {429, 500}) {
            stub.status = status;
            assertThat(anilist.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        }
        stub.status = 200;
        stub.forcedBody = "{not json";
        assertThat(anilist.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        stub.forcedBody = "{\"data\":{\"User\":null}}";
        assertThat(anilist.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    // ---- activity: lists (BR-04, BR-10, BR-08) ----

    @Test
    void theListMapsEveryAcceptedStatusAndSkipsTheRest() {
        List<ActivityEntry> entries = anilist.fetchActivity("ana_l").entries();

        // anime: current, completed (full date), partial date, impossible date, paused, repeating; manga: completed, current
        assertThat(entries).extracting(entry -> entry.work().externalId())
                .containsExactlyInAnyOrder("154587", "139630", "100", "101", "200", "300", "30002", "30013");
        // not in the list: planning 400, dropped 500, adult 666, only in a custom list 777, entry without id 0

        assertThat(of(entries, "139630")).satisfies(entry -> {
            assertThat(entry.type()).isEqualTo(ActivityType.FINISHED);
            assertThat(entry.occurredAt()).isEqualTo(Instant.parse("2025-05-24T00:00:00Z"));
            assertThat(entry.work().category()).isEqualTo(MediaCategory.ANIME);
            assertThat(entry.work().provider()).isEqualTo("ANILIST");
        });
        assertThat(of(entries, "154587").type()).isEqualTo(ActivityType.IN_PROGRESS);
        assertThat(of(entries, "154587").occurredAt()).isEqualTo(Instant.parse("2025-05-28T00:00:00Z"));
        assertThat(of(entries, "200").type()).isEqualTo(ActivityType.IN_PROGRESS); // paused
        assertThat(of(entries, "300").type()).isEqualTo(ActivityType.FINISHED); // repeating
        assertThat(of(entries, "30002").work().category()).isEqualTo(MediaCategory.MANGA);
    }

    @Test
    void adultEntriesNeverLeaveTheProvider() {
        // BR-08, twice: the sync drops adult works for every platform and this provider does not even return them.
        List<ActivityEntry> entries = anilist.fetchActivity("ana_l").entries();

        assertThat(entries).noneMatch(entry -> entry.work().adult());
    }

    @Test
    void theTimeFallsBackToTheLastUpdateWhenTheDateIsPartialOrImpossible() {
        List<ActivityEntry> entries = anilist.fetchActivity("ana_l").entries();

        assertThat(of(entries, "100").occurredAt()).isEqualTo(Instant.ofEpochSecond(1700000000L));
        assertThat(of(entries, "101").occurredAt()).isEqualTo(Instant.ofEpochSecond(1690000000L)); // 30 February
    }

    @Test
    void theEntriesComeNewestFirstAndOnlyAcceptedStatusesAreAskedFor() {
        List<ActivityEntry> entries = anilist.fetchActivity("ana_l").entries();

        List<Instant> times = entries.stream().map(ActivityEntry::occurredAt).toList();
        assertThat(times).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(stub.requests).hasSize(2); // anime and manga, nothing else
        assertThat(request(0).path("query").asString("")).contains("COMPLETED", "CURRENT", "PAUSED", "REPEATING")
                .doesNotContain("PLANNING", "DROPPED");
        assertThat(variables(0).path("type").asString("")).isEqualTo("ANIME");
        assertThat(variables(1).path("type").asString("")).isEqualTo("MANGA");
        assertThat(variables(1).path("name").asString("")).isEqualTo("ana_l");
    }

    @Test
    void aSyncReturnsAtMostFiveHundredEntries() {
        StringBuilder entries = new StringBuilder();
        for (int i = 1; i <= AniListProvider.MAX_ENTRIES + 50; i++) {
            entries.append(i > 1 ? "," : "").append("""
                    {"status":"COMPLETED","updatedAt":%d,"completedAt":{"year":null,"month":null,"day":null},
                     "media":{"id":%d,"isAdult":false,"title":{"romaji":"Work %d"}}}""".formatted(1_600_000_000L + i, i, i));
        }
        stub.forcedBody = "{\"data\":{\"MediaListCollection\":{\"lists\":[{\"isCustomList\":false,\"entries\":["
                + entries + "]}]}}}";

        assertThat(anilist.fetchActivity("ana_l").entries()).hasSize(AniListProvider.MAX_ENTRIES);
    }

    @Test
    void anEmptyCollectionIsAnEmptySnapshotNotAFailure() {
        stub.forcedBody = "{\"data\":{\"MediaListCollection\":{\"lists\":[]}}}";

        assertThat(anilist.fetchActivity("ana_l").entries()).isEmpty();
    }

    @Test
    void providerFailuresDuringTheFetchThrowProviderUnavailable() {
        for (int status : new int[] {404, 429, 500}) {
            stub.status = status;
            assertThatThrownBy(() -> anilist.fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class)
                    .extracting(e -> ((LinkedAccountException) e).problem())
                    .isEqualTo(LinkedAccountException.Problem.PROVIDER_UNAVAILABLE);
        }
        stub.status = 200;
        for (String broken : new String[] {"{not json", "{\"data\":{\"MediaListCollection\":null}}",
                "x".repeat(HttpCatalogProvider.MAX_BODY_BYTES + 10)}) {
            stub.forcedBody = broken;
            assertThatThrownBy(() -> anilist.fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
        }
    }

    @Test
    void anUnreachableServerIsAProviderFailureToo() {
        AniListProvider unreachable = new AniListProvider(HttpCatalogProvider.newClient(), Clock.systemUTC(),
                Duration.ofMinutes(1), URI.create("http://127.0.0.1:1"), Duration.ofMillis(300));

        assertThat(unreachable.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(unreachable.search(MediaCategory.ANIME, "x")).isEmpty();
        assertThatThrownBy(() -> unreachable.fetchActivity("ana_l")).isInstanceOf(LinkedAccountException.class);
    }

    // ---- helpers ----

    private static ActivityEntry of(List<ActivityEntry> entries, String externalId) {
        return entries.stream().filter(entry -> entry.work().externalId().equals(externalId)).findFirst().orElseThrow();
    }

    private JsonNode request(int index) {
        return JSON.readTree(stub.requests.get(index).body());
    }

    private JsonNode variables(int index) {
        return request(index).path("variables");
    }
}
