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

    /** True when this source owns the metadata of the entry, so its values replace the stored ones. */
    public boolean overwrites(CatalogEntry entry) {
        return name.equalsIgnoreCase(entry.provider());
    }
}
