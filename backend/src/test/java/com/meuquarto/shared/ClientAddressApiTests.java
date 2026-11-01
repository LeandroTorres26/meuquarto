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
 * ADR 0017: the per-IP limits use the address in X-Forwarded-For, but only when the direct peer is a
 * trusted proxy. This needs the real embedded Tomcat (the RemoteIpValve is not part of MockMvc), so the
 * requests go over a socket; the peer is 127.0.0.1, which the production configuration trusts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "meuquarto.rate-limit.slug-availability.limit=3",
    "meuquarto.rate-limit.anonymous.limit=1000",
})
class ClientAddressApiTests {

    private static final String PROXY_ADDRESS = "127.0.0.1";

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
    void forwardedClientsFromATrustedProxyHaveSeparateBuckets() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(slugAvailability("198.51.100.10")).isEqualTo(200);
        }
        assertThat(slugAvailability("198.51.100.10")).isEqualTo(429);

        assertThat(slugAvailability("198.51.100.11")).isEqualTo(200);
        assertThat(slugAvailability("2001:db8::7")).isEqualTo(200);
    }

    @Test
    void noForwardedHeaderFallsBackToThePeerAddress() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(slugAvailability(null)).isEqualTo(200);
        }
        assertThat(slugAvailability(null)).isEqualTo(429);
        // The proxy itself is the bucket here; a forwarded client is still separate.
        assertThat(slugAvailability("198.51.100.12")).isEqualTo(200);
    }

    @Test
    void aSpoofedChainDoesNotBuyAFreshBucket() throws Exception {
        // Trusted proxies are skipped from the right; the first untrusted entry is the client, so
        // prepending fake addresses on the left changes nothing.
        for (int i = 0; i < 3; i++) {
            assertThat(slugAvailability("198.51.100.20")).isEqualTo(200);
        }
        assertThat(slugAvailability("203.0.113.99, 198.51.100.20")).isEqualTo(429);
    }

    private int slugAvailability(String forwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://" + PROXY_ADDRESS + ":" + port + "/api/slugs/free/availability"));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
