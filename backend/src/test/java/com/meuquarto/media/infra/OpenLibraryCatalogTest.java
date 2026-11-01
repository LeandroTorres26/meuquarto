package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.shared.domain.MediaCategory;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Open Library parsing against real-shaped JSON served by a local stub; nothing here reaches the real API. */
class OpenLibraryCatalogTest {

    private CatalogStub stub;

    @BeforeEach
    void start() {
        stub = new CatalogStub();
    }

    @AfterEach
    void stop() {
        stub.close();
    }

    private CatalogProvider catalog() {
        return new OpenLibraryCatalog(HttpCatalogProvider.newClient(), Clock.systemUTC(), Duration.ofMinutes(10),
                stub.baseUrl(), Duration.ofSeconds(2));
    }

    @Test
    void searchNormalizesWorksAndSkipsWhatIsNotAWork() {
        stub.body = CatalogStub.fixture("openlibrary/search.json");

        List<CatalogEntry> results = catalog().search(MediaCategory.BOOK, "fantastic mr fox");

        assertThat(results).extracting(CatalogEntry::externalId).containsExactly("OL45804W", "OL262758W", "OL1W");
        assertThat(results.get(0)).isEqualTo(new CatalogEntry(MediaCategory.BOOK, "OPENLIBRARY", "OL45804W",
                "Fantastic Mr Fox", 1970, "https://covers.openlibrary.org/b/id/8739161-M.jpg", false));
        assertThat(results.get(1).year()).isNull();
        assertThat(results.get(1).imageUrl()).isNull();
        assertThat(results.get(2).year()).isNull(); // 99999 is not a year
        assertThat(results.get(2).imageUrl()).isNull(); // -1 means "no cover"
        assertThat(results).noneMatch(CatalogEntry::adult);
    }

    @Test
    void searchEncodesTheQueryAndLimitsTheFieldsAndSendsADescriptiveUserAgent() {
        stub.body = "{\"docs\":[]}";

        catalog().search(MediaCategory.BOOK, "o hobbit & cia/../x");

        CatalogStub.Request request = stub.requests.get(0);
        assertThat(request.path()).startsWith("/search.json?")
                .contains("limit=20", "fields=key,title,first_publish_year,cover_i")
                .contains("q=o+hobbit+%26+cia%2F..%2Fx");
        assertThat(request.userAgent()).isEqualTo(OpenLibraryCatalog.USER_AGENT).contains("meuquarto");
    }

    @Test
    void fetchReadsOneWorkWithTheYearOutOfADateAndTheFirstRealCover() {
        stub.body = CatalogStub.fixture("openlibrary/work-OL45804W.json");

        assertThat(catalog().fetch(MediaCategory.BOOK, "OL45804W")).contains(new CatalogEntry(MediaCategory.BOOK,
                "OPENLIBRARY", "OL45804W", "Fantastic Mr Fox", 1970, "https://covers.openlibrary.org/b/id/8739161-M.jpg",
                false));
        assertThat(stub.requests).extracting(CatalogStub.Request::path).containsExactly("/works/OL45804W.json");
    }

    @Test
    void fetchRejectsIdsThatAreNotWorkKeysWithoutAnyRequest() {
        CatalogProvider catalog = catalog();

        for (String id : new String[] {"", "45804", "OL45804M", "OL45804W/../../account", "ol1w", "OL1W?x=y"}) {
            assertThat(catalog.fetch(MediaCategory.BOOK, id)).isEmpty();
        }
        assertThat(catalog.fetch(MediaCategory.BOOK, null)).isEmpty();
        assertThat(stub.requests).isEmpty();
    }

    @Test
    void emptyResultsAndEveryKindOfFailureGiveAnEmptyAnswer() {
        CatalogProvider catalog = catalog();

        stub.body = "{\"numFound\":0,\"docs\":[]}";
        assertThat(catalog.search(MediaCategory.BOOK, "zzzzqq")).isEmpty();

        stub.status = 503;
        assertThat(catalog.search(MediaCategory.BOOK, "livro um")).isEmpty();

        stub.status = 200;
        stub.body = "<html>not json</html>";
        assertThat(catalog.search(MediaCategory.BOOK, "livro dois")).isEmpty();

        stub.body = "{\"docs\":\"nope\"}";
        assertThat(catalog.search(MediaCategory.BOOK, "livro tres")).isEmpty();
    }

    @Test
    void aSlowProviderTimesOutIntoAnEmptyAnswer() {
        stub.delayMillis = 1500;
        stub.body = CatalogStub.fixture("openlibrary/search.json");
        CatalogProvider slow = new OpenLibraryCatalog(HttpCatalogProvider.newClient(), Clock.systemUTC(),
                Duration.ofMinutes(10), stub.baseUrl(), Duration.ofMillis(200));

        assertThat(slow.search(MediaCategory.BOOK, "lento")).isEmpty();
    }

    @Test
    void anInvalidQueryIsNeverSent() {
        CatalogProvider catalog = catalog();

        assertThat(catalog.search(MediaCategory.BOOK, "a".repeat(101))).isEmpty();
        assertThat(catalog.search(MediaCategory.BOOK, "   ")).isEmpty();
        assertThat(catalog.search(MediaCategory.BOOK, null)).isEmpty();
        assertThat(stub.requests).isEmpty();
    }

    @Test
    void anOversizedResponseIsRefused() {
        stub.body = "{\"docs\":[],\"pad\":\"" + "x".repeat(HttpCatalogProvider.MAX_BODY_BYTES) + "\"}";

        assertThat(catalog().search(MediaCategory.BOOK, "enorme")).isEmpty();
    }

    @Test
    void servesOnlyBooksAndIsAlwaysAvailable() {
        CatalogProvider catalog = catalog();
        List<MediaCategory> served = new ArrayList<>();
        for (MediaCategory category : MediaCategory.values()) {
            if (catalog.serves(category)) {
                served.add(category);
            }
        }
        assertThat(served).containsExactly(MediaCategory.BOOK);
        assertThat(catalog.catalogName()).isEqualTo("OPENLIBRARY");
        assertThat(catalog.isAvailable()).isTrue();
        assertThat(catalog.search(MediaCategory.MOVIE, "matrix")).isEmpty();
        assertThat(stub.requests).isEmpty();
    }
}
