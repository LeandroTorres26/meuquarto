package com.meuquarto.media.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.shared.domain.MediaCategory;
import org.junit.jupiter.api.Test;

class MediaLinksTest {

    @Test
    void knownCatalogsGetAFixedHostAndTheId() {
        assertThat(MediaLinks.urlFor(MediaCategory.MOVIE, "TMDB", "603")).contains("https://www.themoviedb.org/movie/603");
        assertThat(MediaLinks.urlFor(MediaCategory.SERIES, "TMDB", "1399")).contains("https://www.themoviedb.org/tv/1399");
        assertThat(MediaLinks.urlFor(MediaCategory.ANIME, "ANILIST", "1")).contains("https://anilist.co/anime/1");
        assertThat(MediaLinks.urlFor(MediaCategory.MANGA, "ANILIST", "30002")).contains("https://anilist.co/manga/30002");
        assertThat(MediaLinks.urlFor(MediaCategory.BOOK, "OPENLIBRARY", "OL45804W")).contains("https://openlibrary.org/works/OL45804W");
        assertThat(MediaLinks.urlFor(MediaCategory.MUSIC, "DEEZER", "302127")).contains("https://www.deezer.com/album/302127");
        assertThat(MediaLinks.urlFor(MediaCategory.BOOK, "DEEZER", "302127")).isEmpty();
    }

    @Test
    void anUnknownProviderOrCategoryHasNoLink() {
        assertThat(MediaLinks.urlFor(MediaCategory.GAME, "IGDB", "1942")).isEmpty();
        assertThat(MediaLinks.urlFor(MediaCategory.BOOK, "TMDB", "1")).isEmpty();
        assertThat(MediaLinks.urlFor(MediaCategory.MOVIE, "https://evil.example", "1")).isEmpty();
    }

    @Test
    void anIdThatIsNotAPlainTokenNeverBecomesPartOfAnUrl() {
        assertThat(MediaLinks.urlFor(MediaCategory.MOVIE, "TMDB", "../../evil")).isEmpty();
        assertThat(MediaLinks.urlFor(MediaCategory.MOVIE, "TMDB", "1?x=y")).isEmpty();
        assertThat(MediaLinks.urlFor(MediaCategory.MOVIE, "TMDB", "")).isEmpty();
        assertThat(MediaLinks.urlFor(MediaCategory.MOVIE, "TMDB", null)).isEmpty();
    }
}
