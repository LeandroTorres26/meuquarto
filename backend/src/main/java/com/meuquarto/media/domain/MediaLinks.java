package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.MediaCategory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Where a work lives on each platform that has its own page for it (FR-10a, ADR 0023 decision 7). Every
 * link opens the work itself, built from an id we stored, never a search; a platform without an id for the
 * work has no link. The hosts are fixed here and only a validated id (or a percent-encoded name) is
 * appended, so a stored value can never turn into a link to somewhere else.
 * <p>
 * Order: the platform the item came from first (its sources, in the order given), then the others in the
 * order of the category: film Letterboxd then TMDB; series TMDB; anime and manga AniList then MyAnimeList;
 * book Open Library; music Last.fm then Deezer. A game has no link yet (Steam app ids are not stored, and
 * IGDB cannot be linked by id).
 * <p>
 * Where each id comes from: TMDB, AniList, Open Library and Deezer are the work's canonical key (BR-04);
 * Letterboxd reuses the TMDB id ({@code letterboxd.com/tmdb/{id}} redirects to the film); MyAnimeList uses
 * the {@code idMal} AniList reports ({@link LinkHints#malId()}); Last.fm is addressed by artist and album
 * names and only for an album that came from Last.fm.
 */
public final class MediaLinks {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private MediaLinks() {}

    /**
     * The links of one work, in display order.
     *
     * @param sources where the item came from (provider names such as {@code LETTERBOXD}; {@code MANUAL} and
     *                anything that is not a link platform are ignored), the first one leading
     */
    public static List<ItemLink> forWork(MediaKey key, LinkHints hints, Collection<String> sources) {
        if (key == null) {
            return List.of();
        }
        LinkHints known = hints == null ? LinkHints.NONE : hints;
        Collection<String> origin = sources == null ? List.of() : sources;

        Set<LinkPlatform> ordered = new LinkedHashSet<>();
        List<LinkPlatform> defaults = platformsOf(key.category());
        for (String source : origin) {
            LinkPlatform.ofSource(source).filter(defaults::contains).ifPresent(ordered::add);
        }
        ordered.addAll(defaults);

        List<ItemLink> links = new ArrayList<>();
        for (LinkPlatform platform : ordered) {
            urlOf(platform, key, known, origin).ifPresent(url -> links.add(new ItemLink(platform, url)));
        }
        return List.copyOf(links);
    }

    /** The platforms that can have a page for a category, in the order they are shown when nothing leads. */
    private static List<LinkPlatform> platformsOf(MediaCategory category) {
        return switch (category) {
            case MOVIE -> List.of(LinkPlatform.LETTERBOXD, LinkPlatform.TMDB);
            case SERIES -> List.of(LinkPlatform.TMDB);
            case ANIME, MANGA -> List.of(LinkPlatform.ANILIST, LinkPlatform.MYANIMELIST);
            case BOOK -> List.of(LinkPlatform.OPENLIBRARY);
            case MUSIC -> List.of(LinkPlatform.LASTFM, LinkPlatform.DEEZER);
            case GAME -> List.of();
        };
    }

    private static Optional<String> urlOf(LinkPlatform platform, MediaKey key, LinkHints hints,
            Collection<String> sources) {
        MediaCategory category = key.category();
        return switch (platform) {
            case LETTERBOXD -> canonical(key, "TMDB").map(id -> "https://letterboxd.com/tmdb/" + id + "/");
            case TMDB -> canonical(key, "TMDB")
                    .map(id -> "https://www.themoviedb.org/" + (category == MediaCategory.SERIES ? "tv/" : "movie/") + id);
            case ANILIST -> canonical(key, "ANILIST")
                    .map(id -> "https://anilist.co/" + (category == MediaCategory.MANGA ? "manga/" : "anime/") + id);
            case MYANIMELIST -> Optional.ofNullable(hints.malId())
                    .map(id -> "https://myanimelist.net/" + (category == MediaCategory.MANGA ? "manga/" : "anime/") + id);
            case OPENLIBRARY -> canonical(key, "OPENLIBRARY").map(id -> "https://openlibrary.org/works/" + id);
            case DEEZER -> canonical(key, "DEEZER").map(id -> "https://www.deezer.com/album/" + id);
            case LASTFM -> sources.contains(ProviderId.LASTFM.name()) ? lastFmAlbum(hints) : Optional.empty();
        };
    }

    /** The work's id when its canonical catalog is the given one and the id is a plain token. */
    private static Optional<String> canonical(MediaKey key, String provider) {
        boolean usable = key.provider().equals(provider) && SAFE_ID.matcher(key.externalId()).matches();
        return usable ? Optional.of(key.externalId()) : Optional.empty();
    }

    /** {@code last.fm/music/{artist}/{album}}: names percent-encoded with spaces as "+", never "." or "..". */
    private static Optional<String> lastFmAlbum(LinkHints hints) {
        if (hints.artist() == null || hints.album() == null) {
            return Optional.empty();
        }
        String artist = URLEncoder.encode(hints.artist(), StandardCharsets.UTF_8);
        String album = URLEncoder.encode(hints.album(), StandardCharsets.UTF_8);
        if (isDotSegment(artist) || isDotSegment(album)) {
            return Optional.empty();
        }
        return Optional.of("https://www.last.fm/music/" + artist + "/" + album);
    }

    private static boolean isDotSegment(String encoded) {
        return encoded.equals(".") || encoded.equals("..");
    }
}
