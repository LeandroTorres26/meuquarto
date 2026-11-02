package com.meuquarto.identity.infra;

import com.meuquarto.identity.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    boolean existsByTokenHash(String tokenHash);

    /** Spends the token if it is still unused and unexpired. Returns 1 for the one caller that won, 0 otherwise. */
    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int spend(@Param("id") long id, @Param("now") Instant now);

    /** Spends every pending token of the user. */
    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int spendAllOf(@Param("userId") long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
