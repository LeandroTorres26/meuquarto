package com.meuquarto.profile.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Where an avatar lives in storage: {@code avatars/<random uuid>.jpg}. Keys are generated here and
 * nowhere else, so neither the file name nor anything else the user sent ends up in a path. The
 * public name (the part after {@code avatars/}) is what appears in the avatar URL; reading it back
 * accepts only exactly this shape, which also rules out path traversal.
 */
public record AvatarKey(String value) {

    public static final String PREFIX = "avatars/";
    public static final String CONTENT_TYPE = "image/jpeg";

    private static final Pattern PUBLIC_NAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");

    public AvatarKey {
        if (!value.startsWith(PREFIX) || !PUBLIC_NAME.matcher(value.substring(PREFIX.length())).matches()) {
            throw new IllegalArgumentException("not an avatar key");
        }
    }

    /** A new random key. */
    public static AvatarKey generate() {
        return new AvatarKey(PREFIX + UUID.randomUUID() + ".jpg");
    }

    /** The key for a name taken from a URL; empty for anything that is not exactly a name we generated. */
    public static Optional<AvatarKey> fromPublicName(String name) {
        if (name == null || !PUBLIC_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        return Optional.of(new AvatarKey(PREFIX + name));
    }

    /** The part used in the URL path. */
    public String publicName() {
        return value.substring(PREFIX.length());
    }
}
