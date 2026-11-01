package com.meuquarto.media.infra;

import com.meuquarto.media.domain.LinkedAccount;
import com.meuquarto.media.domain.ProviderId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LinkedAccountRepository extends JpaRepository<LinkedAccount, Long> {

    List<LinkedAccount> findByProfileIdOrderByCreatedAtAscIdAsc(long profileId);

    /** Filtering by the profile here is what makes someone else's account look like a missing one. */
    Optional<LinkedAccount> findByIdAndProfileId(long id, long profileId);

    boolean existsByProfileIdAndProvider(long profileId, ProviderId provider);

    /**
     * Starts a sync for the account if nobody started one since {@code cooldownLimit}: one atomic update
     * (BR-03), so a manual click and the scheduler, or two clicks, cannot both win. Returns 1 if this
     * caller owns the run, 0 if it lost.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LinkedAccount a set a.lastSyncStartedAt = :now
            where a.id = :id and (a.lastSyncStartedAt is null or a.lastSyncStartedAt <= :cooldownLimit)
            """)
    int claimSync(@Param("id") long id, @Param("now") Instant now, @Param("cooldownLimit") Instant cooldownLimit);

    /**
     * What the scheduler picks (BR-03, ADR 0007): never synced or last success {@code staleBefore} or
     * older, not backing off, not synced by hand in the cooldown. Oldest first, never-synced first of all.
     */
    @Query("""
            select a from LinkedAccount a
            where (a.lastSyncedAt is null or a.lastSyncedAt <= :staleBefore)
              and (a.nextAttemptAt is null or a.nextAttemptAt <= :now)
              and (a.lastSyncStartedAt is null or a.lastSyncStartedAt <= :cooldownLimit)
            order by a.lastSyncedAt asc nulls first, a.id asc
            """)
    List<LinkedAccount> findDue(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now,
            @Param("cooldownLimit") Instant cooldownLimit, Pageable page);
}
