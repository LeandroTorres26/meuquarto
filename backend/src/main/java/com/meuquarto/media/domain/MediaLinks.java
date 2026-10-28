package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.MediaCategory;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where a work lives on its canonical catalog (FR-10a). The hosts are fixed here and only a plain id
 * is appended, so a stored value can never turn into a link to somewhere else. A catalog that cannot
 * be linked by id (IGDB and Deezer need a slug or a kind) simply has no link yet.
 */
public final class MediaLinks {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private MediaLinks() {}

    public static Optional<String> urlFor(MediaCategory category, String provider, String externalId) {
        if (category == null || provider == null || externalId == null || !SAFE_ID.matcher(externalId).matches()) {
            return Optional.empty();
        }
        String base = switch (provider) {
            case "TMDB" -> switch (category) {
                case MOVIE -> "https://www.themoviedb.org/movie/";
                case SERIES -> "https://www.themoviedb.org/tv/";
                default -> null;
            };
            case "ANILIST" -> switch (category) {
                case ANIME -> "https://anilist.co/anime/";
                case MANGA -> "https://anilist.co/manga/";
                default -> null;
            };
            case "OPENLIBRARY" -> category == MediaCategory.BOOK ? "https://openlibrary.org/works/" : null;
            default -> null;
        };
        return Optional.ofNullable(base).map(prefix -> prefix + externalId);
    }
}
