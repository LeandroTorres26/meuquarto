package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityType;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.shared.domain.MediaCategory;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MyAnimeList leaf against MyAnimeList-shaped answers from a local stub, mapped through an AniList stub
 * (no real network, no real client id). BR-10 (which statuses count), ADR 0005 (the work is the AniList
 * work) and the safety limits of the provider.
 */
class MyAnimeListProviderTest {

    /** A fresh random id per run: nothing in the repository looks like a credential. */
    private static final String CLIENT_ID = UUID.randomUUID().toString().replace("-", "");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String UPDATED = "2026-10-01T10:00:00+00:00";

    private static MyAnimeListStub mal;
    private static AniListStub ani;

    @BeforeAll
    static void startStubs() {
        mal = new MyAnimeListStub(CLIENT_ID);
        ani = new AniListStub();
    }

    @AfterAll
    static void stopStubs() {
        mal.close();
        ani.close();
    }

    @BeforeEach
    void resetStubs() {
        mal.reset();
        ani.reset();
        mal.user("ana_l", List.of(), List.of());
    }

    private static MyAnimeListProvider provider() {
        return provider(CLIENT_ID, Duration.ofSeconds(2), mal.uri());
    }

    private static MyAnimeListProvider provider(String clientId, Duration timeout, URI endpoint) {
        AniListProvider anilist = new AniListProvider(HttpCatalogProvider.newClient(), Clock.systemUTC(),
                Duration.ofMinutes(10), ani.uri(), Duration.ofSeconds(2));
        return new MyAnimeListProvider(HttpCatalogProvider.newClient(), endpoint, clientId, anilist, timeout, Duration.ZERO);
    }

    private static List<ActivityEntry> entries() {
        return provider().fetchActivity("ana_l").entries();
    }

    /** Maps MAL ids to AniList ids 1000 higher (anime) and 2000 higher (manga), so tests read easily. */
    private static void knowAnime(long... malIds) {
        Map<Long, Long> map = new java.util.HashMap<>();
        for (long id : malIds) {
            map.put(id, 1000 + id);
        }
        ani.malToAniList.put("ANIME", map);
    }

    private static void knowManga(long... malIds) {
        Map<Long, Long> map = new java.util.HashMap<>();
        for (long id : malIds) {
            map.put(id, 2000 + id);
        }
        ani.malToAniList.put("MANGA", map);
    }

    /** The MAL ids sent to AniList, one list per request. */
    private static List<List<Long>> lookups() {
        List<List<Long>> batches = new ArrayList<>();
        for (AniListStub.Request request : ani.requests) {
            JsonNode ids = JSON.readTree(request.body()).path("variables").path("ids");
            List<Long> batch = new ArrayList<>();
            ids.forEach(id -> batch.add(id.asLong()));
            batches.add(batch);
        }
        return batches;
    }

    private static void assertProblem(Runnable call, Problem problem) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(LinkedAccountException.class,
                e -> assertThat(e.problem()).isEqualTo(problem));
    }

    // ---- identity ----

    @Test
    void itIsTheMyAnimeListProviderForAnimeAndMangaAndImplementsTheInterfaceDirectly() {
        MyAnimeListProvider real = provider();
        ActivityProvider asInterface = real; // callers only ever hold the base type

        assertThat(asInterface.id()).isEqualTo(ProviderId.MYANIMELIST);
        assertThat(asInterface.categories()).containsExactlyInAnyOrder(MediaCategory.ANIME, MediaCategory.MANGA);
        assertThat(MyAnimeListProvider.ENDPOINT).isEqualTo(URI.create("https://api.myanimelist.net/v2/"));
        assertThat(real.getClass().getSuperclass()).isEqualTo(Object.class);
        assertThat(java.lang.reflect.Modifier.isFinal(real.getClass().getModifiers())).isTrue();
    }

    // ---- BR-10: which statuses count ----

    @Test
    void br10_completedWatchingAndOnHoldCountAndPlanToWatchAndDroppedNeverDo() {
        mal.user("ana_l", List.of(
                MyAnimeListStub.anime(1, "completed", UPDATED),
                MyAnimeListStub.anime(2, "watching", UPDATED),
                MyAnimeListStub.anime(3, "on_hold", UPDATED),
                MyAnimeListStub.anime(4, "plan_to_watch", UPDATED),
                MyAnimeListStub.anime(5, "dropped", UPDATED),
                MyAnimeListStub.anime(6, "something_new", UPDATED)), List.of());
        knowAnime(1, 2, 3, 4, 5, 6);

        List<ActivityEntry> entries = entries();

        assertThat(entries).extracting(e -> e.work().externalId()).containsExactlyInAnyOrder("1001", "1002", "1003");
        assertThat(entries).filteredOn(e -> e.work().externalId().equals("1001"))
                .extracting(ActivityEntry::type).containsExactly(ActivityType.FINISHED);
        assertThat(entries).filteredOn(e -> !e.work().externalId().equals("1001"))
                .extracting(ActivityEntry::type).containsOnly(ActivityType.IN_PROGRESS);
        assertThat(lookups().stream().flatMap(List::stream)).containsExactlyInAnyOrder(1L, 2L, 3L); // the others never leave
    }

    @Test
    void br10_mangaCompletedReadingAndOnHoldCountAndPlanToReadAndDroppedNeverDo() {
        mal.user("ana_l", List.of(), List.of(
                MyAnimeListStub.manga(21, "completed", UPDATED),
                MyAnimeListStub.manga(22, "reading", UPDATED),
                MyAnimeListStub.manga(23, "on_hold", UPDATED),
                MyAnimeListStub.manga(24, "plan_to_read", UPDATED),
                MyAnimeListStub.manga(25, "dropped", UPDATED),
                MyAnimeListStub.manga(26, "watching", UPDATED))); // not a manga status
        knowManga(21, 22, 23, 24, 25, 26);

        List<ActivityEntry> entries = entries();

        assertThat(entries).extracting(e -> e.work().externalId()).containsExactlyInAnyOrder("2021", "2022", "2023");
        assertThat(entries).extracting(e -> e.work().category()).containsOnly(MediaCategory.MANGA);
    }

    @Test
    void br10_rewatchingAndRereadingCountAsFinishedButNeverWhenPlannedOrDropped() {
        mal.user("ana_l", List.of(
                MyAnimeListStub.entry(1, "completed", true, false, null, null, UPDATED),
                MyAnimeListStub.entry(2, "watching", true, false, null, null, UPDATED),
                MyAnimeListStub.entry(3, "dropped", true, false, null, null, UPDATED),
                MyAnimeListStub.entry(4, "plan_to_watch", true, false, null, null, UPDATED)),
                List.of(MyAnimeListStub.entry(21, "reading", true, true, null, null, UPDATED),
                        MyAnimeListStub.entry(22, "plan_to_read", true, true, null, null, UPDATED)));
        knowAnime(1, 2, 3, 4);
        knowManga(21, 22);

        List<ActivityEntry> entries = entries();

        assertThat(entries).extracting(e -> e.work().externalId()).containsExactlyInAnyOrder("1001", "1002", "2021");
        assertThat(entries).extracting(ActivityEntry::type).containsOnly(ActivityType.FINISHED);
    }

    // ---- time ----

    @Test
    void theTimeIsTheFullFinishOrStartDateOtherwiseTheLastUpdate() {
        mal.user("ana_l", List.of(
                MyAnimeListStub.entry(1, "completed", false, false, "2025-03-01", "2025-05-24", UPDATED),
                MyAnimeListStub.entry(2, "watching", false, false, "2025-06-02", "2025-07-01", UPDATED),
                MyAnimeListStub.entry(3, "completed", false, false, null, "2025-05", UPDATED), // partial date
                MyAnimeListStub.entry(4, "completed", false, false, null, "2025", UPDATED),
                MyAnimeListStub.entry(5, "completed", false, false, null, "2025-02-30", UPDATED), // impossible day
                MyAnimeListStub.entry(6, "completed", false, false, null, null, "2026-10-02T13:30:00-03:00"),
                MyAnimeListStub.entry(7, "completed", false, false, null, null, "yesterday"), // no usable time at all
                MyAnimeListStub.entry(8, "completed", false, false, null, "1500-01-01", UPDATED)), List.of());
        knowAnime(1, 2, 3, 4, 5, 6, 7, 8);

        Map<String, Instant> byWork = new java.util.HashMap<>();
        entries().forEach(e -> byWork.put(e.work().externalId(), e.occurredAt()));

        assertThat(byWork).hasSize(7); // entry 7 has no time
        assertThat(byWork.get("1001")).isEqualTo(Instant.parse("2025-05-24T00:00:00Z")); // finish date, not start
        assertThat(byWork.get("1002")).isEqualTo(Instant.parse("2025-06-02T00:00:00Z")); // start date, not finish
        assertThat(byWork.get("1003")).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(byWork.get("1004")).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(byWork.get("1005")).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(byWork.get("1006")).isEqualTo(Instant.parse("2026-10-02T16:30:00Z"));
        assertThat(byWork.get("1008")).isEqualTo(Instant.parse("2026-10-01T10:00:00Z")); // year out of range: ignored
    }

    @Test
    void entriesComeNewestFirstAcrossAnimeAndManga() {
        mal.user("ana_l",
                List.of(MyAnimeListStub.anime(1, "completed", "2026-01-01T00:00:00+00:00")),
                List.of(MyAnimeListStub.manga(21, "completed", "2026-03-01T00:00:00+00:00"),
                        MyAnimeListStub.manga(22, "completed", "2026-02-01T00:00:00+00:00")));
        knowAnime(1);
        knowManga(21, 22);

        assertThat(entries()).extracting(e -> e.work().externalId()).containsExactly("2021", "2022", "1001");
    }

    // ---- ADR 0005: the work is the AniList work ----

    @Test
    void theWorkIsTheCanonicalAniListWorkWithNothingFromMyAnimeList() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(52991, "watching", UPDATED)), List.of());
        ani.malToAniList.put("ANIME", Map.of(52991L, 154587L));

        ActivityEntry entry = entries().getFirst();

        assertThat(entry.work().provider()).isEqualTo("ANILIST");
        assertThat(entry.work().category()).isEqualTo(MediaCategory.ANIME);
        assertThat(entry.work().externalId()).isEqualTo("154587");
        assertThat(entry.work().title()).isEqualTo("Work 154587").doesNotContain("MAL title"); // AniList's title
        assertThat(entry.work().imageUrl()).startsWith("https://s4.anilist.co/").doesNotContain("myanimelist");
        assertThat(entry.work().year()).isEqualTo(2020);
    }

    @Test
    void animeAndMangaWithTheSameMalIdAreLookedUpByTypeSoTheyNeverCross() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(7, "completed", UPDATED)),
                List.of(MyAnimeListStub.manga(7, "completed", UPDATED)));
        ani.malToAniList.put("ANIME", Map.of(7L, 111L));
        ani.malToAniList.put("MANGA", Map.of(7L, 222L));

        assertThat(entries()).extracting(e -> e.work().category() + ":" + e.work().externalId())
                .containsExactlyInAnyOrder("ANIME:111", "MANGA:222");
    }

    @Test
    void anEntryWithoutAnAniListMatchIsSkippedAndTheOthersStay() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(1, "completed", UPDATED),
                MyAnimeListStub.anime(2, "completed", UPDATED)), List.of());
        knowAnime(1); // AniList does not know MAL 2

        assertThat(entries()).extracting(e -> e.work().externalId()).containsExactly("1001");
    }

    @Test
    void theSkippedEntriesAreCountedInOneWarningWithoutTheirTitles() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(1, "completed", UPDATED),
                MyAnimeListStub.anime(2, "completed", UPDATED), MyAnimeListStub.anime(3, "completed", UPDATED)), List.of());
        knowAnime(1);
        ListAppender<ILoggingEvent> logs = captureLogs();

        entries();

        List<String> messages = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(m -> m.contains("skipped 2 ANIME entries without an AniList match"));
        assertThat(String.join("\n", messages)).doesNotContain("MAL title");
        detach(logs);
    }

    @Test
    void twoMalEntriesOfTheSameAniListWorkAddItOnce() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(1, "completed", UPDATED),
                MyAnimeListStub.anime(2, "completed", UPDATED)), List.of());
        ani.malToAniList.put("ANIME", Map.of(1L, 500L, 2L, 500L));

        assertThat(entries()).hasSize(1);
    }

    @Test
    void theAniListWorkCarriesTheMalIdItWasFoundBy() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(7, "completed", UPDATED)), List.of());
        knowAnime(7);

        assertThat(entries().getFirst().work().hints().malId()).isEqualTo(7); // so the MyAnimeList page can be linked
    }

    @Test
    void anAdultWorkNeverComesOut() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(1, "completed", UPDATED),
                MyAnimeListStub.anime(2, "completed", UPDATED)), List.of());
        knowAnime(1, 2);
        ani.adultAniList = java.util.Set.of(1002L);

        assertThat(entries()).extracting(e -> e.work().externalId()).containsExactly("1001");
    }

    @Test
    void theLookupGoesInBatchesOfFifty() {
        mal.user("ana_l", MyAnimeListStub.completed(1, 120), List.of());
        long[] ids = java.util.stream.LongStream.rangeClosed(1, 120).toArray();
        knowAnime(ids);

        List<ActivityEntry> entries = entries();

        assertThat(entries).hasSize(120);
        assertThat(lookups()).extracting(List::size).containsExactly(50, 50, 20);
        assertThat(ani.requests).allSatisfy(r -> assertThat(r.body()).contains("idMal_in")
                .doesNotContain("MAL title")); // ids only: nothing else of MyAnimeList is sent to AniList
    }

    @Test
    void aListWithNothingThatCountsAsksAniListNothing() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(1, "plan_to_watch", UPDATED)), List.of());

        assertThat(entries()).isEmpty();
        assertThat(ani.requests).isEmpty();
    }

    @Test
    void anAniListFailureFailsTheWholeFetchSoNothingIsAppliedHalfWay() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(1, "completed", UPDATED)), List.of());
        knowAnime(1);
        ani.status = 429;

        assertProblem(() -> entries(), Problem.PROVIDER_UNAVAILABLE);
    }

    // ---- paging ----

    @Test
    void theNextPageIsRequestedFromTheOffsetAndThePagingUrlIsNeverFollowed() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            list.add(MyAnimeListStub.anime(i + 1, "plan_to_watch", UPDATED)); // page one keeps nothing
        }
        list.addAll(MyAnimeListStub.completed(5001, 300));
        mal.user("ana_l", list, List.of());
        long[] ids = java.util.stream.LongStream.rangeClosed(5001, 5300).toArray();
        knowAnime(ids);

        List<ActivityEntry> entries = entries(); // the stub's paging.next is on a host that cannot resolve

        assertThat(entries).hasSize(300);
        List<MyAnimeListStub.Request> animeRequests = mal.listRequests().stream()
                .filter(r -> r.path().endsWith("/animelist")).toList();
        assertThat(animeRequests).hasSize(2);
        assertThat(animeRequests.get(0).rawQuery()).contains("fields=list_status", "limit=1000", "offset=0");
        assertThat(animeRequests.get(1).rawQuery()).contains("fields=list_status", "limit=1000", "offset=1000");
    }

    @Test
    void readingStopsOncePlentyOfRecentEntriesAreKept() {
        mal.user("ana_l", MyAnimeListStub.completed(1, 2500), List.of()); // 3 pages exist
        knowAnime(java.util.stream.LongStream.rangeClosed(1, 2500).toArray());

        List<ActivityEntry> entries = entries();

        assertThat(entries).hasSize(MyAnimeListProvider.MAX_ENTRIES);
        assertThat(mal.listRequests().stream().filter(r -> r.path().endsWith("/animelist"))).hasSize(1);
        assertThat(lookups().stream().flatMap(List::stream)).hasSize(MyAnimeListProvider.MAX_ENTRIES);
        // the newest by time: ids 1..500 were updated most recently
        assertThat(entries).extracting(e -> e.work().externalId()).contains("1001", "1500").doesNotContain("1501");
    }

    @Test
    void aHugeListIsReadForAtMostTenPages() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < 11_000; i++) {
            list.add(MyAnimeListStub.anime(i + 1, "plan_to_watch", UPDATED));
        }
        mal.user("ana_l", list, List.of());

        assertThat(entries()).isEmpty();
        assertThat(mal.listRequests().stream().filter(r -> r.path().endsWith("/animelist")))
                .hasSize(MyAnimeListProvider.MAX_PAGES);
    }

    // ---- the account check ----

    @Test
    void theCheckAsksForOneEntryAndAnExistingPublicListIsFound() {
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS);

        assertThat(mal.listRequests()).hasSize(1);
        assertThat(mal.listRequests().getFirst().path()).isEqualTo("/v2/users/ana_l/animelist");
        assertThat(mal.listRequests().getFirst().rawQuery()).contains("limit=1");
    }

    @Test
    void aMissingUserIs404AndAPrivateListIs403AndBothAreNotFound() {
        assertThat(provider().checkAccountExists("nobody")).isEqualTo(AccountCheck.NOT_FOUND);

        mal.privateLists.add("ana_l");
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.NOT_FOUND);
    }

    @Test
    void bothListsUnreadableMeansTheFetchThrowsAccountNotFound() {
        assertProblem(() -> provider().fetchActivity("nobody"), Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);

        mal.privateLists.add("ana_l");
        assertProblem(() -> entries(), Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);
    }

    @Test
    void onePrivateListDoesNotHideTheOther() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(1, "completed", UPDATED)),
                List.of(MyAnimeListStub.manga(21, "completed", UPDATED)));
        knowAnime(1);
        knowManga(21);
        mal.privateLists.add("ana_l:mangalist");

        assertThat(entries()).extracting(e -> e.work().externalId()).containsExactly("1001");
    }

    @Test
    void anEmptyListIsAnEmptySnapshotNotAnError() {
        assertThat(entries()).isEmpty();
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.EXISTS);
    }

    // ---- failures ----

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500, 503})
    void anyOtherErrorStatusIsUnavailableAndTheFetchThrowsProviderUnavailable(int code) {
        mal.status = code;

        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertProblem(() -> entries(), Problem.PROVIDER_UNAVAILABLE);
    }

    @Test
    void aClientIdMyAnimeListDoesNotKnowIsUnavailableNotNotFound() {
        MyAnimeListProvider wrong = provider(UUID.randomUUID().toString().replace("-", ""), Duration.ofSeconds(2), mal.uri());

        assertThat(wrong.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE); // 401
        assertProblem(() -> wrong.fetchActivity("ana_l"), Problem.PROVIDER_UNAVAILABLE);
    }

    @Test
    void anUnreadableOrUnexpectedBodyIsUnavailable() {
        mal.forcedBody = "<html>bad gateway</html>";
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertProblem(() -> entries(), Problem.PROVIDER_UNAVAILABLE);

        mal.forcedBody = "{\"something\":\"else\"}";
        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertProblem(() -> entries(), Problem.PROVIDER_UNAVAILABLE);
    }

    @Test
    void aMalformedEntryIsSkippedWithoutHidingTheOthers() {
        mal.user("ana_l", List.of(MyAnimeListStub.anime(1, "completed", UPDATED),
                Map.of("node", "nope", "list_status", 42),
                Map.of("node", Map.of("id", "abc"), "list_status", Map.of("status", "completed"))), List.of());
        knowAnime(1);

        assertThat(entries()).extracting(e -> e.work().externalId()).containsExactly("1001");
    }

    @Test
    void anOversizeAnswerIsDropped() {
        mal.forcedBody = "{\"data\":[],\"pad\":\"" + "a".repeat(MyAnimeListProvider.MAX_BODY_BYTES) + "\"}";

        assertProblem(() -> entries(), Problem.PROVIDER_UNAVAILABLE);
    }

    @Test
    void aSlowServerTimesOut() {
        mal.delayMillis = 2500;
        MyAnimeListProvider quick = provider(CLIENT_ID, Duration.ofMillis(300), mal.uri());

        assertProblem(() -> quick.fetchActivity("ana_l"), Problem.PROVIDER_UNAVAILABLE);
        assertThat(quick.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
    }

    @Test
    void aRedirectIsNotFollowed() {
        mal.status = 302;

        assertThat(provider().checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(mal.requests).hasSize(1);
    }

    @Test
    void theRequestsAreSpacedToStayGentle() {
        AniListProvider anilist = new AniListProvider(HttpCatalogProvider.newClient(), Clock.systemUTC(),
                Duration.ofMinutes(10), ani.uri(), Duration.ofSeconds(2));
        MyAnimeListProvider spaced = new MyAnimeListProvider(HttpCatalogProvider.newClient(), mal.uri(), CLIENT_ID, anilist,
                Duration.ofSeconds(2), Duration.ofMillis(150));
        long start = System.nanoTime();

        for (int i = 0; i < 4; i++) {
            spaced.checkAccountExists("ana_l");
        }

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(3 * 150 - 20));
    }

    // ---- the client id ----

    @Test
    void theClientIdTravelsOnlyInTheHeaderNeverInTheUrl() {
        provider().checkAccountExists("ana_l");
        entries();

        assertThat(mal.requests).isNotEmpty().allSatisfy(r -> {
            assertThat(r.clientIdHeader()).isEqualTo(CLIENT_ID);
            assertThat(r.path() + "?" + r.rawQuery()).doesNotContain(CLIENT_ID).doesNotContainIgnoringCase("client");
        });
    }

    @Test
    void withoutAClientIdNothingIsSentAndTheProviderIsUnavailable() {
        for (String missing : new String[] {null, "", "   "}) {
            MyAnimeListProvider keyless = provider(missing, Duration.ofSeconds(2), mal.uri());

            assertThat(keyless.isAvailable()).isFalse();
            assertThat(keyless.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
            assertProblem(() -> keyless.fetchActivity("ana_l"), Problem.PROVIDER_UNAVAILABLE);
        }
        assertThat(mal.requests).isEmpty();
        assertThat(ani.requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc&user=evil", "abc def", "key/../x", "k#frag", "k\r\nHost: evil", "short", "ключ-ключ-ключ-ключ"})
    void aClientIdThatIsNotPlainAlphanumericIsNeverSent(String badId) {
        MyAnimeListProvider odd = provider(badId, Duration.ofSeconds(2), mal.uri());

        assertThat(odd.isAvailable()).isFalse();
        assertThat(odd.checkAccountExists("ana_l")).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThat(mal.requests).isEmpty();
    }

    @Test
    void aConfiguredClientIdMakesTheProviderAvailable() {
        assertThat(provider().isAvailable()).isTrue();
    }

    @Test
    void theClientIdNeverReachesTheLogsOrAnExceptionOnAnyKindOfFailure() {
        ListAppender<ILoggingEvent> logs = captureLogs();
        List<Throwable> thrown = new ArrayList<>();
        try {
            MyAnimeListProvider wrong = provider(UUID.randomUUID().toString().replace("-", ""), Duration.ofSeconds(2), mal.uri());
            wrong.checkAccountExists("ana_l"); // 401
            thrown.add(catchLinked(() -> wrong.fetchActivity("ana_l")));
            mal.status = 500;
            provider().checkAccountExists("ana_l");
            thrown.add(catchLinked(() -> entries()));
            mal.status = 0;
            mal.forcedBody = "not json";
            thrown.add(catchLinked(() -> entries()));
            mal.reset();
            mal.user("ana_l", List.of(MyAnimeListStub.anime(1, "completed", UPDATED)), List.of());
            knowAnime(1);
            ani.status = 500;
            thrown.add(catchLinked(() -> entries()));
            ani.reset();
            mal.delayMillis = 2500;
            thrown.add(catchLinked(() -> provider(CLIENT_ID, Duration.ofMillis(200), mal.uri()).fetchActivity("ana_l")));
            MyAnimeListStub gone = new MyAnimeListStub(CLIENT_ID); // a refused connection: the stub's own port, closed
            URI closed = gone.uri();
            gone.close();
            thrown.add(catchLinked(() -> provider(CLIENT_ID, Duration.ofSeconds(1), closed).fetchActivity("ana_l")));
        } finally {
            detach(logs);
        }

        assertThat(thrown).hasSize(6).allSatisfy(e -> {
            assertThat(String.valueOf(e.getMessage()) + e.getCause()).doesNotContain(CLIENT_ID);
            assertThat(e.getCause()).isNull();
        });
        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent event : logs.list) {
            String everything = event.getFormattedMessage() + event.getThrowableProxy();
            assertThat(everything).doesNotContain(CLIENT_ID).doesNotContainIgnoringCase("X-MAL-CLIENT-ID")
                    .doesNotContain("api.myanimelist.net");
        }
    }

    private static LinkedAccountException catchLinked(Runnable call) {
        try {
            call.run();
        } catch (LinkedAccountException expected) {
            return expected;
        }
        throw new AssertionError("expected a LinkedAccountException");
    }

    // ---- username rules and hostile names ----

    @ParameterizedTest
    @ValueSource(strings = {"ab", "Ana_L", "ana-l", "a1", "abcdefghijklmnop", "ANA"})
    void aMyAnimeListNameOfTwoToSixteenSafeCharactersIsValidAndCaseIsKept(String name) {
        MyAnimeListProvider provider = provider();

        assertThat(provider.validateUsernameFormat(provider.normalizeUsername("  " + name + " "))).isEmpty();
        assertThat(provider.normalizeUsername(name)).isEqualTo(name);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"a", "abcdefghijklmnopq", "a b", "a/b", "../x", "..", "a?x=y", "a#b", "a@b", "a.b", "a%2fb",
            "x\"){ id }", "a\r\nX-Evil: 1", "a\u0000b", "café", "ＡＮＡ", "https://evil.example", "a\\b", "a;b"})
    void aHostileNameIsRefusedByTheFormatRulesAndNeverReachesTheNetwork(String name) {
        MyAnimeListProvider provider = provider();

        assertThat(provider.validateUsernameFormat(provider.normalizeUsername(name))).isPresent();
        assertThat(provider.checkAccountExists(name)).isEqualTo(AccountCheck.UNAVAILABLE);
        assertThatThrownBy(() -> provider.fetchActivity(name)).isInstanceOf(RuntimeException.class);
        assertThat(mal.requests).isEmpty();
    }

    @Test
    void theUsernameIsUrlEncodedInThePath() {
        mal.user("ana-l_9", List.of(), List.of());

        assertThat(provider().checkAccountExists("ana-l_9")).isEqualTo(AccountCheck.EXISTS);
        assertThat(mal.listRequests().getFirst().path()).isEqualTo("/v2/users/ana-l_9/animelist");
    }

    // ---- helpers ----

    private static ListAppender<ILoggingEvent> captureLogs() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        return logs;
    }

    private static void detach(ListAppender<ILoggingEvent> logs) {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }
}
