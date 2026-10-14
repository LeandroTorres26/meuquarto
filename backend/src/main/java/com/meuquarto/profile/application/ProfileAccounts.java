package com.meuquarto.profile.application;

import com.meuquarto.profile.domain.Profile;
import com.meuquarto.profile.domain.Slug;
import com.meuquarto.profile.infra.ProfileRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** What the identity module needs from profiles: create one at sign-up and find a user's slug. */
@Service
public class ProfileAccounts {

    private final ProfileRepository profiles;
    private final SlugAvailability availability;

    public ProfileAccounts(ProfileRepository profiles, SlugAvailability availability) {
        this.profiles = profiles;
        this.availability = availability;
    }

    /** Runs inside the caller's transaction, so a failed sign-up leaves no profile behind. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String createProfile(long userId, String rawSlug) {
        SlugAvailability.Result result = availability.check(rawSlug);
        if (!result.available()) {
            throw new SlugUnavailableException(result.reason());
        }
        try {
            return profiles.saveAndFlush(new Profile(userId, Slug.of(result.slug()))).slug();
        } catch (DataIntegrityViolationException raceWithAnotherSignUp) {
            throw new SlugUnavailableException(SlugAvailability.Reason.TAKEN);
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> slugOf(long userId) {
        return profiles.findByUserId(userId).map(Profile::slug);
    }

    public static class SlugUnavailableException extends RuntimeException {

        private final SlugAvailability.Reason reason;

        public SlugUnavailableException(SlugAvailability.Reason reason) {
            super("Slug unavailable: " + reason);
            this.reason = reason;
        }

        public SlugAvailability.Reason reason() {
            return reason;
        }
    }
}
