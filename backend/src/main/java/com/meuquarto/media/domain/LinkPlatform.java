package com.meuquarto.media.domain;

import java.util.Optional;

/**
 * The platforms the item dialog can link to (FR-10a). The name is what the API sends as the link's
 * {@code provider}; for the platforms a user can link it is also the {@link ProviderId} name, which is how
 * "the platform the item came from" is found among an item's sources.
 */
public enum LinkPlatform {
    LETTERBOXD, TMDB, ANILIST, MYANIMELIST, OPENLIBRARY, LASTFM, DEEZER;

    /** The platform that an item source (a provider name such as {@code ANILIST}, or {@code MANUAL}) stands for. */
    public static Optional<LinkPlatform> ofSource(String source) {
        for (LinkPlatform platform : values()) {
            if (platform.name().equals(source)) {
                return Optional.of(platform);
            }
        }
        return Optional.empty();
    }
}
