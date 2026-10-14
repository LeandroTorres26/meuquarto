package com.meuquarto.profile.infra;

import com.meuquarto.profile.domain.Profile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    boolean existsBySlug(String slug);

    Optional<Profile> findByUserId(long userId);
}
