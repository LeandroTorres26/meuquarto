package com.meuquarto.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.shared.domain.MediaCategory;
import org.junit.jupiter.api.Test;

/** BR-04: the canonical catalog owns the metadata; other sources only fill gaps. */
class MetadataSourceTest {

    private static final CatalogEntry FILM = new CatalogEntry(MediaCategory.MOVIE, "TMDB", "603", "Matrix", 1999, null, false);
    private static final CatalogEntry ANIME = new CatalogEntry(MediaCategory.ANIME, "ANILIST", "1", "Cowboy Bebop", 1998, null, false);

    @Test
    void br04TheCatalogOverwritesItsOwnEntries() {
        assertThat(MetadataSource.catalog(FILM).overwrites(FILM)).isTrue();
        assertThat(MetadataSource.catalog(ANIME).overwrites(ANIME)).isTrue();
    }

    @Test
    void br04AnActivitySourceThatIsNotTheCatalogOnlyFillsGaps() {
        assertThat(MetadataSource.activity(ProviderId.LETTERBOXD).overwrites(FILM)).isFalse();
    }

    @Test
    void br04AniListActivityIsCanonicalForAnimeAndMangaBecauseAniListIsTheirCatalog() {
        assertThat(MetadataSource.activity(ProviderId.ANILIST).overwrites(ANIME)).isTrue();
    }

    @Test
    void br04ASourceIsOnlyCanonicalForItsOwnCatalog() {
        assertThat(MetadataSource.activity(ProviderId.ANILIST).overwrites(FILM)).isFalse();
        assertThat(MetadataSource.activity(ProviderId.LETTERBOXD).overwrites(ANIME)).isFalse();
    }

    @Test
    void aSourceNeedsAName() {
        assertThatThrownBy(() -> new MetadataSource(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MetadataSource(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
