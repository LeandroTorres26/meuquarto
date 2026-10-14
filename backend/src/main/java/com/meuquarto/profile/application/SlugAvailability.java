package com.meuquarto.profile.application;

import com.meuquarto.profile.domain.Slug;
import com.meuquarto.profile.infra.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tells whether a slug can be used for a new profile (BR-01: valid and not taken). */
@Service
public class SlugAvailability {

    private final ProfileRepository profiles;

    public SlugAvailability(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public Result check(String raw) {
        String normalized = Slug.normalize(raw);
        return Slug.check(normalized)
                .map(problem -> new Result(normalized, false, Reason.valueOf(problem.name())))
                .orElseGet(() -> profiles.existsBySlug(normalized)
                        ? new Result(normalized, false, Reason.TAKEN)
                        : new Result(normalized, true, null));
    }

    public record Result(String slug, boolean available, Reason reason) {}

    /** Why a slug is unavailable; mirrors {@link Slug.Problem} plus {@code TAKEN}. */
    public enum Reason {
        TOO_SHORT, TOO_LONG, INVALID_CHARACTERS, INVALID_HYPHEN, RESERVED, TAKEN
    }
}
