package com.meuquarto.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.identity.application.TooManyAttemptsException;
import com.meuquarto.identity.domain.AccountRuleException;
import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.shared.domain.ErrorKind;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** The single handler maps ErrorKind to HTTP; no Spring context needed to test it. */
class DomainErrorsTest {

    private final DomainErrors handler = new DomainErrors();

    @Test
    void everyKindHasAStatus() {
        assertThat(DomainErrors.statusOf(ErrorKind.INVALID)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(DomainErrors.statusOf(ErrorKind.CONFLICT)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(DomainErrors.statusOf(ErrorKind.TOO_MANY)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(DomainErrors.statusOf(ErrorKind.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(DomainErrors.statusOf(ErrorKind.UNAVAILABLE)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void bodyCarriesOnlyTheCode() {
        var response = handler.domain(new AccountRuleException(Rule.EMAIL_TAKEN));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("EMAIL_TAKEN");
        assertThat(response.getHeaders().containsHeader(HttpHeaders.RETRY_AFTER)).isFalse();
    }

    @Test
    void throttledAnswerHasRetryAfterOfAtLeastOneSecond() {
        var response = handler.domain(new TooManyAttemptsException(Duration.ofMillis(200)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(response.getBody().error()).isEqualTo("TOO_MANY_ATTEMPTS");
    }
}
