package com.meuquarto.shared.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP rate limits for the abuse-prone public endpoints plus a ceiling for anonymous traffic.
 * Added to the security filter chain (after the session is loaded, before CSRF), so a 429 still
 * carries the security headers and a forged cookie does not buy the anonymous ceiling away.
 * Not a Spring bean on purpose: Boot would also register a bean filter as a plain servlet filter.
 * The client address is {@code getRemoteAddr()}; behind a reverse proxy the app must enable forwarded
 * headers (docs/security.md).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private record Limit(String name, RequestMatcher matcher, RateLimitProperties.Rule rule) {}

    private final FixedWindowLimiter limiter;
    private final List<Limit> routeLimits;
    private final RateLimitProperties.Rule anonymous;

    public RateLimitFilter(FixedWindowLimiter limiter, RateLimitProperties props) {
        this.limiter = limiter;
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        this.routeLimits = List.of(
                new Limit("signup", paths.matcher(HttpMethod.POST, "/api/auth/signup"), props.signup()),
                new Limit("login", paths.matcher(HttpMethod.POST, "/api/auth/login"), props.login()),
                new Limit("slug-availability", paths.matcher(HttpMethod.GET, "/api/slugs/*/availability"),
                        props.slugAvailability()),
                new Limit("catalog-search", paths.matcher(HttpMethod.GET, "/api/catalog/search"), props.catalogSearch()),
                new Limit("collection-add", paths.matcher(HttpMethod.POST, "/api/collection"), props.collectionAdd()),
                new Limit("link-account", paths.matcher(HttpMethod.POST, "/api/linked-accounts"), props.linkAccount()),
                new Limit("manual-sync", paths.matcher(HttpMethod.POST, "/api/linked-accounts/*/sync"), props.manualSync()),
                new Limit("avatar-upload", paths.matcher(HttpMethod.POST, "/api/profile/avatar"), props.avatarUpload()),
                new Limit("public-read", paths.matcher(HttpMethod.GET, "/api/public/**"), props.publicRead()));
        this.anonymous = props.anonymous();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Duration retryAfter = null;
        for (Limit limit : routeLimits) {
            if (limit.matcher().matches(request)) {
                retryAfter = longer(retryAfter,
                        limiter.hit(limit.name() + "|" + ip, limit.rule().limit(), limit.rule().window()));
            }
        }
        if (isAnonymous()) {
            retryAfter = longer(retryAfter, limiter.hit("anonymous|" + ip, anonymous.limit(), anonymous.window()));
        }
        if (retryAfter == null) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, (retryAfter.toMillis() + 999) / 1000)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"TOO_MANY_REQUESTS\"}");
    }

    private static Duration longer(Duration current, Optional<Duration> candidate) {
        if (candidate.isEmpty()) {
            return current;
        }
        return current == null || candidate.get().compareTo(current) > 0 ? candidate.get() : current;
    }

    private static boolean isAnonymous() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated();
    }
}
