package com.meuquarto.media.infra;

import com.meuquarto.media.domain.LinkedAccount;
import com.meuquarto.media.domain.ProviderId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkedAccountRepository extends JpaRepository<LinkedAccount, Long> {

    List<LinkedAccount> findByProfileIdOrderByCreatedAtAscIdAsc(long profileId);

    /** Filtering by the profile here is what makes someone else's account look like a missing one. */
    Optional<LinkedAccount> findByIdAndProfileId(long id, long profileId);

    boolean existsByProfileIdAndProvider(long profileId, ProviderId provider);
}
