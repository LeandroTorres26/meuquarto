package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.shared.domain.ValidationRule;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A platform whose public activity we read by username (Letterboxd, AniList, Last.fm, Steam,
 * Goodreads), seen only through this contract (ADR 0018, hierarchy 1). The sync and the linking code
 * hold an {@code ActivityProvider} and never ask which class it really is.
 * <p>
 * Features of the course used here: an interface, {@code default} methods ({@link #normalizeUsername},
 * {@link #validateUsernameFormat}) and generics (the username rules are
 * {@code ValidationRule<String, UsernameProblem>}).
 */
public interface ActivityProvider {

    ProviderId id();

    /** The categories this platform can feed (Letterboxd: movies; AniList: anime and manga...). */
    Set<MediaCategory> categories();

    /** The format rules for a normalized username of this platform (see {@link UsernameRules}). */
    List<ValidationRule<String, UsernameProblem>> usernameRules();

    /** Default: trim. A platform whose names are case-insensitive overrides it to lower-case them. */
    default String normalizeUsername(String raw) {
        return raw == null ? "" : raw.strip();
    }

    /**
     * Default method: runs the rules of the platform over a username already normalized. Nothing is
     * sent to the network before this answers empty.
     */
    default Optional<UsernameProblem> validateUsernameFormat(String normalizedUsername) {
        return ValidationRule.firstProblem(usernameRules(), normalizedUsername);
    }

    /** Asks the platform whether the account exists and is public. Network; never throws for provider failures. */
    AccountCheck checkAccountExists(String username);

    /**
     * Reads the recent activity of the user. Network. The username must already be valid; a provider
     * failure throws a {@code LinkedAccountException} with problem {@code PROVIDER_UNAVAILABLE}, so a
     * sync can record the failure for this one account and go on with the others.
     */
    ActivitySnapshot fetchActivity(String username);
}
