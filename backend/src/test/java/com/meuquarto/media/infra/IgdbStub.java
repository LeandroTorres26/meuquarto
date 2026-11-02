package com.meuquarto.media.infra;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A local stand-in for Twitch's token endpoint ({@code /oauth2/token}) and IGDB's {@code /v4/games}.
 * It hands out a new random token on every token request and, on the games endpoint, answers 401 to any
 * token it does not currently consider valid. Nothing here touches the network.
 */
final class IgdbStub implements AutoCloseable {

    record GamesRequest(String method, String clientId, String authorization, String contentType, String body) {}

    final List<String> tokenForms = new CopyOnWriteArrayList<>();
    final List<GamesRequest> games = new CopyOnWriteArrayList<>();
    final AtomicInteger tokensIssued = new AtomicInteger();
    volatile String gamesBody = "[]";
    volatile int gamesStatus = 200;
    volatile int tokenStatus = 200;
    volatile long expiresIn = 3600;
    /** The only token the games endpoint accepts; replaced by every token request. */
    volatile String validToken = "";
    /** When set, the games endpoint ignores {@link #validToken} and rejects everything with 401. */
    volatile boolean rejectAll = false;

    private final HttpServer server;

    IgdbStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/oauth2/token", exchange -> {
            tokenForms.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    + " |query=" + exchange.getRequestURI().getRawQuery());
            if (tokenStatus != 200) {
                exchange.sendResponseHeaders(tokenStatus, -1);
                exchange.close();
                return;
            }
            tokensIssued.incrementAndGet();
            validToken = "tok" + UUID.randomUUID().toString().replace("-", "");
            reply(exchange, 200, "{\"access_token\":\"" + validToken + "\",\"expires_in\":" + expiresIn
                    + ",\"token_type\":\"bearer\"}");
        });
        server.createContext("/v4/games", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            games.add(new GamesRequest(exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Client-ID"),
                    authorization, exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            if (rejectAll || !("Bearer " + validToken).equals(authorization)) {
                reply(exchange, 401, "{\"message\":\"Authorization Failure.\"}");
                return;
            }
            reply(exchange, gamesStatus, gamesBody);
        });
        server.start();
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    String apiUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v4/games";
    }

    String tokenUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2/token";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
