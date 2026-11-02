package com.meuquarto.media.domain;

/**
 * What a catalog or an activity feed knows about a work beyond its canonical key, only so the item dialog
 * can link to the work's own page on other platforms (FR-10a): the MyAnimeList id of an AniList work
 * ({@code idMal}) and the artist and album names of a Last.fm album. Anything missing, non-positive,
 * blank or too long is simply dropped, so a hint is either usable or absent.
 */
public record LinkHints(Integer malId, String artist, String album) {

    public static final int MAX_NAME_LENGTH = 200;

    /** No hints: the work is linked only through its canonical key. */
    public static final LinkHints NONE = new LinkHints(null, null, null);

    public LinkHints {
        malId = malId != null && malId > 0 ? malId : null;
        artist = clean(artist);
        album = clean(album);
    }

    public static LinkHints ofMalId(long malId) {
        return new LinkHints(malId > 0 && malId <= Integer.MAX_VALUE ? (int) malId : null, null, null);
    }

    public static LinkHints ofAlbum(String artist, String album) {
        return new LinkHints(null, artist, album);
    }

    private static String clean(String name) {
        if (name == null) {
            return null;
        }
        String stripped = name.strip();
        return stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH ? null : stripped;
    }
}
