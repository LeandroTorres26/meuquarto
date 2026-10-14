package archfixture.profile.application;

import archfixture.profile.domain.Profile;
import org.springframework.data.repository.Repository;

/** Violation: repository outside infra. */
public interface MisplacedRepository extends Repository<Profile, Long> {}
