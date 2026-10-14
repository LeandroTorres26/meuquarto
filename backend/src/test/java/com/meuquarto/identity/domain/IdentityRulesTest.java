package com.meuquarto.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.identity.domain.AccountRuleException.Rule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentityRulesTest {

    @Nested
    class Email {

        @Test
        void isTrimmedAndLowercased() {
            assertThat(EmailAddress.of("  Leandro@Example.COM ").value()).isEqualTo("leandro@example.com");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "leandro", "leandro@", "@example.com", "leandro@example", "a b@example.com", "a@b@c.com"})
        void malformedIsRejected(String raw) {
            assertRule(() -> EmailAddress.of(raw), Rule.INVALID_EMAIL);
        }

        @Test
        void longerThan254IsRejected() {
            assertRule(() -> EmailAddress.of("a".repeat(250) + "@x.com"), Rule.INVALID_EMAIL);
        }
    }

    @Nested
    class Password {

        private final PasswordPolicy policy = new PasswordPolicy(Set.of("senha123", "12345678"));
        private final EmailAddress email = EmailAddress.of("leandro@example.com");

        @Test
        void longEnoughUncommonPasswordIsAccepted() {
            assertThatCode(() -> policy.check("cavalo bateria grampo", email)).doesNotThrowAnyException();
        }

        @Test
        void shorterThan8IsRejected() {
            assertRule(() -> policy.check("abc1234", email), Rule.PASSWORD_TOO_SHORT);
        }

        @Test
        void longerThan128IsRejected() {
            assertRule(() -> policy.check("a".repeat(129), email), Rule.PASSWORD_TOO_LONG);
        }

        @Test
        void commonPasswordIsRejectedIgnoringCase() {
            assertRule(() -> policy.check("SENHA123", email), Rule.PASSWORD_TOO_COMMON);
        }

        @Test
        void emailOrItsLocalPartIsRejected() {
            assertRule(() -> policy.check("leandro@example.com", email), Rule.PASSWORD_SAME_AS_EMAIL);
            PasswordPolicy anyList = new PasswordPolicy(Set.of());
            EmailAddress longLocal = EmailAddress.of("leandrotorres@example.com");
            assertRule(() -> anyList.check("LeandroTorres", longLocal), Rule.PASSWORD_SAME_AS_EMAIL);
        }
    }

    @Nested
    class Throttle {

        private final MutableClock clock = new MutableClock(Instant.parse("2026-10-14T10:00:00Z"));
        private final LoginThrottle throttle = new LoginThrottle(3, Duration.ofMinutes(15), clock);

        @Test
        void allowsUntilTheLimitThenAsksToWait() {
            throttle.recordFailure("k");
            throttle.recordFailure("k");
            assertThat(throttle.retryAfter("k")).isEmpty();

            throttle.recordFailure("k");
            assertThat(throttle.retryAfter("k")).contains(Duration.ofMinutes(15));
        }

        @Test
        void failuresLeaveTheWindow() {
            for (int i = 0; i < 3; i++) {
                throttle.recordFailure("k");
            }
            clock.advance(Duration.ofMinutes(15).plusSeconds(1));
            assertThat(throttle.retryAfter("k")).isEmpty();
        }

        @Test
        void successClearsAndKeysAreIndependent() {
            for (int i = 0; i < 3; i++) {
                throttle.recordFailure("k");
            }
            assertThat(throttle.retryAfter("other")).isEmpty();
            throttle.recordSuccess("k");
            assertThat(throttle.retryAfter("k")).isEmpty();
        }
    }

    private static void assertRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Rule rule) {
        assertThatThrownBy(call).isInstanceOf(AccountRuleException.class)
                .extracting(e -> ((AccountRuleException) e).rule()).isEqualTo(rule);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
