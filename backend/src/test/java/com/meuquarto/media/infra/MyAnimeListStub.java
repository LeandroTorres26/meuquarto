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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * A local stand-in for api.myanimelist.net v2: answers {@code /users/{name}/animelist} and {@code /mangalist}
 * like the real API (paging by limit and offset, a {@code paging.next} URL, 404 for a missing user, 403 for a
 * private list, 401 for a client id it does not know). Records every request, with the client id header kept
 * apart from the URL, so a test can prove what was sent and that the id never travels in the address.
 * The {@code paging.next} it sends points at an address that can never resolve ({@code .invalid}), so a
 * provider that followed it would fail loudly instead of reaching a real server. No real network.
 */
final class MyAnimeListStub implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern PATH = Pattern.compile("/v2/users/([^/]+)/(animelist|mangalist)");

    record Request(String path, String rawQuery, String clientIdHeader) {}

    final List<Request> requests = new CopyOnWriteArrayList<>();
    /** The client id the stub accepts; any other (or none) gets 401. */
    final String clientId;
    /** Users that exist, by name, with their anime and manga lists (the JSON of each entry). */
    final Map<String, List<Map<String, Object>>> anime = new ConcurrentHashMap<>();
    final Map<String, List<Map<String, Object>>> manga = new ConcurrentHashMap<>();
    /** Users whose animelist/mangalist answer 403: {@code "name"} for both lists, {@code "name:animelist"} for one. */
    final List<String> privateLists = new CopyOnWriteArrayList<>();
    /** When not 0, every answer has this status. */
    volatile int status = 0;
    /** When not null, every answer carries this body instead of the normal one. */
    volatile String forcedBody;
    volatile long delayMillis = 0;

    private final HttpServer server;

    MyAnimeListStub(String clientId) {
        this.clientId = clientId;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getRawPath();
                String query = String.valueOf(exchange.getRequestURI().getRawQuery());
                requests.add(new Request(path, query, exchange.getRequestHeaders().getFirst("X-MAL-CLIENT-ID")));
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                int code = 200;
                String body;
                Matcher match = PATH.matcher(path);
                if (status != 0) {
                    code = status;
                    body = forcedBody == null ? "" : forcedBody;
                    if (code == 302) {
                        exchange.getResponseHeaders().add("Location", "/v2/elsewhere");
                    }
                } else if (forcedBody != null) {
                    body = forcedBody;
                } else if (!clientId.equals(exchange.getRequestHeaders().getFirst("X-MAL-CLIENT-ID"))) {
                    code = 401;
                    body = "{\"error\":\"invalid_token\"}";
                } else if (!match.matches()) {
                    code = 404;
                    body = "{\"error\":\"not_found\"}";
                } else {
                    String name = URLDecoder.decode(match.group(1), StandardCharsets.UTF_8);
                    String list = match.group(2);
                    if (!anime.containsKey(name) && !manga.containsKey(name)) {
                        code = 404;
                        body = "{\"error\":\"not_found\"}";
                    } else if (privateLists.contains(name) || privateLists.contains(name + ":" + list)) {
                        code = 403;
                        body = "{\"error\":\"forbidden\"}";
                    } else {
                        body = page(name, list, parse(query));
                    }
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
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    void reset() {
        requests.clear();
        anime.clear();
        manga.clear();
        privateLists.clear();
        status = 0;
        forcedBody = null;
        delayMillis = 0;
    }

    /** A user with these lists (an empty list is fine: the account exists). */
    void user(String name, List<Map<String, Object>> animeList, List<Map<String, Object>> mangaList) {
        anime.put(name, animeList);
        manga.put(name, mangaList);
    }

    /** Requests whose path is a list endpoint (the only kind the provider should ever send). */
    List<Request> listRequests() {
        return requests.stream().filter(r -> PATH.matcher(r.path()).matches()).toList();
    }

    private String page(String name, String list, Map<String, String> query) {
        List<Map<String, Object>> all = (list.equals("animelist") ? anime : manga).getOrDefault(name, List.of());
        int limit = Math.min(Integer.parseInt(query.getOrDefault("limit", "100")), 1000);
        int offset = Integer.parseInt(query.getOrDefault("offset", "0"));
        List<Map<String, Object>> slice = all.subList(Math.min(offset, all.size()), Math.min(offset + limit, all.size()));
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("data", slice);
        Map<String, Object> paging = new LinkedHashMap<>();
        if (offset + limit < all.size()) {
            paging.put("next", "https://next.invalid/v2/users/" + name + "/" + list + "?offset=" + (offset + limit)
                    + "&limit=" + limit);
        }
        answer.put("paging", paging);
        return JSON.writeValueAsString(answer);
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

    /** One list entry. {@code flag} is {@code is_rewatching} (anime) or {@code is_rereading} (manga). */
    static Map<String, Object> entry(long malId, String status, boolean repeating, boolean manga, String start,
            String finish, String updatedAt) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", malId);
        node.put("title", "MAL title " + malId);
        node.put("main_picture", Map.of("medium", "https://cdn.myanimelist.net/images/x/" + malId + ".jpg"));
        Map<String, Object> listStatus = new LinkedHashMap<>();
        listStatus.put("status", status);
        listStatus.put("score", 8);
        listStatus.put(manga ? "num_chapters_read" : "num_episodes_watched", 3);
        listStatus.put(manga ? "is_rereading" : "is_rewatching", repeating);
        listStatus.put("updated_at", updatedAt);
        if (start != null) {
            listStatus.put("start_date", start);
        }
        if (finish != null) {
            listStatus.put("finish_date", finish);
        }
        return Map.of("node", node, "list_status", listStatus);
    }

    /** An anime entry updated at {@code updatedAt}, with no dates of its own. */
    static Map<String, Object> anime(long malId, String status, String updatedAt) {
        return entry(malId, status, false, false, null, null, updatedAt);
    }

    static Map<String, Object> manga(long malId, String status, String updatedAt) {
        return entry(malId, status, false, true, null, null, updatedAt);
    }

    /** {@code count} completed anime entries with ids from {@code firstId}, updated one minute apart going back. */
    static List<Map<String, Object>> completed(long firstId, int count) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(anime(firstId + i, "completed", java.time.Instant.parse("2026-10-30T12:00:00Z")
                    .minusSeconds(60L * i).toString().replace("Z", "+00:00")));
        }
        return entries;
    }
}
