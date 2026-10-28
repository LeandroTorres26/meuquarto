package com.meuquarto.media.application;

import com.meuquarto.media.domain.ActivityEntry;
import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityRegistry;
import com.meuquarto.media.domain.ActivitySnapshot;
import com.meuquarto.media.domain.CatalogEntry;
import com.meuquarto.media.domain.LinkedAccount;
import com.meuquarto.media.domain.LinkedAccountException;
import com.meuquarto.media.domain.LinkedAccountException.Problem;
import com.meuquarto.media.domain.SyncCooldownException;
import com.meuquarto.media.domain.SyncOutcome;
import com.meuquarto.media.domain.SyncPolicy;
import com.meuquarto.media.domain.SyncRun;
import com.meuquarto.media.infra.LinkedAccountRepository;
import com.meuquarto.media.infra.SyncRunRepository;
import com.meuquarto.media.infra.SyncedEntryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The sync engine (FR-13, BR-03, BR-10, ADR 0007): reads the activity of a linked account from its
 * platform and applies it to the owner's collection. It is the caller of the polymorphism in ADR 0018,
 * hierarchy 1: it asks the {@link ActivityRegistry} for an {@link ActivityProvider} and calls
 * {@link ActivityProvider#fetchActivity} on it, like the PetShop calling {@code gritaria()} on every animal.
 * Nothing here knows which platform it is talking to, so adding one never changes this class.
 * <p>
 * Order of a run, chosen for safety and cost:
 * <ol>
 *   <li>claim the run with one atomic update (the BR-03 cooldown, also against the scheduler);</li>
 *   <li>fetch from the platform, <b>outside any database transaction</b> (network is slow and not ours);</li>
 *   <li>apply everything in one transaction: skip adult works (BR-08), snapshot each work, store the raw
 *       activity (deduplicated, so a repeated run inserts nothing) and record new activity into the
 *       collection through the {@link AccountOwners} port (ADR 0020);</li>
 *   <li>record the run and the account's health, with the backoff on failure.</li>
 * </ol>
 * A failure is recorded for this one account and never stops the others (NFR-03). Only our own error
 * codes are stored, never provider text.
 */
@Service
public class SyncService {

    /** A provider returns at most a few hundred entries; this only bounds a misbehaving one. */
    static final int MAX_ENTRIES_PER_RUN = 1000;

    private static final String FAILURE_CODE = "SYNC_FAILED";
    private static final String NO_PROVIDER_CODE = Problem.PROVIDER_UNAVAILABLE.name();

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final LinkedAccountRepository accounts;
    private final SyncRunRepository runs;
    private final SyncedEntryRepository entries;
    private final ActivityRegistry registry;
    private final MediaSnapshots snapshots;
    private final AccountOwners owners;
    private final TransactionTemplate transaction;
    private final Clock clock = Clock.systemUTC();

    public SyncService(LinkedAccountRepository accounts, SyncRunRepository runs, SyncedEntryRepository entries,
            ActivityRegistry registry, MediaSnapshots snapshots, AccountOwners owners,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.runs = runs;
        this.entries = entries;
        this.registry = registry;
        this.snapshots = snapshots;
        this.owners = owners;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * "Sincronizar agora" (BR-03). The account must be the signed-in user's (anyone else's looks missing).
     * Runs in the caller's thread, bounded by the provider's timeouts; if it ever needs to be asynchronous
     * only this method changes (ADR 0007). A provider failure is a result ({@code FAILED}), not an error.
     *
     * @throws SyncCooldownException when the account was synced less than 15 minutes ago
     */
    public SyncOutcome syncNow(long userId, long accountId) {
        long profileId = owners.profileIdOf(userId)
                .orElseThrow(() -> new LinkedAccountException(Problem.LINKED_ACCOUNT_NOT_FOUND));
        LinkedAccount account = accounts.findByIdAndProfileId(accountId, profileId)
                .orElseThrow(() -> new LinkedAccountException(Problem.LINKED_ACCOUNT_NOT_FOUND));
        Instant now = clock.instant();
        Optional<Duration> wait = SyncPolicy.cooldownRemaining(account.lastSyncStartedAt(), now);
        if (wait.isPresent()) {
            throw new SyncCooldownException(wait.get());
        }
        if (!claim(account.id(), now)) {
            // Another run (a click or the scheduler) started between the check and the claim.
            Duration remaining = accounts.findById(account.id())
                    .flatMap(fresh -> SyncPolicy.cooldownRemaining(fresh.lastSyncStartedAt(), clock.instant()))
                    .orElse(SyncPolicy.MANUAL_COOLDOWN);
            throw new SyncCooldownException(remaining);
        }
        return run(account.id(), now);
    }

    /**
     * What the scheduler calls: syncs up to {@code batchSize} accounts that are due (stale, not backing off,
     * not synced in the cooldown), oldest first. Each account is isolated from the others.
     *
     * @return how many accounts were synced (successfully or not)
     */
    public int syncDueAccounts(int batchSize) {
        Instant now = clock.instant();
        List<LinkedAccount> due = accounts.findDue(now.minus(SyncPolicy.STALE_AFTER), now,
                now.minus(SyncPolicy.MANUAL_COOLDOWN), PageRequest.of(0, batchSize));
        int synced = 0;
        for (LinkedAccount account : due) {
            try {
                Instant startedAt = clock.instant();
                if (claim(account.id(), startedAt)) {
                    run(account.id(), startedAt);
                    synced++;
                }
            } catch (RuntimeException e) {
                // Defensive: run() already turns failures into results; this keeps one account from ending the batch.
                log.warn("scheduled sync of one account aborted: {}", e.getClass().getSimpleName());
            }
        }
        return synced;
    }

    private boolean claim(long accountId, Instant now) {
        return accounts.claimSync(accountId, now, now.minus(SyncPolicy.MANUAL_COOLDOWN)) == 1;
    }

    /** One claimed run: fetch (no transaction), apply (one transaction), record. Never throws for provider failures. */
    private SyncOutcome run(long accountId, Instant startedAt) {
        Optional<LinkedAccount> found = accounts.findById(accountId);
        if (found.isEmpty()) {
            return SyncOutcome.skipped(Problem.LINKED_ACCOUNT_NOT_FOUND.name()); // unlinked meanwhile
        }
        LinkedAccount account = found.get();
        Optional<ActivityProvider> provider = registry.find(account.provider());
        SyncOutcome outcome;
        if (provider.isEmpty()) {
            outcome = SyncOutcome.skipped(NO_PROVIDER_CODE);
        } else {
            outcome = fetchAndApply(account, provider.get());
        }
        finish(accountId, startedAt, outcome);
        return outcome;
    }

    private SyncOutcome fetchAndApply(LinkedAccount account, ActivityProvider provider) {
        ActivitySnapshot activity;
        try {
            activity = provider.fetchActivity(account.username()); // network: no transaction is open
        } catch (LinkedAccountException e) {
            return SyncOutcome.failed(e.code());
        } catch (RuntimeException e) {
            log.warn("sync fetch failed for {}: {}", account.provider(), e.getClass().getSimpleName());
            return SyncOutcome.failed(FAILURE_CODE);
        }
        try {
            return transaction.execute(status -> apply(account, provider, activity));
        } catch (RuntimeException e) {
            log.warn("sync apply failed for {}: {}", account.provider(), e.getClass().getSimpleName());
            return SyncOutcome.failed(FAILURE_CODE);
        }
    }

    /** BR-10 and BR-08, generic for every platform; what a platform counts as activity is the provider's own rule. */
    private SyncOutcome apply(LinkedAccount account, ActivityProvider provider, ActivitySnapshot activity) {
        int applied = 0;
        for (ActivityEntry entry : activity.entries().stream().limit(MAX_ENTRIES_PER_RUN).toList()) {
            CatalogEntry work = entry.work();
            if (work.adult() || !provider.categories().contains(work.category())) {
                continue; // BR-08: adult works never enter; a category the platform cannot feed is not trusted
            }
            MediaSnapshots.Snapshot media = snapshots.snapshot(work);
            int inserted = entries.insertIfNew(account.id(), media.id(), entry.type().name(), entry.occurredAt());
            if (inserted == 1) {
                owners.recordSyncedItem(account.profileId(), account.id(), account.provider().name(),
                        work.category(), media.id(), entry.occurredAt());
            }
            applied++;
        }
        return SyncOutcome.success(applied);
    }

    /** Stores the run and updates the account's health. If the account was unlinked meanwhile there is nothing to update. */
    private void finish(long accountId, Instant startedAt, SyncOutcome outcome) {
        try {
            transaction.executeWithoutResult(status -> accounts.findById(accountId).ifPresent(account -> {
                Instant now = clock.instant();
                switch (outcome.status()) {
                    case SUCCESS -> account.syncSucceeded(now);
                    case FAILED -> account.syncFailed(outcome.error(), now);
                    case SKIPPED -> account.syncSkipped(now);
                }
                accounts.save(account);
                runs.save(new SyncRun(accountId, startedAt, now, outcome));
            }));
        } catch (RuntimeException e) {
            log.warn("could not record a sync run: {}", e.getClass().getSimpleName());
        }
    }
}
