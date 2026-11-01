package com.meuquarto.media.infra;

import com.meuquarto.media.domain.SyncedEntry;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SyncedEntryRepository extends JpaRepository<SyncedEntry, Long> {

    /**
     * Records the activity unless the same (account, work, kind, time) is already there. Returns 1 for a
     * new row and 0 for a repeat, which is how a second run over the same feed changes nothing. One atomic
     * statement, so two runs at once cannot raise a unique violation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO synced_entries (linked_account_id, media_item_id, activity_type, occurred_at)
            VALUES (:accountId, :mediaItemId, :activityType, :occurredAt)
            ON CONFLICT (linked_account_id, media_item_id, activity_type, occurred_at) DO NOTHING
            """)
    int insertIfNew(@Param("accountId") long accountId, @Param("mediaItemId") long mediaItemId,
            @Param("activityType") String activityType, @Param("occurredAt") Instant occurredAt);
}
