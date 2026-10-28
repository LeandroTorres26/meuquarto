package com.meuquarto.profile.application;

import com.meuquarto.shared.domain.MediaCategory;
import java.time.Instant;
import java.util.List;

/**
 * What an anonymous visitor may see (FR-04), assembled from local data only. Immutable, so it can be
 * cached and shared. It deliberately holds no e-mail, user id, hidden or adult item.
 */
public record PublicProfile(String slug, String displayName, List<PublicItem> favorites, List<Section> categories) {

    /**
     * One category block of the home. {@code total} is the visible collection size ("Ver todos", FR-10b).
     * {@code mostRecent} is true when the owner has no highlight here and the items are the latest ones (FR-09a).
     */
    public record Section(MediaCategory category, long total, boolean mostRecent, List<PublicItem> items) {}

    /** A work as the public sees it. {@code ref} is the collection item id: it only means something inside one profile. */
    public record PublicItem(long ref, MediaCategory category, String title, Integer year, String imageUrl,
            String note) {}

    /** The details dialog (S17): the item, why it is in the collection and where to find it. */
    public record Detail(PublicItem item, List<Origin> origin, List<Link> links) {}

    /** {@code source} is {@code MANUAL} or a provider name such as {@code LETTERBOXD}. */
    public record Origin(String source, Instant firstSeenAt) {}

    /** {@code provider} names the platform ("TMDB"); the url comes from a fixed allowlist of hosts. */
    public record Link(String provider, String url) {}

    /** One page of S18; {@code highlighted} is the badge. */
    public record Listing(List<Entry> items, int page, int size, long total) {}

    public record Entry(PublicItem item, boolean highlighted) {}
}
