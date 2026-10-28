package com.meuquarto.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.identity.application.TooManyAttemptsException;
import com.meuquarto.identity.domain.AccountRuleException;
import com.meuquarto.identity.domain.AccountRuleException.Rule;
import com.meuquarto.profile.application.SlugAvailability.Reason;
import com.meuquarto.profile.application.SlugUnavailableException;
import com.meuquarto.profile.domain.InvalidSlugException;
import com.meuquarto.profile.domain.Slug;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every DomainException subclass declares its own code and kind; callers use only the base type. */
class DomainExceptionTest {

    @Test
    void everyAccountRuleHasItsNameAsCodeAndTheRightKind() {
        for (Rule rule : Rule.values()) {
            DomainException e = new AccountRuleException(rule);
            assertThat(e.code()).isEqualTo(rule.name());
            assertThat(e.kind()).isEqualTo(rule == Rule.EMAIL_TAKEN ? ErrorKind.CONFLICT : ErrorKind.INVALID);
            assertThat(e.retryAfter()).isEmpty();
        }
    }

    @Test
    void everySlugReasonIsPrefixedAndOnlyTakenConflicts() {
        for (Reason reason : Reason.values()) {
            DomainException e = new SlugUnavailableException(reason);
            assertThat(e.code()).isEqualTo("SLUG_" + reason.name());
            assertThat(e.kind()).isEqualTo(reason == Reason.TAKEN ? ErrorKind.CONFLICT : ErrorKind.INVALID);
        }
    }

    @Test
    void everyInvalidSlugProblemIsInvalid() {
        for (Slug.Problem problem : Slug.Problem.values()) {
            DomainException e = new InvalidSlugException(problem);
            assertThat(e.code()).isEqualTo("SLUG_" + problem.name());
            assertThat(e.kind()).isEqualTo(ErrorKind.INVALID);
        }
    }

    @Test
    void throttlingOverridesRetryAfter() {
        DomainException e = new TooManyAttemptsException(Duration.ofMinutes(15));
        assertThat(e.code()).isEqualTo("TOO_MANY_ATTEMPTS");
        assertThat(e.kind()).isEqualTo(ErrorKind.TOO_MANY);
        assertThat(e.retryAfter()).contains(Duration.ofMinutes(15));
    }

    @Test
    void aListOfDifferentSubclassesIsUsedThroughTheBaseType() {
        List<DomainException> errors = new ArrayList<>();
        errors.add(new AccountRuleException(Rule.EMAIL_TAKEN));
        errors.add(new InvalidSlugException(Slug.Problem.RESERVED));
        errors.add(new TooManyAttemptsException(Duration.ofSeconds(1)));

        assertThat(errors).extracting(DomainException::code)
                .containsExactly("EMAIL_TAKEN", "SLUG_RESERVED", "TOO_MANY_ATTEMPTS");
        assertThat(errors).extracting(DomainException::kind)
                .containsExactly(ErrorKind.CONFLICT, ErrorKind.INVALID, ErrorKind.TOO_MANY);
    }
}
