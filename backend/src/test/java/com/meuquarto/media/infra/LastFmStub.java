package com.meuquarto.media.infra;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.json.JsonMapper;

/**
 * A local stand-in for ws.audioscrobbler.com: answers {@code user.getrecenttracks} like Last.fm does,
 * including its habit of returning errors as JSON (error 6 user not found, 10 invalid key, 17 private).
 * Records every request's raw query so a test can prove what was and was not sent. No real network.
 */
final class LastFmStub implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final String COVER = "https://lastfm.freetls.fastly.net/i/u/300x300/0a1b2c3d4e5f60718293a4b5c6d7e8f9.jpg";
    static final String PLACEHOLDER = "https://lastfm.freetls.fastly.net/i/u/300x300/2a96cbd8b46e442fc41c2b86b821562f.png";

    /** The raw (still percent-encoded) query of a request. */
    final List<String> requests = new CopyOnWriteArrayList<>();
    /** The key the stub accepts; any other gets error 10. */
    final String key;
    /** Users that exist and are public; others get error 6. */
    volatile List<String> knownUsers = List.of("ana_l");
    /** Users with private listening (error 17). */
    volatile List<String> privateUsers = List.of();
    /** What a known user's recent tracks are (the JSON of the whole answer). */
    volatile String answer = page();
    /** When not 0, every answer has this status. */
    volatile int status = 0;
    /** When not null, every answer carries this body instead of the normal one. */
    volatile String forcedBody;
    volatile long delayMillis = 0;

    private final HttpServer server;

    LastFmStub(String key) {
        this.key = key;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            try {
                String raw = String.valueOf(exchange.getRequestURI().getRawQuery());
                requests.add(raw);
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                Map<String, String> query = parse(raw);
                int code = 200;
                String body;
                if (status != 0) {
                    code = status;
                    body = forcedBody == null ? "" : forcedBody;
                } else if (forcedBody != null) {
                    body = forcedBody;
                } else if (!key.equals(query.get("api_key"))) {
                    code = 403;
                    body = error(10, "Invalid API key - You must be granted a valid key by last.fm");
                } else if (privateUsers.contains(query.get("user"))) {
                    body = error(17, "Login: User required to be logged in");
                } else if (!knownUsers.contains(query.get("user"))) {
                    code = 404;
                    body = error(6, "User not found");
                } else {
                    body = answer;
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
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true); // a slow handler (timeout tests) must not block the next request
            return thread;
        }));
        server.start();
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/2.0/");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    void reset() {
        requests.clear();
        knownUsers = List.of("ana_l");
        privateUsers = List.of();
        answer = page();
        status = 0;
        forcedBody = null;
        delayMillis = 0;
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

    static String error(int code, String message) {
        return JSON.writeValueAsString(Map.of("error", code, "message", message));
    }

    // ---- fixtures, shaped like the real answer ----

    /** A scrobble of {@code album} by {@code artist} at {@code uts} (epoch seconds), with the usual four image sizes. */
    static Map<String, Object> scrobble(String artist, String album, long uts, String cover) {
        Map<String, Object> track = base(artist, album, cover);
        track.put("date", Map.of("uts", Long.toString(uts), "#text", "01 Oct 2026, 12:00"));
        return track;
    }

    /** The track playing now: flagged {@code nowplaying} and, as on Last.fm, without a date. */
    static Map<String, Object> nowPlaying(String artist, String album, String cover) {
        Map<String, Object> track = base(artist, album, cover);
        track.put("@attr", Map.of("nowplaying", "true"));
        return track;
    }

    private static Map<String, Object> base(String artist, String album, String cover) {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("artist", Map.of("mbid", "", "#text", artist));
        track.put("streamable", "0");
        List<Map<String, String>> images = new ArrayList<>();
        for (String size : List.of("small", "medium", "large", "extralarge")) {
            images.add(Map.of("size", size, "#text", cover == null ? "" : cover));
        }
        track.put("image", images);
        track.put("mbid", "");
        track.put("album", Map.of("mbid", "", "#text", album));
        track.put("name", "Some song");
        track.put("url", "https://www.last.fm/music/x/_/y");
        return track;
    }

    /** {@code count} scrobbles of one album, one per day going back from {@code newestUts}. */
    static List<Map<String, Object>> plays(String artist, String album, int count, long newestUts, String cover) {
        List<Map<String, Object>> tracks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tracks.add(scrobble(artist, album, newestUts - i * 86_400L, cover));
        }
        return tracks;
    }

    /** The whole answer for a list of tracks. */
    static String pageOf(List<Map<String, Object>> tracks) {
        return JSON.writeValueAsString(Map.of("recenttracks", Map.of(
                "track", tracks,
                "@attr", Map.of("user", "ana_l", "page", "1", "perPage", "200", "totalPages", "1", "total", "1"))));
    }

    @SafeVarargs
    static String page(Map<String, Object>... tracks) {
        return pageOf(List.of(tracks));
    }
}
