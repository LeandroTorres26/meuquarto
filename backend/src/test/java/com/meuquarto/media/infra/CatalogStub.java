package com.meuquarto.media.infra;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local stand-in for a GET catalog API (Open Library, Deezer): answers every request with the body and
 * status the test sets, and records the path and User-Agent of each one. Nothing here touches the network.
 */
final class CatalogStub implements AutoCloseable {

    record Request(String path, String userAgent) {}

    final List<Request> requests = new CopyOnWriteArrayList<>();
    volatile int status = 200;
    volatile String body = "{}";
    /** Milliseconds to wait before answering (for the timeout test). */
    volatile long delayMillis = 0;

    private final HttpServer server;

    CatalogStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            requests.add(new Request(exchange.getRequestURI().toString(), exchange.getRequestHeaders().getFirst("User-Agent")));
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    static String fixture(String name) {
        try {
            return Files.readString(Path.of("src/test/resources/fixtures", name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
