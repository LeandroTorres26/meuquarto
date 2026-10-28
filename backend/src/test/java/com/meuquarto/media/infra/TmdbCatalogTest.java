package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.shared.domain.MediaCategory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TMDB parsing against recorded-style JSON served by a local stub; nothing here reaches the real TMDB. */
class TmdbCatalogTest {

    private static final String MOVIES = """
            {"page":1,"total_results":4,"results":[
              {"id":603,"title":"Matrix","release_date":"1999-03-30","poster_path":"/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg","adult":false},
              {"id":604,"title":"Sem poster","release_date":"","poster_path":null,"adult":false},
              {"id":999,"title":"Adulto","release_date":"2001-01-01","poster_path":"/a.jpg","adult":true},
              {"id":605,"title":"Poster estranho","release_date":"2003-11-05","poster_path":"https://evil.example/x.jpg","adult":false},
              {"title":"Sem id","release_date":"2003-11-05"}
            ]}""";

    private static final String TV = """
            {"page":1,"results":[
              {"id":1396,"name":"Breaking Bad","first_air_date":"2008-01-20","poster_path":"/ggFHVNu6YYI5L9pCfOacjizRGt.jpg","adult":false}
            ]}""";

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>(MOVIES);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private TmdbCatalog tmdb(String token) {
        return new TmdbCatalog(HttpCatalogProvider.newClient(), Clock.systemUTC(), Duration.ofMinutes(10),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/3", token);
    }

    @Test
    void searchNormalizesFilmsAndDropsAdultAndBrokenEntries() {
        List<CatalogEntry> results = tmdb("secret-token").search(MediaCategory.MOVIE, "matrix");

        assertThat(results).extracting(CatalogEntry::externalId).containsExactly("603", "604", "605");
        CatalogEntry matrix = results.get(0);
        assertThat(matrix).isEqualTo(new CatalogEntry(MediaCategory.MOVIE, "TMDB", "603", "Matrix", 1999,
                "https://image.tmdb.org/t/p/w342/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg", false));
        assertThat(results.get(1).imageUrl()).isNull(); // missing poster
        assertThat(results.get(1).year()).isNull(); // missing date
        assertThat(results.get(2).imageUrl()).isNull(); // poster outside the TMDB CDN is refused
    }

    @Test
    void searchSendsTheTokenAsBearerHeaderAndNeverInTheUrl() {
        tmdb("secret-token").search(MediaCategory.MOVIE, "o poderoso & chefão");

        assertThat(authorization.get()).isEqualTo("Bearer secret-token");
        String url = requests.get(0);
        assertThat(url).startsWith("/3/search/movie?").contains("language=pt-BR", "include_adult=false")
                .contains("query=o+poderoso+%26+chef%C3%A3o").doesNotContain("secret-token");
    }

    @Test
    void seriesUseTheTvEndpointAndItsFieldNames() {
        body.set(TV);

        List<CatalogEntry> results = tmdb("t").search(MediaCategory.SERIES, "breaking");

        assertThat(requests.get(0)).startsWith("/3/search/tv?");
        assertThat(results).containsExactly(new CatalogEntry(MediaCategory.SERIES, "TMDB", "1396", "Breaking Bad",
                2008, "https://image.tmdb.org/t/p/w342/ggFHVNu6YYI5L9pCfOacjizRGt.jpg", false));
    }

    @Test
    void fetchReturnsOneEntryAndRejectsIdsThatAreNotNumbers() {
        body.set("{\"id\":603,\"title\":\"Matrix\",\"release_date\":\"1999-03-30\",\"poster_path\":\"/p.jpg\"}");
        TmdbCatalog tmdb = tmdb("t");

        assertThat(tmdb.fetch(MediaCategory.MOVIE, "603")).get().extracting(CatalogEntry::title).isEqualTo("Matrix");
        assertThat(requests).containsExactly("/3/movie/603?language=pt-BR");

        requests.clear();
        assertThat(tmdb.fetch(MediaCategory.MOVIE, "603/../../account")).isEmpty();
        assertThat(tmdb.fetch(MediaCategory.MOVIE, "")).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void errorStatusAndBrokenJsonGiveEmptyResults() {
        status.set(401);
        assertThat(tmdb("bad").search(MediaCategory.MOVIE, "matrix")).isEmpty();

        status.set(200);
        body.set("<html>not json</html>");
        assertThat(tmdb("t").search(MediaCategory.MOVIE, "matrix")).isEmpty();

        body.set("{\"results\":\"nope\"}");
        assertThat(tmdb("t").search(MediaCategory.MOVIE, "matrix")).isEmpty();
    }

    @Test
    void withoutTokenItIsUnavailableAndMakesNoRequest() {
        for (String token : new String[] {"", "   ", null}) {
            TmdbCatalog tmdb = tmdb(token);
            assertThat(tmdb.isAvailable()).isFalse();
            assertThat(tmdb.search(MediaCategory.MOVIE, "matrix")).isEmpty();
            assertThat(tmdb.fetch(MediaCategory.MOVIE, "603")).isEmpty();
        }
        assertThat(requests).isEmpty();
    }

    @Test
    void servesOnlyFilmsAndSeries() {
        TmdbCatalog tmdb = tmdb("t");
        List<MediaCategory> served = new ArrayList<>();
        for (MediaCategory category : MediaCategory.values()) {
            if (tmdb.serves(category)) {
                served.add(category);
            }
        }
        assertThat(served).containsExactly(MediaCategory.MOVIE, MediaCategory.SERIES);
        assertThat(tmdb.id()).isEqualTo("TMDB");
    }
}
