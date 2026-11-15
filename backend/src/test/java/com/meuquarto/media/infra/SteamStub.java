package com.meuquarto.media.infra;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import tools.jackson.databind.json.JsonMapper;

/**
 * A local stand-in for api.steampowered.com, shaped like the answers the provider relies on:
 * {@code ResolveVanityURL} ({@code success} 1 or 42), {@code GetOwnedGames} (an empty {@code response} for a
 * private profile, {@code game_count} for a public one) and {@code GetRecentlyPlayedGames}; 403 for a key it
 * does not know. Records every request with its raw query, so a test can prove what was and was not sent.
 * No real network.
 */
final class SteamStub implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Request(String path, String rawQuery) {}

    final List<Request> requests = new CopyOnWriteArrayList<>();
    /** The key the stub accepts; any other gets 403, as Steam does. */
    final String key;
    /** Vanity name to SteamID64. */
    final Map<String, String> vanity = new ConcurrentHashMap<>();
    /** SteamID64 to the owned games (the JSON of each). A SteamID64 that is not here answers an empty response. */
    final Map<String, List<Map<String, Object>>> owned = new ConcurrentHashMap<>();
    /** SteamID64 to the recently played games. */
    final Map<String, List<Map<String, Object>>> recent = new ConcurrentHashMap<>();
    /** When not 0, every answer has this status. */
    volatile int status = 0;
    /** When not null, every answer carries this body instead of the normal one. */
    volatile String forcedBody;
    /** When not null, a request whose path contains this answers 500 (one method of the API failing alone). */
    volatile String failingMethod;
    volatile long delayMillis = 0;

    private final HttpServer server;

    SteamStub(String key) {
        this.key = key;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getRawPath();
                String raw = String.valueOf(exchange.getRequestURI().getRawQuery());
                requests.add(new Request(path, raw));
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                Map<String, String> query = parse(raw);
                int code = 200;
                String body;
                if (status != 0) {
                    code = status;
                    body = forcedBody == null ? "" : forcedBody;
                    if (code == 302) {
                        exchange.getResponseHeaders().add("Location", "/elsewhere");
                    }
                } else if (forcedBody != null) {
                    body = forcedBody;
                } else if (failingMethod != null && path.contains(failingMethod)) {
                    code = 500;
                    body = "";
                } else if (!key.equals(query.get("key"))) {
                    code = 403;
                    body = "<html><body><h1>Forbidden</h1></body></html>";
                } else {
                    body = answer(path, query);
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (bytes.length == 0) {
                    exchange.sendResponseHeaders(code, -1);
                } else {
                    exchange.sendResponseHeaders(code, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } catch (IOException | InterruptedException clientGaveUp) {
                // a timed-out client closes the connection while the stub is still answering
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true); // a slow handler (timeout tests) must not block the next request
            return thread;
        }));
        server.start();
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    void reset() {
        requests.clear();
        vanity.clear();
        owned.clear();
        recent.clear();
        status = 0;
        forcedBody = null;
        failingMethod = null;
        delayMillis = 0;
    }

    /** A public account: a vanity name, its SteamID64 and its games. */
    void account(String vanityName, String steamId, List<Map<String, Object>> games, List<Map<String, Object>> recentGames) {
        vanity.put(vanityName, steamId);
        owned.put(steamId, games);
        recent.put(steamId, recentGames);
    }

    /** Requests for one of the three methods the provider may use. */
    List<Request> requestsTo(String method) {
        return requests.stream().filter(r -> r.path().contains(method)).toList();
    }

    private String answer(String path, Map<String, String> query) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (path.contains("ResolveVanityURL")) {
            String id = vanity.get(query.get("vanityurl"));
            if (id == null) {
                response.put("success", 42);
                response.put("message", "No match");
            } else {
                response.put("steamid", id);
                response.put("success", 1);
            }
        } else if (path.contains("GetOwnedGames")) {
            List<Map<String, Object>> games = owned.get(query.get("steamid"));
            if (games != null) { // a private profile (or an id that does not exist) gets {"response":{}}
                response.put("game_count", games.size());
                if (!games.isEmpty()) {
                    response.put("games", games);
                }
            }
        } else if (path.contains("GetRecentlyPlayedGames")) {
            List<Map<String, Object>> games = recent.getOrDefault(query.get("steamid"), List.of());
            response.put("total_count", games.size());
            if (!games.isEmpty()) {
                response.put("games", games);
            }
        } else {
            return "{}";
        }
        return JSON.writeValueAsString(Map.of("response", response));
    }

    private static Map<String, String> parse(String raw) {
        Map<String, String> query = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                query.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return query;
    }

    // ---- fixtures, shaped like the real answer ----

    /** One owned game as GetOwnedGames sends it with include_appinfo=1; {@code lastPlayed} is unix seconds (0 = none). */
    static Map<String, Object> game(long appId, String name, long playtimeMinutes, long lastPlayed) {
        Map<String, Object> game = new LinkedHashMap<>();
        game.put("appid", appId);
        game.put("name", name);
        game.put("playtime_forever", playtimeMinutes);
        game.put("img_icon_url", "0123456789abcdef0123456789abcdef01234567");
        game.put("playtime_windows_forever", playtimeMinutes);
        game.put("rtime_last_played", lastPlayed);
        return game;
    }

    /** The same game as GetRecentlyPlayedGames sends it (no last-played time there). */
    static Map<String, Object> recentGame(long appId, String name, long playtimeMinutes) {
        Map<String, Object> game = new LinkedHashMap<>();
        game.put("appid", appId);
        game.put("name", name);
        game.put("playtime_2weeks", 30);
        game.put("playtime_forever", playtimeMinutes);
        return game;
    }
}
