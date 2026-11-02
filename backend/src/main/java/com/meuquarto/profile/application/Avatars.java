package com.meuquarto.profile.application;

import com.meuquarto.profile.domain.AvatarException;
import com.meuquarto.profile.domain.AvatarException.Problem;
import com.meuquarto.profile.domain.AvatarKey;
import com.meuquarto.profile.domain.AvatarProcessor;
import com.meuquarto.profile.domain.Profile;
import com.meuquarto.profile.domain.ProfileException;
import com.meuquarto.profile.infra.ProfileRepository;
import com.meuquarto.shared.storage.ObjectStorage;
import com.meuquarto.shared.storage.StorageUnavailableException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The owner's avatar (FR-05): upload, replace, remove, and the read that serves it to everyone. The
 * picture is validated and re-encoded outside any transaction ({@link AvatarProcessor}), stored under a
 * new random key, and only then does the profile row point to it. The previous object is deleted after
 * that commit, as a best effort: a failed delete leaves an unreferenced object, never a broken profile.
 * The owner always comes from the session.
 */
@Service
public class Avatars {

    private static final Logger log = LoggerFactory.getLogger(Avatars.class);
    private static final String URL_PREFIX = "/api/public/avatars/";

    private final ProfileRepository profiles;
    private final ObjectStorage storage;
    private final PublicProfileCache cache;
    private final TransactionTemplate transaction;
    private final AvatarProcessor processor = new AvatarProcessor();

    public Avatars(ProfileRepository profiles, ObjectStorage storage, PublicProfileCache cache,
            PlatformTransactionManager transactionManager) {
        this.profiles = profiles;
        this.storage = storage;
        this.cache = cache;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** The URL path of an avatar for the page (same origin, served by {@link #serve}). */
    public static String urlPath(String key) {
        return URL_PREFIX + new AvatarKey(key).publicName();
    }

    /** Validates, re-encodes and stores the picture, then makes it the owner's avatar. Returns its URL path. */
    public String upload(long userId, byte[] upload) {
        AvatarProcessor.Result avatar = processor.process(upload);
        AvatarKey key = AvatarKey.generate();
        try {
            storage.put(key.value(), avatar.content(), avatar.contentType());
        } catch (StorageUnavailableException e) {
            log.warn("avatar not stored: storage unavailable");
            throw new AvatarException(Problem.STORAGE_UNAVAILABLE);
        }
        try {
            transaction.executeWithoutResult(status -> {
                Profile profile = lockedProfileOf(userId);
                Optional<String> previous = profile.avatarKey();
                profile.useAvatar(key.value());
                cache.evictAfterCommit(profile);
                previous.ifPresent(this::deleteAfterCommit);
            });
        } catch (RuntimeException e) {
            deleteQuietly(key.value()); // the new object will never be referenced
            throw e;
        }
        return urlPath(key.value());
    }

    /** Clears the owner's avatar and deletes the stored picture. Removing when there is none is not an error. */
    public void remove(long userId) {
        transaction.executeWithoutResult(status -> {
            Profile profile = lockedProfileOf(userId);
            Optional<String> previous = profile.avatarKey();
            profile.removeAvatar();
            cache.evictAfterCommit(profile);
            previous.ifPresent(this::deleteAfterCommit);
        });
    }

    /**
     * The stored picture for a public name from the URL. A name that is not exactly one we generate is "not
     * found" without asking storage anything.
     */
    public ObjectStorage.StoredObject serve(String publicName) {
        AvatarKey key = AvatarKey.fromPublicName(publicName)
                .orElseThrow(() -> new AvatarException(Problem.AVATAR_NOT_FOUND));
        try {
            return storage.get(key.value()).orElseThrow(() -> new AvatarException(Problem.AVATAR_NOT_FOUND));
        } catch (StorageUnavailableException e) {
            log.warn("avatar not read: storage unavailable");
            throw new AvatarException(Problem.STORAGE_UNAVAILABLE);
        }
    }

    /** Deletes the object once the surrounding transaction commits; used when a profile loses its avatar or goes away. */
    void deleteAfterCommit(String key) {
        AfterCommit.run(() -> deleteQuietly(key));
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("avatar object not deleted: {}", e.getClass().getSimpleName());
        }
    }

    private Profile lockedProfileOf(long userId) {
        long id = profiles.findByUserId(userId)
                .orElseThrow(() -> new ProfileException(ProfileException.Problem.PROFILE_NOT_FOUND)).id();
        return profiles.lockById(id)
                .orElseThrow(() -> new ProfileException(ProfileException.Problem.PROFILE_NOT_FOUND));
    }
}
