package com.meuquarto.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.shared.ratelimit.FixedWindowLimiter;
import com.meuquarto.support.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * ADR 0017: X-Forwarded-For from a peer that is not a trusted proxy is ignored. The peer here is
 * 127.0.0.1 and the trusted range is narrowed to 203.0.113.0/24, so the peer counts as an outsider.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "server.tomcat.remoteip.internal-proxies=203.0.113.0/24",
    "meuquarto.rate-limit.slug-availability.limit=3",
    "meuquarto.rate-limit.anonymous.limit=1000",
})
class UntrustedProxyApiTests {

    @Autowired
    FixedWindowLimiter limiter;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    @AfterEach
    void resetLimiter() {
        limiter.reset();
    }

    @Test
    void forwardedForFromAnUntrustedPeerIsIgnored() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(slugAvailability("198.51.100." + (10 + i))).isEqualTo(200);
        }
        // Every request came from the same peer, so rotating the header never opens a new bucket.
        assertThat(slugAvailability("198.51.100.99")).isEqualTo(429);
    }

    private int slugAvailability(String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/slugs/free/availability"))
                .header("X-Forwarded-For", forwardedFor)
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
