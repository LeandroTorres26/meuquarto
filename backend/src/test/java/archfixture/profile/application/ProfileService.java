package archfixture.profile.application;

import archfixture.media.application.Sync;
import archfixture.profile.api.ProfileDto;

/** Violations: application depends on api; profile -> media closes a cycle with Sync. */
public class ProfileService {
    ProfileDto dto;
    Sync sync;
}
