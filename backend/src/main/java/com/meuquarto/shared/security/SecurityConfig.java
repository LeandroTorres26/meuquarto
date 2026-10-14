package com.meuquarto.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Deny by default: every route needs authentication unless listed here (docs/security.md).
 * Sessions live in PostgreSQL through Spring Session; CSRF uses the SPA cookie-to-header pattern:
 * the XSRF-TOKEN cookie is readable by the web app, which sends it back in the X-XSRF-TOKEN header.
 */
@Configuration
public class SecurityConfig {

    /**
     * Session cookie flags set explicitly: Spring Session writes its own cookie and does not pick up
     * {@code server.servlet.session.cookie.*}. Secure is fine on http://localhost (browsers allow it).
     */
    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        return serializer;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/slugs/*/availability").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa())
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("SESSION"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
