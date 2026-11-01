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

/** Deezer parsing against real-shaped JSON served by a local stub; nothing here reaches the real API. */
class DeezerCatalogTest {

    private static final String CDN_COVER =
            "https://cdn-images.dzcdn.net/images/cover/2e018122cb56986277102d2041a592c8/250x250-000000-80-0-0.jpg";

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
        return new DeezerCatalog(HttpCatalogProvider.newClient(), Clock.systemUTC(), Duration.ofMinutes(10),
                stub.baseUrl(), Duration.ofSeconds(2));
    }

    @Test
    void searchNormalizesAlbumsWithoutAnExtraCallForTheYear() {
        stub.body = CatalogStub.fixture("deezer/search-album.json");

        List<CatalogEntry> results = catalog().search(MediaCategory.MUSIC, "discovery");

        assertThat(results).extracting(CatalogEntry::externalId).containsExactly("302127", "5", "6", "7");
        assertThat(results.get(0)).isEqualTo(new CatalogEntry(MediaCategory.MUSIC, "DEEZER", "302127",
                "Discovery - Daft Punk", null, CDN_COVER, false));
        assertThat(results.get(3).title()).isEqualTo("Sem artista nem capa");
        assertThat(stub.requests).hasSize(1);
        assertThat(stub.requests.get(0).path()).startsWith("/search/album?").contains("limit=20", "q=discovery");
    }

    @Test
    void explicitLyricsAreNotAdultContent() {
        stub.body = CatalogStub.fixture("deezer/search-album.json");

        List<CatalogEntry> results = catalog().search(MediaCategory.MUSIC, "rapper");

        assertThat(results).extracting(CatalogEntry::externalId).contains("5");
        assertThat(results).noneMatch(CatalogEntry::adult);
    }

    @Test
    void onlyDeezerCdnHostsAreAcceptedAsCovers() {
        stub.body = CatalogStub.fixture("deezer/search-album.json");
        List<CatalogEntry> results = catalog().search(MediaCategory.MUSIC, "capas");

        assertThat(results.get(1).imageUrl()).startsWith("https://e-cdns-images.dzcdn.net/");
        assertThat(results.get(2).imageUrl()).isNull(); // evil.example
        assertThat(results.get(3).imageUrl()).isNull(); // missing

        String[] bad = {
            "http://cdn-images.dzcdn.net/x.jpg", // not https
            "https://cdn-images.dzcdn.net.evil.example/x.jpg", // look-alike
            "https://evil.example/cdn-images.dzcdn.net/x.jpg",
            "https://user@cdn-images.dzcdn.net/x.jpg", // user info
            "https://cdn-images.dzcdn.net:8443/x.jpg", // port
            "https://cdn-images.dzcdn.net/x.jpg#frag",
            "javascript:alert(1)",
            "data:image/png;base64,AAAA",
            "https://api.deezer.com/album/1/image", // Deezer API redirect host is not on the list
        };
        for (String cover : bad) {
            stub.body = "{\"data\":[{\"id\":1,\"title\":\"A\",\"cover_medium\":\"" + cover + "\"}]}";
            // a different query each time so the answer cache cannot hide the change
            assertThat(catalog().search(MediaCategory.MUSIC, "cover " + cover.hashCode()))
                    .extracting(CatalogEntry::imageUrl).containsExactly((String) null);
        }
    }

    @Test
    void fetchReadsOneAlbumWithItsReleaseYear() {
        stub.body = CatalogStub.fixture("deezer/album-302127.json");

        assertThat(catalog().fetch(MediaCategory.MUSIC, "302127")).contains(new CatalogEntry(MediaCategory.MUSIC,
                "DEEZER", "302127", "Discovery - Daft Punk", 2001, CDN_COVER, false));
        assertThat(stub.requests).extracting(CatalogStub.Request::path).containsExactly("/album/302127");
    }

    @Test
    void fetchRejectsIdsThatAreNotNumbersWithoutAnyRequest() {
        CatalogProvider catalog = catalog();

        for (String id : new String[] {"", "abc", "302127/../../user", "1?x=y", "-5"}) {
            assertThat(catalog.fetch(MediaCategory.MUSIC, id)).isEmpty();
        }
        assertThat(catalog.fetch(MediaCategory.MUSIC, null)).isEmpty();
        assertThat(stub.requests).isEmpty();
    }

    @Test
    void emptyResultsAndEveryKindOfFailureGiveAnEmptyAnswer() {
        CatalogProvider catalog = catalog();

        stub.body = "{\"data\":[],\"total\":0}";
        assertThat(catalog.search(MediaCategory.MUSIC, "zzzzqq")).isEmpty();

        stub.status = 500;
        assertThat(catalog.search(MediaCategory.MUSIC, "album um")).isEmpty();

        stub.status = 200;
        stub.body = "{\"error\":{\"type\":\"Exception\",\"message\":\"Quota limit exceeded\",\"code\":4}}";
        assertThat(catalog.search(MediaCategory.MUSIC, "album dois")).isEmpty();
        assertThat(catalog.fetch(MediaCategory.MUSIC, "1")).isEmpty();

        stub.body = "<html>not json</html>";
        assertThat(catalog.search(MediaCategory.MUSIC, "album tres")).isEmpty();
    }

    @Test
    void aSlowProviderTimesOutIntoAnEmptyAnswer() {
        stub.delayMillis = 1500;
        stub.body = CatalogStub.fixture("deezer/search-album.json");
        CatalogProvider slow = new DeezerCatalog(HttpCatalogProvider.newClient(), Clock.systemUTC(),
                Duration.ofMinutes(10), stub.baseUrl(), Duration.ofMillis(200));

        assertThat(slow.search(MediaCategory.MUSIC, "lento")).isEmpty();
    }

    @Test
    void anInvalidQueryIsNeverSentAndAnOversizedResponseIsRefused() {
        CatalogProvider catalog = catalog();
        assertThat(catalog.search(MediaCategory.MUSIC, "a".repeat(101))).isEmpty();
        assertThat(catalog.search(MediaCategory.MUSIC, "  ")).isEmpty();
        assertThat(stub.requests).isEmpty();

        stub.body = "{\"data\":[],\"pad\":\"" + "x".repeat(HttpCatalogProvider.MAX_BODY_BYTES) + "\"}";
        assertThat(catalog.search(MediaCategory.MUSIC, "enorme")).isEmpty();
    }

    @Test
    void servesOnlyMusicAndIsAlwaysAvailable() {
        CatalogProvider catalog = catalog();
        List<MediaCategory> served = new ArrayList<>();
        for (MediaCategory category : MediaCategory.values()) {
            if (catalog.serves(category)) {
                served.add(category);
            }
        }
        assertThat(served).containsExactly(MediaCategory.MUSIC);
        assertThat(catalog.catalogName()).isEqualTo("DEEZER");
        assertThat(catalog.isAvailable()).isTrue();
    }
}
