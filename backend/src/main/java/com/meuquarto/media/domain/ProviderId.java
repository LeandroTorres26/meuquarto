package com.meuquarto.media.domain;

import java.util.Arrays;
import java.util.List;

/**
 * The platforms a user can link by username (ADR 0004, ADR 0023). The name is stored in
 * {@code linked_accounts.provider} and in {@code collection_item_sources.source}, so it never changes.
 * <p>
 * The declaration order is the fixed order in which the public profile lists the links (FR-12a).
 * A <b>link-only</b> platform (Backloggd, Trakt, StoryGraph) is only a link on the profile: its lists are
 * never read, so it has no {@code ActivityProvider} and the sync never touches it (ADR 0023, decision 5).
 */
public enum ProviderId {
    LETTERBOXD(false), ANILIST(false), LASTFM(false), STEAM(false), GOODREADS(false),
    BACKLOGGD(true), TRAKT(true), STORYGRAPH(true);

    private final boolean linkOnly;

    ProviderId(boolean linkOnly) {
        this.linkOnly = linkOnly;
    }

    /** True when the platform is only a link on the profile: no existence check, no sync. */
    public boolean linkOnly() {
        return linkOnly;
    }

    /** The link-only platforms, for queries that must leave them out of the sync. */
    public static List<ProviderId> linkOnlyPlatforms() {
        return Arrays.stream(values()).filter(ProviderId::linkOnly).toList();
    }
}
