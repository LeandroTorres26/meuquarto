package com.meuquarto.identity.api;

import com.meuquarto.identity.application.AccountSettings;
import com.meuquarto.identity.infra.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Account settings for the signed-in user only; the user always comes from the session. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountSettings settings;

    public AccountController(AccountSettings settings) {
        this.settings = settings;
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        settings.changePassword(principal.userId(), body.currentPassword(), body.newPassword(),
                session == null ? null : session.getId());
        request.changeSessionId();
    }

    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest body, HttpServletRequest request, HttpServletResponse response) {
        settings.deleteAccount(principal.userId(), body.password());
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        new CookieClearingLogoutHandler("SESSION").logout(request, response, null);
    }

    public record ChangePasswordRequest(
            @NotNull @Size(max = 128) String currentPassword,
            @NotNull @Size(max = 128) String newPassword) {}

    public record DeleteAccountRequest(@NotNull @Size(max = 128) String password) {}
}
