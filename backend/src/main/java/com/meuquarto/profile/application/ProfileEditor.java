package com.meuquarto.profile.application;

import com.meuquarto.profile.domain.Bio;
import com.meuquarto.profile.domain.DisplayName;
import com.meuquarto.profile.domain.Plan;
import com.meuquarto.profile.domain.Profile;
import com.meuquarto.profile.domain.ProfileException;
import com.meuquarto.profile.domain.ProfileException.Problem;
import com.meuquarto.profile.domain.ProfileTheme;
import com.meuquarto.profile.domain.Slug;
import com.meuquarto.profile.infra.ProfileRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The profile editor (S6, FR-05): read and change the signed-in owner's name, bio, slug and theme.
 * The owner is a parameter taken from the session by the controller, never from the request body.
 * The public profile of the old and the new slug is dropped from the cache once the change commits.
 */
@Service
public class ProfileEditor {

    private final ProfileRepository profiles;
    private final SlugAvailability availability;
    private final PlanResolver plans;
    private final PublicProfileCache cache;

    public ProfileEditor(ProfileRepository profiles, SlugAvailability availability, PlanResolver plans,
            PublicProfileCache cache) {
        this.profiles = profiles;
        this.availability = availability;
        this.plans = plans;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public Editable view(long userId) {
        return editable(profileOf(userId), plans.planOf(userId));
    }

    /**
     * Replaces the four editable fields. Every field is validated before anything is written; a changed slug
     * must be valid and free (BR-01), and the old one is released.
     */
    @Transactional
    public Editable update(long userId, String rawName, String rawBio, String rawSlug, String rawTheme) {
        Profile profile = profileOf(userId);
        Plan plan = plans.planOf(userId);

        DisplayName name = DisplayName.of(rawName);
        Bio bio = Bio.of(rawBio);
        ProfileTheme theme = ProfileTheme.of(rawTheme);
        if (!plan.canApply(theme.tier())) {
            throw new ProfileException(Problem.THEME_LOCKED); // BR-05
        }
        String oldSlug = profile.slug();
        String newSlug = Slug.normalize(rawSlug);
        boolean slugChanges = !newSlug.equals(oldSlug);
        if (slugChanges) {
            SlugAvailability.Result result = availability.check(newSlug);
            if (!result.available()) {
                throw new SlugUnavailableException(result.reason());
            }
            profile.changeSlug(Slug.of(newSlug));
        }
        profile.rename(name);
        profile.changeBio(bio);
        profile.changeTheme(theme);
        try {
            profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException raceWithAnotherChange) {
            throw new SlugUnavailableException(SlugAvailability.Reason.TAKEN);
        }
        cache.evictAfterCommit(oldSlug);
        if (slugChanges) {
            cache.evictAfterCommit(newSlug);
        }
        return editable(profile, plan);
    }

    private Profile profileOf(long userId) {
        return profiles.findByUserId(userId).orElseThrow(() -> new ProfileException(Problem.PROFILE_NOT_FOUND));
    }

    private static Editable editable(Profile profile, Plan plan) {
        return new Editable(profile.slug(), profile.displayName().orElse(profile.slug()), profile.bio().orElse(""),
                profile.avatarKey().map(Avatars::urlPath).orElse(null), profile.theme().id(),
                ProfileTheme.availableTo(plan).stream().map(ProfileTheme::id).toList());
    }

    /** What S6 shows. {@code displayName} falls back to the slug until the owner picks one; {@code avatarUrl} is null without avatar. */
    public record Editable(String slug, String displayName, String bio, String avatarUrl, String theme,
            List<String> availableThemes) {}
}
