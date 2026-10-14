package com.meuquarto.identity.api;

import com.meuquarto.identity.application.Accounts.TooManyAttemptsException;
import com.meuquarto.identity.domain.AccountRuleException;
import com.meuquarto.profile.application.ProfileAccounts.SlugUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error codes the web app turns into messages. Never echoes input or internals. */
@RestControllerAdvice(assignableTypes = {AuthController.class, AccountController.class})
class AuthErrors {

    record ErrorResponse(String error) {}

    @ExceptionHandler(AccountRuleException.class)
    ResponseEntity<ErrorResponse> accountRule(AccountRuleException e) {
        HttpStatus status = e.rule() == AccountRuleException.Rule.EMAIL_TAKEN ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorResponse(e.rule().name()));
    }

    @ExceptionHandler(SlugUnavailableException.class)
    ResponseEntity<ErrorResponse> slug(SlugUnavailableException e) {
        HttpStatus status = e.reason().name().equals("TAKEN") ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorResponse("SLUG_" + e.reason().name()));
    }

    /** Same answer for unknown e-mail and wrong password: no account enumeration at sign-in. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> badCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("INVALID_CREDENTIALS"));
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    ResponseEntity<ErrorResponse> throttled(TooManyAttemptsException e) {
        long seconds = Math.max(1, e.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(new ErrorResponse("TOO_MANY_ATTEMPTS"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody() {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST"));
    }
}
