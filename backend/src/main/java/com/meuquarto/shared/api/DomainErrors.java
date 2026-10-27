package com.meuquarto.shared.api;

import com.meuquarto.shared.domain.DomainException;
import com.meuquarto.shared.domain.ErrorKind;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One handler for every {@link DomainException} (ADR 0018). Polymorphism does the work: the handler
 * asks the exception for its code, kind and wait time instead of testing which class it is.
 * Never echoes input or internals.
 */
@RestControllerAdvice
class DomainErrors {

    record ErrorResponse(String error) {}

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErrorResponse> domain(DomainException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(statusOf(e.kind()));
        e.retryAfter().ifPresent(wait ->
                response.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, wait.toSeconds()))));
        return response.body(new ErrorResponse(e.code()));
    }

    static HttpStatus statusOf(ErrorKind kind) {
        return switch (kind) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }
}
