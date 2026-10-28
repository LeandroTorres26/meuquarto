package com.meuquarto.media.api;

import com.meuquarto.media.application.LinkedAccounts;
import com.meuquarto.media.domain.LinkStatus;
import com.meuquarto.media.domain.LinkedAccount;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in owner's linked accounts (FR-12, FR-14, FR-16). Every route needs a session; the owner comes from it. */
@RestController
@RequestMapping("/api/linked-accounts")
public class LinkedAccountController {

    /** Longer than any platform username; it only bounds the body before the platform rules run. */
    private static final int MAX_USERNAME = 100;

    private final LinkedAccounts linkedAccounts;

    public LinkedAccountController(LinkedAccounts linkedAccounts) {
        this.linkedAccounts = linkedAccounts;
    }

    @GetMapping
    public List<AccountResponse> list(@CurrentUserId long userId) {
        return linkedAccounts.list(userId).stream().map(AccountResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse link(@CurrentUserId long userId, @Valid @RequestBody LinkRequest body) {
        return AccountResponse.of(linkedAccounts.link(userId, body.provider(), body.username()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@CurrentUserId long userId, @PathVariable long id) {
        linkedAccounts.unlink(userId, id);
    }

    public record LinkRequest(@NotNull ProviderId provider, @NotBlank @Size(max = MAX_USERNAME) String username) {}

    public record AccountResponse(long id, ProviderId provider, String username, LinkStatus status,
            Instant lastSyncedAt, String lastError) {

        static AccountResponse of(LinkedAccount account) {
            return new AccountResponse(account.id(), account.provider(), account.username(), account.status(),
                    account.lastSyncedAt(), account.lastError());
        }
    }
}
