package com.meuquarto.identity.api;

import com.meuquarto.identity.application.Accounts;
import com.meuquarto.identity.infra.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON sign-up, sign-in and current user. Sign-out is Spring Security's logout at
 * {@code POST /api/auth/logout}. Every POST needs the CSRF header (cookie-to-header, see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Accounts accounts;
    private final SecurityContextRepository contextRepository;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SessionAuthenticationStrategy sessionFixation = new ChangeSessionIdAuthenticationStrategy();

    public AuthController(Accounts accounts, SecurityContextRepository contextRepository) {
        this.accounts = accounts;
        this.contextRepository = contextRepository;
    }

    /** Lets the web app obtain the CSRF cookie before its first POST. */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        token.getToken(); // loading the deferred token writes the XSRF-TOKEN cookie
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signUp(@Valid @RequestBody SignUpRequest body, HttpServletRequest request, HttpServletResponse response) {
        Accounts.SignedUp signedUp = accounts.signUp(body.email(), body.password(), body.slug());
        startSession(UsernamePasswordAuthenticationToken.authenticated(signedUp.principal(), null, signedUp.principal().getAuthorities()),
                request, response);
        return new MeResponse(signedUp.principal().getUsername(), signedUp.slug());
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = accounts.signIn(body.email(), body.password(), request.getRemoteAddr());
        startSession(authentication, request, response);
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        return new MeResponse(principal.getUsername(), accounts.slugOf(principal.userId()).orElse(null));
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return new MeResponse(principal.getUsername(), accounts.slugOf(principal.userId()).orElse(null));
    }

    /** New session id on every sign-in (no session fixation), then the context goes into the session. */
    private void startSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        sessionFixation.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    public record SignUpRequest(
            @NotNull @Size(max = 254) String email,
            @NotNull @Size(max = 128) String password,
            @NotNull @Size(max = 64) String slug) {}

    public record LoginRequest(
            @NotNull @Size(max = 254) String email,
            @NotNull @Size(max = 128) String password) {}

    public record MeResponse(String email, String slug) {}
}
