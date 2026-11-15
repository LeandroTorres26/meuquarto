-- An album's title is the album name only; the artist is shown as its own line (issue #154).
-- Last.fm albums saved after V17 carry the album name in music_album, so their title can be restored.
-- Deezer albums saved before this change have no artist stored: they keep "Album - Artist" until the work
-- is read from the catalog again, which rewrites title and fills music_artist.
UPDATE media_items
SET title = music_album
WHERE category = 'MUSIC' AND music_album IS NOT NULL;
