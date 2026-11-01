package com.meuquarto.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring-specific error codes (bad credentials, invalid body). Business errors are handled for all
 * controllers by {@code DomainErrors} in shared.
 */
@RestControllerAdvice(assignableTypes = {AuthController.class, AccountController.class})
class AuthErrors {

    record ErrorResponse(String error) {}

    /** Same answer for unknown e-mail and wrong password: no account enumeration at sign-in. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> badCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("INVALID_CREDENTIALS"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody() {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST"));
    }
}
