package com.meuquarto.media.infra;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A local stand-in for graphql.anilist.co: answers GraphQL POSTs with the fixtures in
 * {@code src/test/resources/fixtures/anilist}, chosen by what the query asks for. Records every request
 * (method, path, body) so a test can prove what was and was not sent. Nothing here touches the network.
 */
final class AniListStub implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path DIR = Path.of("src/test/resources/fixtures/anilist");

    record Request(String method, String path, String body) {}

    final List<Request> requests = new CopyOnWriteArrayList<>();
    /** When not 200, every answer has this status and an empty body (429, 500...). */
    volatile int status = 200;
    /** When not null, every answer carries this body instead of the fixture. */
    volatile String forcedBody;
    /** Users the stub knows; any other name gets the 404 AniList sends for a missing or private user. */
    volatile List<String> knownUsers = List.of("ana_l");

    private final HttpServer server;

    AniListStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(), body));
                int code = status;
                byte[] answer = new byte[0];
                String fixture = code == 200 ? (forcedBody != null ? forcedBody : fixtureFor(body)) : null;
                if (fixture != null) {
                    answer = fixture.getBytes(StandardCharsets.UTF_8);
                } else if (code == 200 || code == 404) {
                    code = 404;
                    answer = "{\"errors\":[{\"message\":\"Not Found.\",\"status\":404}],\"data\":null}"
                            .getBytes(StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (answer.length == 0) {
                    exchange.sendResponseHeaders(code, -1);
                } else {
                    exchange.sendResponseHeaders(code, answer.length);
                    exchange.getResponseBody().write(answer);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Override
    public void close() {
        server.stop(0);
    }

    void reset() {
        requests.clear();
        status = 200;
        forcedBody = null;
        knownUsers = List.of("ana_l");
    }

    private String fixtureFor(String requestBody) throws IOException {
        JsonNode request = JSON.readTree(requestBody);
        String query = request.path("query").asString("");
        JsonNode variables = request.path("variables");
        String type = variables.path("type").asString("ANIME").toLowerCase();
        if (query.contains("Page(")) {
            return read("search-" + type + ".json");
        }
        if (query.contains("Media(id")) {
            return read("media-" + variables.path("id").asLong() + ".json");
        }
        String name = variables.path("name").asString("");
        if (!knownUsers.contains(name)) {
            return null; // AniList answers 404 for a user that does not exist or whose list is private
        }
        return query.contains("status_in") ? read("list-" + type + ".json") : read("user.json");
    }

    private static String read(String fixture) throws IOException {
        return Files.readString(DIR.resolve(fixture));
    }
}
