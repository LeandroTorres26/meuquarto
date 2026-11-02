package com.meuquarto.identity.application;

import com.meuquarto.identity.domain.AccountRuleException;
import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.identity.domain.EmailAddress;
import com.meuquarto.identity.domain.PasswordPolicy;
import com.meuquarto.identity.domain.PasswordResetToken;
import com.meuquarto.identity.domain.ResetToken;
import com.meuquarto.identity.domain.SiteUrl;
import com.meuquarto.identity.domain.User;
import com.meuquarto.identity.infra.PasswordResetTokenRepository;
import com.meuquarto.identity.infra.UserRepository;
import com.meuquarto.identity.infra.UserSessions;
import com.meuquarto.shared.ratelimit.FixedWindowLimiter;
import com.meuquarto.shared.ratelimit.RateLimitProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Esqueci minha senha" (FR-01a, ADR 0012).
 * <p>
 * Requesting never reveals whether the e-mail has an account: every branch ends the same way, does
 * the same work (a token is generated and hashed, and the database is asked one question) and the
 * e-mail itself is handed to the mailer, which sends it asynchronously. Throttled requests are
 * dropped silently for the same reason. Confirming spends the token, sets the password and ends all
 * sessions and every other pending token of the user.
 */
@Service
public class PasswordReset {

    private static final Duration KEEP_EXPIRED = Duration.ofDays(1);

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final UserSessions sessions;
    private final PasswordResetMailer mailer;
    private final SiteUrl siteUrl;
    private final SecureRandom random;
    private final Clock clock = Clock.systemUTC();
    private final FixedWindowLimiter limiter;
    private final RateLimitProperties.Rule perEmail;

    public PasswordReset(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, UserSessions sessions, PasswordResetMailer mailer, SiteUrl siteUrl,
            SecureRandom random, FixedWindowLimiter limiter, RateLimitProperties rateLimits) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessions = sessions;
        this.mailer = mailer;
        this.siteUrl = siteUrl;
        this.random = random;
        this.limiter = limiter;
        this.perEmail = rateLimits.passwordResetEmail();
    }

    /** Sends a reset link if the e-mail has an account. Always returns normally, whatever the e-mail. */
    public void request(String rawEmail) {
        ResetToken token = ResetToken.generate(random);
        String hash = token.hash(); // computed on every path, so hashing cost is not a signal
        Optional<String> email = normalized(rawEmail);
        boolean allowed = email.isPresent()
                && limiter.hit("password-reset-email|" + email.get(), perEmail.limit(), perEmail.window()).isEmpty();
        Optional<User> user = email.flatMap(users::findByEmail);
        if (user.isEmpty() || !allowed) {
            tokens.existsByTokenHash(hash); // one query on the "nothing to do" path as well
            return;
        }
        tokens.save(new PasswordResetToken(user.get().id(), token, clock.instant()));
        mailer.sendResetLink(user.get().email(), siteUrl.resetLink(token));
    }

    /**
     * Sets a new password with a reset token. Unknown, tampered, expired and spent tokens all fail with
     * the same {@link Rule#INVALID_RESET_TOKEN}. A weak new password fails without spending the token,
     * so the person can try another one with the same link.
     */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        ResetToken token = ResetToken.parse(rawToken);
        if (token == null) {
            throw invalidToken();
        }
        Instant now = clock.instant();
        PasswordResetToken stored = tokens.findByTokenHash(token.hash())
                .filter(t -> t.isUsable(now))
                .orElseThrow(PasswordReset::invalidToken);
        User user = users.findById(stored.userId()).orElseThrow(PasswordReset::invalidToken);
        passwordPolicy.check(newPassword, new EmailAddress(user.email()));
        if (tokens.spend(stored.id(), now) != 1) { // lost a race with another use of the same link
            throw invalidToken();
        }
        tokens.spendAllOf(user.id(), now);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        sessions.endAllExcept(user.email(), null);
    }

    /** Used and expired rows are only history; drop them a day after they stop mattering. */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeOldTokens() {
        tokens.deleteExpiredBefore(clock.instant().minus(KEEP_EXPIRED));
    }

    private static Optional<String> normalized(String rawEmail) {
        try {
            return Optional.of(EmailAddress.of(rawEmail).value());
        } catch (AccountRuleException notAnEmail) {
            return Optional.empty();
        }
    }

    private static AccountRuleException invalidToken() {
        return new AccountRuleException(Rule.INVALID_RESET_TOKEN);
    }
}
