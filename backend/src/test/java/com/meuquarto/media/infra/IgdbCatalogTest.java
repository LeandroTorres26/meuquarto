package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.shared.domain.MediaCategory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** IGDB against a local stub of Twitch and IGDB; the credentials are random values made for each run. */
class IgdbCatalogTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final String clientId = "id" + UUID.randomUUID().toString().replace("-", "");
    private final String clientSecret = "sec" + UUID.randomUUID().toString().replace("-", "");
    private final MutableClock clock = new MutableClock();
    private IgdbStub stub;

    @BeforeEach
    void startStub() {
        stub = new IgdbStub();
        stub.gamesBody = CatalogStub.fixture("igdb/search-games.json");
    }

    @AfterEach
    void stopStub() {
        stub.close();
    }

    private IgdbCatalog igdb(String id, String secret, Duration cacheTtl) {
        return new IgdbCatalog(HttpCatalogProvider.newClient(), clock, cacheTtl, stub.apiUrl(), stub.tokenUrl(), id,
                secret, Duration.ofSeconds(2), Duration.ZERO);
    }

    private IgdbCatalog igdb() {
        return igdb(clientId, clientSecret, Duration.ZERO);
    }

    @Test
    void searchNormalizesGamesAndDropsAdultAndBrokenEntries() {
        List<CatalogEntry> results = igdb().search(MediaCategory.GAME, "witcher");

        assertThat(results).extracting(CatalogEntry::externalId).containsExactly("1942", "7346", "119133", "556");
        assertThat(results.get(0)).isEqualTo(new CatalogEntry(MediaCategory.GAME, "IGDB", "1942",
                "The Witcher 3: Wild Hunt", 2015, "https://images.igdb.com/igdb/image/upload/t_cover_big/co1wyy.jpg", false));
        assertThat(results.get(2).year()).isNull(); // no date
        assertThat(results.get(2).imageUrl()).isNull(); // no cover
        assertThat(results.get(3).imageUrl()).isNull(); // image id that is not alphanumeric is refused
    }

    @Test
    void theEroticThemeMapsToAdultOnFetchAndIsDroppedFromSearch() {
        stub.gamesBody = "[{\"id\":555,\"name\":\"Jogo adulto\",\"themes\":[42,1]}]";

        assertThat(igdb().fetch(MediaCategory.GAME, "555")).get().extracting(CatalogEntry::adult).isEqualTo(true);
        assertThat(igdb().search(MediaCategory.GAME, "adulto")).isEmpty();
    }

    @Test
    void searchSendsApicalypseWithTheClientIdAndABearerTokenAndNeverTheSecret() {
        igdb().search(MediaCategory.GAME, "zelda");

        IgdbStub.GamesRequest request = stub.games.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.clientId()).isEqualTo(clientId);
        assertThat(request.authorization()).isEqualTo("Bearer " + stub.validToken);
        assertThat(request.body()).isEqualTo("search \"zelda\"; fields name,first_release_date,cover.image_id,themes;"
                + " where version_parent = null & parent_game = null; limit 20;");
        assertThat(request.body()).doesNotContain(clientSecret);
        assertThat(stub.tokenForms.get(0)).contains("grant_type=client_credentials", "client_id=" + clientId,
                "client_secret=" + clientSecret).endsWith("|query=null"); // credentials in the form, not in the URL
    }

    @Test
    void theUsersTextCannotCloseTheStringOrAddClauses() {
        IgdbCatalog igdb = igdb();

        igdb.search(MediaCategory.GAME, "x\"; where id = 1; fields *; \"");
        igdb.search(MediaCategory.GAME, "back\\\"slash");
        igdb.search(MediaCategory.GAME, "line\nbreak\r\u0000tab\there");

        assertThat(stub.games.get(0).body())
                .startsWith("search \"x\\\"; where id = 1; fields *; \\\"\"; fields name,");
        assertThat(stub.games.get(1).body()).startsWith("search \"back\\\\\\\"slash\"; fields name,");
        assertThat(stub.games.get(2).body()).startsWith("search \"linebreaktabhere\"; fields name,");
        // exactly one limit in every body: the text added no clause of its own
        for (IgdbStub.GamesRequest request : stub.games) {
            assertThat(request.body().split("limit ", -1)).hasSize(2);
        }
    }

    @Test
    void quoteRejectsEmptyTooLongAndControlOnlyText() {
        assertThatThrownBy(() -> IgdbCatalog.quote(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IgdbCatalog.quote("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IgdbCatalog.quote("\n\u0001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IgdbCatalog.quote("a".repeat(101))).isInstanceOf(IllegalArgumentException.class);
        assertThat(IgdbCatalog.quote("a".repeat(100))).hasSize(102);

        assertThat(igdb().search(MediaCategory.GAME, "a".repeat(101))).isEmpty();
        assertThat(stub.games).isEmpty();
    }

    @Test
    void fetchReturnsOneGameAndRejectsIdsThatAreNotNumbers() {
        stub.gamesBody = CatalogStub.fixture("igdb/game-1942.json");
        IgdbCatalog igdb = igdb();

        assertThat(igdb.fetch(MediaCategory.GAME, "1942")).get().extracting(CatalogEntry::title)
                .isEqualTo("The Witcher 3: Wild Hunt");
        assertThat(stub.games.get(0).body())
                .isEqualTo("fields name,first_release_date,cover.image_id,themes; where id = 1942; limit 1;");

        int before = stub.games.size();
        assertThat(igdb.fetch(MediaCategory.GAME, "1942; where id = 1")).isEmpty();
        assertThat(igdb.fetch(MediaCategory.GAME, "")).isEmpty();
        assertThat(igdb.fetch(MediaCategory.GAME, null)).isEmpty();
        assertThat(stub.games).hasSize(before);

        stub.gamesBody = "[]";
        assertThat(igdb.fetch(MediaCategory.GAME, "42")).isEmpty();
    }

    @Test
    void theTokenIsReusedUntilShortlyBeforeItExpiresAndThenRenewed() {
        stub.expiresIn = 3600;
        IgdbCatalog igdb = igdb();

        igdb.search(MediaCategory.GAME, "one");
        igdb.search(MediaCategory.GAME, "two");
        assertThat(stub.tokensIssued).hasValue(1);

        clock.now = clock.now.plusSeconds(3600 - 61); // still before the one-minute margin
        igdb.search(MediaCategory.GAME, "three");
        assertThat(stub.tokensIssued).hasValue(1);

        clock.now = clock.now.plusSeconds(2); // past expires_in minus 60 s
        igdb.search(MediaCategory.GAME, "four");
        assertThat(stub.tokensIssued).hasValue(2);
        assertThat(stub.games.get(3).authorization()).isEqualTo("Bearer " + stub.validToken);
    }

    @Test
    void a401RenewsTheTokenAndRetriesExactlyOnce() {
        IgdbCatalog igdb = igdb();
        igdb.search(MediaCategory.GAME, "first");
        assertThat(stub.tokensIssued).hasValue(1);

        stub.validToken = "revoked-on-the-server"; // the cached token stops working
        assertThat(igdb.search(MediaCategory.GAME, "second")).isNotEmpty();
        assertThat(stub.tokensIssued).hasValue(2);
        assertThat(stub.games).hasSize(3); // first, rejected second, retried second

        stub.rejectAll = true;
        assertThat(igdb.search(MediaCategory.GAME, "third")).isEmpty();
        assertThat(stub.games).hasSize(5); // one try plus one retry, never a loop
    }

    @Test
    void answersAreCachedByQuery() {
        IgdbCatalog igdb = igdb(clientId, clientSecret, Duration.ofMinutes(10));

        igdb.search(MediaCategory.GAME, "zelda");
        igdb.search(MediaCategory.GAME, "zelda");

        assertThat(stub.games).hasSize(1);
    }

    @Test
    void withoutBothCredentialsItIsUnavailableAndMakesNoRequest() {
        String[][] cases = {{"", ""}, {clientId, ""}, {"", clientSecret}, {"  ", "  "}, {null, null}};
        for (String[] credentials : cases) {
            IgdbCatalog igdb = igdb(credentials[0], credentials[1], Duration.ZERO);
            assertThat(igdb.isAvailable()).isFalse();
            assertThat(igdb.search(MediaCategory.GAME, "zelda")).isEmpty();
            assertThat(igdb.fetch(MediaCategory.GAME, "1942")).isEmpty();
        }
        assertThat(stub.tokensIssued).hasValue(0);
        assertThat(stub.tokenForms).isEmpty();
        assertThat(stub.games).isEmpty();
    }

    @Test
    void aFailingTokenEndpointOrGamesEndpointGivesEmptyResults() {
        stub.tokenStatus = 403;
        assertThat(igdb().search(MediaCategory.GAME, "zelda")).isEmpty();
        assertThat(stub.games).isEmpty();

        stub.tokenStatus = 200;
        stub.gamesStatus = 500;
        assertThat(igdb().search(MediaCategory.GAME, "zelda")).isEmpty();

        stub.gamesStatus = 200;
        stub.gamesBody = "<html>not json</html>";
        assertThat(igdb().search(MediaCategory.GAME, "zelda")).isEmpty();
    }

    @Test
    void servesOnlyGames() {
        IgdbCatalog igdb = igdb();
        for (MediaCategory category : MediaCategory.values()) {
            assertThat(igdb.serves(category)).isEqualTo(category == MediaCategory.GAME);
        }
        assertThat(igdb.catalogName()).isEqualTo("IGDB");
        assertThat(igdb.search(MediaCategory.MOVIE, "zelda")).isEmpty();
        assertThat(stub.games).isEmpty();
    }
}
