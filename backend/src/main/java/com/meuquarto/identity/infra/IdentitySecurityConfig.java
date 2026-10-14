package com.meuquarto.identity.infra;

import com.meuquarto.identity.domain.LoginThrottle;
import com.meuquarto.identity.domain.PasswordPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Ready-made Spring Security pieces wired to our users table (ADR 0012). No hand-written crypto. */
@Configuration
public class IdentitySecurityConfig {

    /**
     * Argon2id with OWASP's minimum (19 MiB memory, 2 iterations, parallelism 1), behind a delegating
     * encoder so the algorithm can be upgraded later: hashes are stored as {@code {argon2}...}.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        String id = "argon2";
        return new DelegatingPasswordEncoder(id, Map.of(id, new Argon2PasswordEncoder(16, 32, 1, 19_456, 2)));
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(email)
                .map(u -> new AccountPrincipal(u.id(), u.email(), u.passwordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** 5 failed sign-ins per e-mail and client address in 15 minutes. */
    @Bean
    LoginThrottle loginThrottle() {
        return new LoginThrottle(5, Duration.ofMinutes(15), Clock.systemUTC());
    }

    @Bean
    PasswordPolicy passwordPolicy() throws IOException {
        try (InputStream in = new ClassPathResource("identity/common-passwords.txt").getInputStream()) {
            Set<String> common = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(l -> !l.isEmpty()).collect(Collectors.toSet());
            return new PasswordPolicy(common);
        }
    }
}
