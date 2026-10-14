package archfixture.profile.domain;

import archfixture.profile.infra.ProfileStore;

/** Violation: domain depends on infra. */
public class Profile {
    ProfileStore store;
}
