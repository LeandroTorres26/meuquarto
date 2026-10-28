package com.meuquarto.profile.infra;

import com.meuquarto.profile.domain.Profile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    boolean existsBySlug(String slug);

    Optional<Profile> findByUserId(long userId);

    /**
     * Locks the profile row until the transaction ends. Writers of one collection (the owner adding,
     * a sync) go through this lock, so the check "is the work already here" and the insert cannot interleave.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profile p where p.id = :id")
    Optional<Profile> lockById(@Param("id") long id);
}
