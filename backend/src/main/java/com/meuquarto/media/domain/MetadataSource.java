package com.meuquarto.media.domain;

/**
 * Who is writing the metadata of a work (BR-04, ADR 0005). The canonical catalog of a category owns
 * title, year and image; any other source (an activity feed such as Letterboxd) may only fill gaps.
 * <p>
 * A source is canonical for an entry when its name is the entry's canonical catalog name. That is why
 * AniList is both: its catalog and its activity feed are named {@code ANILIST}, so both are canonical
 * for anime and manga, while {@code LETTERBOXD} is never canonical for films (TMDB is).
 * Plain Java on purpose: no framework, so the rule is unit-tested without Spring.
 */
public record MetadataSource(String name) {

    public MetadataSource {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A metadata source needs a name");
        }
    }

    /** A catalog writing its own entry (manual add through search or fetch): always canonical. */
    public static MetadataSource catalog(CatalogEntry entry) {
        return new MetadataSource(entry.provider());
    }

    /** A platform the user linked (sync). Canonical only for the works whose catalog has its name. */
    public static MetadataSource activity(ProviderId platform) {
        return new MetadataSource(platform.name());
    }

    /**
     * Rows keyed by a platform that only stands in for the real catalog (a Goodreads book, until it is
     * mapped to Open Library). The platform "owns" them by name, but its two shelves describe the same book
     * with different fields (a cover on one, none on the other), so nobody overwrites: the first values
     * stay and later ones only fill the gaps.
     */
    private static final java.util.Set<String> PROVISIONAL = java.util.Set.of("GOODREADS");

    /** True when this source owns the metadata of the entry, so its values replace the stored ones. */
    public boolean overwrites(CatalogEntry entry) {
        return name.equalsIgnoreCase(entry.provider()) && !PROVISIONAL.contains(entry.provider().toUpperCase(java.util.Locale.ROOT));
    }
}
