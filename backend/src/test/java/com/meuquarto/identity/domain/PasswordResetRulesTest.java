package com.meuquarto.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** FR-01a / ADR 0012: reset token shape, hashing, lifetime and the SITE_URL the link is built from. No Spring. */
class PasswordResetRulesTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static String sha256Hex(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    class Token {

        @Test
        void has256BitsWrittenAsFortyThreeUrlSafeCharacters() {
            ResetToken token = ResetToken.generate(RANDOM);

            assertThat(token.value()).matches("[A-Za-z0-9_-]{43}");
            assertThat(ResetToken.parse(token.value())).isNotNull();
        }

        @Test
        void neverRepeats() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                assertThat(seen.add(ResetToken.generate(RANDOM).value())).isTrue();
            }
        }

        @Test
        void isStoredAsItsSha256Hex() {
            ResetToken token = ResetToken.parse("A".repeat(43));

            // Computed here with the JDK directly, independently of ResetToken.
            assertThat(token.hash()).isEqualTo(sha256Hex("A".repeat(43)));
            assertThat(token.hash()).isNotEqualTo(ResetToken.parse("B".repeat(43)).hash());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "short", "A", "A!AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                " AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+/="})
        void anythingElseIsNotOneOfOurs(String candidate) {
            assertThat(ResetToken.parse(candidate)).isNull();
        }

        @Test
        void nullIsNotOneOfOurs() {
            assertThat(ResetToken.parse(null)).isNull();
        }

        @Test
        void doesNotShowItsValueWhenPrinted() {
            ResetToken token = ResetToken.generate(RANDOM);

            assertThat(token.toString()).doesNotContain(token.value());
        }
    }

    @Nested
    class Lifetime {

        private final Instant now = Instant.parse("2026-11-01T12:00:00Z");
        private final PasswordResetToken stored = new PasswordResetToken(7, ResetToken.generate(RANDOM), now);

        @Test
        void isUsableForThirtyMinutes() {
            assertThat(stored.isUsable(now)).isTrue();
            assertThat(stored.isUsable(now.plus(Duration.ofMinutes(29)).plusSeconds(59))).isTrue();
        }

        @Test
        void expiresAtThirtyMinutes() {
            assertThat(stored.isUsable(now.plus(Duration.ofMinutes(30)))).isFalse();
            assertThat(stored.isUsable(now.plus(Duration.ofHours(2)))).isFalse();
        }
    }

    @Nested
    class Site {

        private final ResetToken token = ResetToken.parse("T".repeat(43));

        @Test
        void blankFallsBackToTheProductionOrigin() {
            assertThat(SiteUrl.parse("").resetLink(token)).isEqualTo("https://meuquarto.com.br/redefinir-senha?token=" + "T".repeat(43));
            assertThat(SiteUrl.parse(null).resetLink(token)).startsWith("https://meuquarto.com.br/");
        }

        @Test
        void keepsOnlyTheOrigin() {
            assertThat(SiteUrl.parse("https://Meuquarto.com.br/").resetLink(token))
                    .startsWith("https://meuquarto.com.br/redefinir-senha?token=");
            assertThat(SiteUrl.parse("http://localhost:3000").resetLink(token))
                    .startsWith("http://localhost:3000/redefinir-senha?token=");
        }

        @ParameterizedTest
        @ValueSource(strings = {"meuquarto.com.br", "ftp://meuquarto.com.br", "javascript:alert(1)", "http://meuquarto.com.br",
                "https://user:pass@meuquarto.com.br", "https://meuquarto.com.br/app", "https://meuquarto.com.br?x=1",
                "https://meuquarto.com.br#top", "https://", "not a url"})
        void anythingButAnHttpsOriginIsRefusedAtStartup(String configured) {
            // The message is a fixed text: whatever was configured (maybe a pasted secret) is not echoed.
            String fixed = assertThatThrownBy(() -> SiteUrl.parse("ftp://x"))
                    .isInstanceOf(IllegalArgumentException.class).actual().getMessage();
            assertThatThrownBy(() -> SiteUrl.parse(configured))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(fixed);
        }
    }
}
