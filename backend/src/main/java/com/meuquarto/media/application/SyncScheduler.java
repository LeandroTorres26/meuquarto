package com.meuquarto.media.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The timer of ADR 0007: every 15 minutes it asks {@link SyncService} to sync a bounded batch of due
 * accounts. All the rules (what is due, cooldown, backoff, isolation) live in the service and the domain;
 * this class only ticks, so tests call {@link #runBatch()} directly and never wait for the clock.
 * <p>
 * Single instance only: with two API instances both would tick. The atomic claim in the service keeps them
 * from syncing the same account twice, but a real lock (ShedLock) or a queue would be needed for scale.
 * Scheduling can be switched off with {@code meuquarto.sync.scheduler.enabled=false} (see {@code SyncSchedulingConfig}).
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncService sync;
    private final int batchSize;

    public SyncScheduler(SyncService sync, @Value("${meuquarto.sync.batch-size:20}") int batchSize) {
        this.sync = sync;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${meuquarto.sync.scheduler.interval:PT15M}",
            initialDelayString = "${meuquarto.sync.scheduler.initial-delay:PT1M}")
    public void runBatch() {
        try {
            int synced = sync.syncDueAccounts(batchSize);
            if (synced > 0) {
                log.info("scheduled sync: {} account(s) synced", synced);
            }
        } catch (RuntimeException e) {
            log.warn("scheduled sync batch failed: {}", e.getClass().getSimpleName());
        }
    }
}
