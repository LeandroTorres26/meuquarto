package com.meuquarto.media.application;

import com.meuquarto.media.domain.AccountCheck;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityRegistry;
import com.meuquarto.media.domain.LinkedAccount;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.media.infra.LinkedAccountRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Linking, listing and unlinking accounts by public username (FR-12, FR-14, FR-16). The owner is
 * always the signed-in user, given by the caller; an account that is not the owner's behaves exactly
 * like one that does not exist. Nothing here knows a platform: it only talks to {@link ActivityProvider}.
 */
@Service
public class LinkedAccounts {

    private final LinkedAccountRepository accounts;
    private final ActivityRegistry registry;
    private final AccountOwners owners;
    private final Clock clock = Clock.systemUTC();

    public LinkedAccounts(LinkedAccountRepository accounts, ActivityRegistry registry, AccountOwners owners) {
        this.accounts = accounts;
        this.registry = registry;
        this.owners = owners;
    }

    /**
     * Order matters for safety: the format rules of the platform run before any network call, and the
     * outbound request is made outside any database transaction.
     */
    public LinkedAccount link(long userId, ProviderId providerId, String rawUsername) {
        long profileId = profileIdOf(userId);
        ActivityProvider provider = registry.find(providerId)
                .orElseThrow(() -> new LinkedAccountException(Problem.PROVIDER_UNAVAILABLE));
        String username = provider.normalizeUsername(rawUsername);
        if (provider.validateUsernameFormat(username).isPresent()) {
            throw new LinkedAccountException(Problem.INVALID_USERNAME);
        }
        if (accounts.existsByProfileIdAndProvider(profileId, providerId)) {
            throw new LinkedAccountException(Problem.ALREADY_LINKED);
        }
        AccountCheck check = provider.checkAccountExists(username);
        switch (check) {
            case NOT_FOUND -> throw new LinkedAccountException(Problem.ACCOUNT_NOT_FOUND_ON_PROVIDER);
            case UNAVAILABLE -> throw new LinkedAccountException(Problem.PROVIDER_UNAVAILABLE);
            case EXISTS -> { }
        }
        try {
            return accounts.saveAndFlush(LinkedAccount.link(profileId, providerId, username, clock.instant()));
        } catch (DataIntegrityViolationException race) {
            // Two requests at once: the unique (profile, provider) constraint decides who wins.
            if (accounts.existsByProfileIdAndProvider(profileId, providerId)) {
                throw new LinkedAccountException(Problem.ALREADY_LINKED);
            }
            throw race;
        }
    }

    @Transactional(readOnly = true)
    public List<LinkedAccount> list(long userId) {
        return accounts.findByProfileIdOrderByCreatedAtAscIdAsc(profileIdOf(userId));
    }

    /** FR-16: the collection is released first, then the account row is deleted, in one transaction. */
    @Transactional
    public void unlink(long userId, long accountId) {
        long profileId = profileIdOf(userId);
        LinkedAccount account = accounts.findByIdAndProfileId(accountId, profileId)
                .orElseThrow(() -> new LinkedAccountException(Problem.LINKED_ACCOUNT_NOT_FOUND));
        owners.releaseCollectionItems(profileId, account.id());
        accounts.delete(account);
        accounts.flush();
    }

    private long profileIdOf(long userId) {
        return owners.profileIdOf(userId)
                .orElseThrow(() -> new LinkedAccountException(Problem.LINKED_ACCOUNT_NOT_FOUND));
    }
}
