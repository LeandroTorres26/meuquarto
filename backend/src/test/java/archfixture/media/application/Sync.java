package archfixture.media.application;

import archfixture.profile.domain.Profile;

/** Violation: another module reaches into the profile domain (and media -> profile closes the cycle). */
public class Sync {
    Profile profile;
}
