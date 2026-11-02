package com.meuquarto.media.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.shared.domain.MediaCategory;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** FR-10a: every link is the work's own page on a fixed host, built from a stored id, in a fixed order. */
class MediaLinksTest {

    private static MediaKey key(MediaCategory category, String provider, String id) {
        return new MediaKey(category, provider, id);
    }

    private static List<ItemLink> links(MediaKey key, LinkHints hints, String... sources) {
        return MediaLinks.forWork(key, hints, List.of(sources));
    }

    private static ItemLink link(LinkPlatform platform, String url) {
        return new ItemLink(platform, url);
    }

    // --- one test per platform ---

    @Test
    void aFilmLinksLetterboxdThroughItsTmdbIdAndThenTmdb() {
        assertThat(links(key(MediaCategory.MOVIE, "TMDB", "603"), LinkHints.NONE, "MANUAL")).containsExactly(
                link(LinkPlatform.LETTERBOXD, "https://letterboxd.com/tmdb/603/"),
                link(LinkPlatform.TMDB, "https://www.themoviedb.org/movie/603"));
    }

    @Test
    void aSeriesLinksTmdbOnlyAsATvPage() {
        assertThat(links(key(MediaCategory.SERIES, "TMDB", "1399"), LinkHints.NONE))
                .containsExactly(link(LinkPlatform.TMDB, "https://www.themoviedb.org/tv/1399"));
    }

    @Test
    void anAnimeLinksAniListAndThenMyAnimeListThroughIdMal() {
        assertThat(links(key(MediaCategory.ANIME, "ANILIST", "154587"), LinkHints.ofMalId(52991))).containsExactly(
                link(LinkPlatform.ANILIST, "https://anilist.co/anime/154587"),
                link(LinkPlatform.MYANIMELIST, "https://myanimelist.net/anime/52991"));
    }

    @Test
    void aMangaUsesThePagesOfManga() {
        assertThat(links(key(MediaCategory.MANGA, "ANILIST", "30656"), LinkHints.ofMalId(656))).containsExactly(
                link(LinkPlatform.ANILIST, "https://anilist.co/manga/30656"),
                link(LinkPlatform.MYANIMELIST, "https://myanimelist.net/manga/656"));
    }

    @Test
    void withoutIdMalThereIsNoMyAnimeListLink() {
        assertThat(links(key(MediaCategory.ANIME, "ANILIST", "1"), LinkHints.NONE))
                .containsExactly(link(LinkPlatform.ANILIST, "https://anilist.co/anime/1"));
        assertThat(LinkHints.ofMalId(0).malId()).isNull();
        assertThat(LinkHints.ofMalId(-4).malId()).isNull();
        assertThat(LinkHints.ofMalId(Integer.MAX_VALUE + 1L).malId()).isNull();
    }

    @Test
    void aBookLinksOpenLibraryAndAGoodreadsBookHasNoLink() {
        assertThat(links(key(MediaCategory.BOOK, "OPENLIBRARY", "OL45804W"), LinkHints.NONE))
                .containsExactly(link(LinkPlatform.OPENLIBRARY, "https://openlibrary.org/works/OL45804W"));
        assertThat(links(key(MediaCategory.BOOK, "GOODREADS", "1234"), LinkHints.NONE, "GOODREADS")).isEmpty();
    }

    @Test
    void aGameHasNoLinkYet() {
        assertThat(links(key(MediaCategory.GAME, "IGDB", "1942"), LinkHints.NONE, "STEAM")).isEmpty();
    }

    @Test
    void anAlbumFromLastFmLinksItsPageByNamesWithSpacesAsPlus() {
        LinkHints names = LinkHints.ofAlbum("Milton Nascimento", "Clube da Esquina");
        assertThat(links(key(MediaCategory.MUSIC, "LASTFM", "abc"), names, "LASTFM")).containsExactly(
                link(LinkPlatform.LASTFM, "https://www.last.fm/music/Milton+Nascimento/Clube+da+Esquina"));
    }

    @Test
    void lastFmNamesArePercentEncodedIncludingSlashesAmpersandsAndAccents() {
        LinkHints names = LinkHints.ofAlbum("AC/DC", "Elis & Tom: Ação?");
        assertThat(links(key(MediaCategory.MUSIC, "LASTFM", "abc"), names, "LASTFM")).containsExactly(
                link(LinkPlatform.LASTFM, "https://www.last.fm/music/AC%2FDC/Elis+%26+Tom%3A+A%C3%A7%C3%A3o%3F"));
    }

    @Test
    void lastFmNeedsBothNamesAndAnAlbumThatCameFromLastFm() {
        MediaKey album = key(MediaCategory.MUSIC, "LASTFM", "abc");
        assertThat(links(album, LinkHints.ofAlbum("Artist", null), "LASTFM")).isEmpty();
        assertThat(links(album, LinkHints.ofAlbum(null, "Album"), "LASTFM")).isEmpty();
        assertThat(links(album, LinkHints.NONE, "LASTFM")).isEmpty();
        // names are not enough: the album did not come from Last.fm
        assertThat(links(album, LinkHints.ofAlbum("Artist", "Album"), "MANUAL")).isEmpty();
        assertThat(links(album, LinkHints.ofAlbum("Artist", "Album"))).isEmpty();
    }

    @Test
    void anAlbumPickedFromDeezerLinksDeezer() {
        assertThat(links(key(MediaCategory.MUSIC, "DEEZER", "302127"), LinkHints.NONE, "MANUAL"))
                .containsExactly(link(LinkPlatform.DEEZER, "https://www.deezer.com/album/302127"));
    }

    @Test
    void anAlbumWithBothAPageOnLastFmAndOnDeezerShowsLastFmFirst() {
        LinkHints names = LinkHints.ofAlbum("Novos Baianos", "Acabou Chorare");
        assertThat(links(key(MediaCategory.MUSIC, "DEEZER", "1"), names, "LASTFM")).containsExactly(
                link(LinkPlatform.LASTFM, "https://www.last.fm/music/Novos+Baianos/Acabou+Chorare"),
                link(LinkPlatform.DEEZER, "https://www.deezer.com/album/1"));
    }

    // --- order: the platform the item came from first ---

    @Test
    void theActivityPlatformComesFirstAndTheRestKeepTheCategoryOrder() {
        LinkHints mal = LinkHints.ofMalId(1);
        MediaKey anime = key(MediaCategory.ANIME, "ANILIST", "1");
        assertThat(links(anime, mal, "MYANIMELIST")).extracting(ItemLink::platform)
                .containsExactly(LinkPlatform.MYANIMELIST, LinkPlatform.ANILIST);
        assertThat(links(anime, mal, "ANILIST")).extracting(ItemLink::platform)
                .containsExactly(LinkPlatform.ANILIST, LinkPlatform.MYANIMELIST);
        assertThat(links(anime, mal, "MANUAL")).extracting(ItemLink::platform)
                .containsExactly(LinkPlatform.ANILIST, LinkPlatform.MYANIMELIST);
        assertThat(links(anime, mal, "MYANIMELIST", "ANILIST")).extracting(ItemLink::platform)
                .containsExactly(LinkPlatform.MYANIMELIST, LinkPlatform.ANILIST);
    }

    @Test
    void aFilmFromLetterboxdStartsWithLetterboxdAndASourceThatDoesNotApplyIsIgnored() {
        MediaKey film = key(MediaCategory.MOVIE, "TMDB", "976893");
        assertThat(links(film, LinkHints.NONE, "LETTERBOXD")).extracting(ItemLink::platform)
                .containsExactly(LinkPlatform.LETTERBOXD, LinkPlatform.TMDB);
        // Goodreads, Steam and anything unknown do not lead or add a link to a film
        assertThat(links(film, LinkHints.NONE, "GOODREADS", "STEAM", "nonsense", "")).extracting(ItemLink::platform)
                .containsExactly(LinkPlatform.LETTERBOXD, LinkPlatform.TMDB);
    }

    @Test
    void repeatedSourcesNeverRepeatALink() {
        assertThat(links(key(MediaCategory.MOVIE, "TMDB", "1"), LinkHints.NONE, "LETTERBOXD", "LETTERBOXD", "TMDB"))
                .extracting(ItemLink::platform).containsExactly(LinkPlatform.LETTERBOXD, LinkPlatform.TMDB);
    }

    // --- a link exists only for a platform whose id we hold ---

    @Test
    void aWorkKeyedByAnotherCatalogGetsNoLinkOnAPlatformThatIsNotItsCatalog() {
        assertThat(links(key(MediaCategory.MOVIE, "IGDB", "603"), LinkHints.NONE)).isEmpty();
        assertThat(links(key(MediaCategory.MOVIE, "https://evil.example", "1"), LinkHints.NONE)).isEmpty();
        assertThat(links(key(MediaCategory.BOOK, "TMDB", "1"), LinkHints.NONE)).isEmpty();
        assertThat(links(key(MediaCategory.MUSIC, "ANILIST", "1"), LinkHints.NONE)).isEmpty();
    }

    @Test
    void nullInputsGiveNoLinks() {
        assertThat(MediaLinks.forWork(null, LinkHints.NONE, List.of())).isEmpty();
        assertThat(MediaLinks.forWork(key(MediaCategory.MOVIE, "TMDB", "1"), null, null))
                .extracting(ItemLink::platform).containsExactly(LinkPlatform.LETTERBOXD, LinkPlatform.TMDB);
    }

    // --- hostile ids and names ---

    static Stream<String> hostileIds() {
        return Stream.of("../../evil", "..", "1/../2", "1?x=y", "1#frag", "me@evil.example", "1\r\nHost: evil", "1\n",
                " 1", "1 ", "ação", "１２３", "1;2", "1%2F2", "a".repeat(65));
    }

    @ParameterizedTest
    @MethodSource("hostileIds")
    void aHostileCanonicalIdNeverBecomesALink(String id) {
        assertThat(links(key(MediaCategory.MOVIE, "TMDB", id), LinkHints.NONE, "LETTERBOXD")).isEmpty();
        assertThat(links(key(MediaCategory.SERIES, "TMDB", id), LinkHints.NONE)).isEmpty();
        assertThat(links(key(MediaCategory.ANIME, "ANILIST", id), LinkHints.NONE)).isEmpty();
        assertThat(links(key(MediaCategory.MANGA, "ANILIST", id), LinkHints.NONE)).isEmpty();
        assertThat(links(key(MediaCategory.BOOK, "OPENLIBRARY", id), LinkHints.NONE)).isEmpty();
        assertThat(links(key(MediaCategory.MUSIC, "DEEZER", id), LinkHints.NONE)).isEmpty();
    }

    @Test
    void anIdOfTheLongestAllowedLengthIsAccepted() {
        String longest = "1".repeat(64);
        assertThat(links(key(MediaCategory.MOVIE, "TMDB", longest), LinkHints.NONE).get(1).url())
                .isEqualTo("https://www.themoviedb.org/movie/" + longest);
    }

    static Stream<String> hostileNames() {
        return Stream.of("../x", "x/../y", "\r\nHost: evil.example", "a?b=c#d", "me@evil.example", "<script>", "x\u0000y",
                "日本語", "\"quoted\"", "%2e%2e", "x".repeat(LinkHints.MAX_NAME_LENGTH));
    }

    @ParameterizedTest
    @MethodSource("hostileNames")
    void hostileLastFmNamesStayInsideTheFixedPath(String name) {
        MediaKey album = key(MediaCategory.MUSIC, "LASTFM", "abc");
        for (ItemLink link : links(album, LinkHints.ofAlbum(name, name), "LASTFM")) {
            String rest = link.url().substring("https://www.last.fm/music/".length());
            assertThat(link.url()).startsWith("https://www.last.fm/music/");
            // exactly two path segments, and nothing outside the unreserved set or a percent escape
            assertThat(rest.split("/", -1)).hasSize(2).doesNotContain(".", "..");
            assertThat(rest).matches("[A-Za-z0-9.*_+%/-]+");
        }
    }

    @Test
    void lastFmNamesMadeOfDotsOrTooLongOrBlankGiveNoLink() {
        MediaKey album = key(MediaCategory.MUSIC, "LASTFM", "abc");
        assertThat(links(album, LinkHints.ofAlbum("..", "Album"), "LASTFM")).isEmpty();
        assertThat(links(album, LinkHints.ofAlbum("Artist", "."), "LASTFM")).isEmpty();
        assertThat(links(album, LinkHints.ofAlbum("x".repeat(LinkHints.MAX_NAME_LENGTH + 1), "Album"), "LASTFM")).isEmpty();
        assertThat(links(album, LinkHints.ofAlbum("   ", "Album"), "LASTFM")).isEmpty();
    }

    @Test
    void anHostileCrlfNameIsEncodedNotCopied() {
        MediaKey album = key(MediaCategory.MUSIC, "LASTFM", "abc");
        assertThat(links(album, LinkHints.ofAlbum("a\r\nSet-Cookie: x", "b"), "LASTFM").get(0).url())
                .isEqualTo("https://www.last.fm/music/a%0D%0ASet-Cookie%3A+x/b");
    }
}
