package com.meuquarto.identity.application;

import com.meuquarto.identity.application.Accounts.TooManyAttemptsException;
import com.meuquarto.identity.domain.AccountRuleException;
import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.identity.domain.EmailAddress;
import com.meuquarto.identity.domain.LoginThrottle;
import com.meuquarto.identity.domain.PasswordPolicy;
import com.meuquarto.identity.domain.User;
import com.meuquarto.identity.infra.UserRepository;
import com.meuquarto.identity.infra.UserSessions;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sensitive account actions (S14). Both need the current password; wrong guesses count against the
 * same throttle as sign-in, so a stolen session cannot be used to brute-force the password.
 */
@Service
public class AccountSettings {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginThrottle throttle;
    private final UserSessions sessions;

    public AccountSettings(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            LoginThrottle throttle, UserSessions sessions) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.throttle = throttle;
        this.sessions = sessions;
    }

    /** Changes the password and ends every other session of the user (the current one stays). */
    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword, String currentSessionId) {
        User user = confirmed(userId, currentPassword);
        passwordPolicy.check(newPassword, new EmailAddress(user.email()));
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        sessions.endAllExcept(user.email(), currentSessionId);
    }

    /**
     * Deletes the account (FR-02). The database cascades to the profile and everything under it;
     * orders, once they exist (#38), are kept anonymized by their own foreign key rule.
     */
    @Transactional
    public void deleteAccount(long userId, String password) {
        User user = confirmed(userId, password);
        users.delete(user);
        sessions.endAllExcept(user.email(), null);
    }

    private User confirmed(long userId, String password) {
        String key = "confirm|" + userId;
        throttle.retryAfter(key).ifPresent(wait -> {
            throw new TooManyAttemptsException(wait);
        });
        User user = users.findById(userId).orElseThrow(() -> new AccountRuleException(Rule.WRONG_PASSWORD));
        if (password == null || !passwordEncoder.matches(password, user.passwordHash())) {
            throttle.recordFailure(key);
            throw new AccountRuleException(Rule.WRONG_PASSWORD);
        }
        throttle.recordSuccess(key);
        return user;
    }
}
