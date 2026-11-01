package com.meuquarto.identity.application;

import com.meuquarto.identity.domain.AccountRuleException;
import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.identity.domain.EmailAddress;
import com.meuquarto.identity.domain.LoginThrottle;
import com.meuquarto.identity.domain.PasswordPolicy;
import com.meuquarto.identity.domain.User;
import com.meuquarto.identity.infra.AccountPrincipal;
import com.meuquarto.identity.infra.UserRepository;
import com.meuquarto.profile.application.ProfileAccounts;
import java.time.Duration;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-up and sign-in use cases (FR-01, ADR 0012). Sessions are handled by the api layer. */
@Service
public class Accounts {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthenticationManager authenticationManager;
    private final LoginThrottle throttle;
    private final ProfileAccounts profiles;

    public Accounts(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            AuthenticationManager authenticationManager, LoginThrottle throttle, ProfileAccounts profiles) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.authenticationManager = authenticationManager;
        this.throttle = throttle;
        this.profiles = profiles;
    }

    /** Creates the user and its profile in one transaction; either both exist or neither. */
    @Transactional
    public SignedUp signUp(String rawEmail, String password, String rawSlug) {
        EmailAddress email = EmailAddress.of(rawEmail);
        passwordPolicy.check(password, email);
        if (users.existsByEmail(email.value())) {
            throw new AccountRuleException(Rule.EMAIL_TAKEN);
        }
        User user;
        try {
            user = users.saveAndFlush(new User(email, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException raceWithAnotherSignUp) {
            throw new AccountRuleException(Rule.EMAIL_TAKEN);
        }
        String slug = profiles.createProfile(user.id(), rawSlug);
        return new SignedUp(new AccountPrincipal(user.id(), user.email(), null), slug);
    }

    /**
     * Checks the password with Spring Security. Refused while the e-mail and client address are
     * throttled; every failure counts, a success clears the counter.
     */
    public Authentication signIn(String rawEmail, String password, String clientAddress) {
        String email = rawEmail == null ? "" : rawEmail.strip().toLowerCase(java.util.Locale.ROOT);
        String key = email + "|" + clientAddress;
        Optional<Duration> wait = throttle.retryAfter(key);
        if (wait.isPresent()) {
            throw new TooManyAttemptsException(wait.get());
        }
        try {
            Authentication result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            throttle.recordSuccess(key);
            return result;
        } catch (AuthenticationException e) {
            throttle.recordFailure(key);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> slugOf(long userId) {
        return profiles.slugOf(userId);
    }

    public record SignedUp(AccountPrincipal principal, String slug) {}
}
