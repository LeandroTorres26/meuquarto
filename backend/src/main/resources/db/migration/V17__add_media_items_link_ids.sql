-- Ids and names that only a link to the work's own page needs (FR-10a, ADR 0023 decision 7).
-- mal_id: the MyAnimeList id of an AniList work, which AniList reports as idMal. Filled when the work is
-- saved and again by every later sync or catalog read; null means "no MyAnimeList page known".
-- music_artist / music_album: the names of an album that came from Last.fm, whose page is addressed by
-- names (last.fm/music/{artist}/{album}); the row key is only a hash of them. Null for every other work.
ALTER TABLE media_items ADD COLUMN mal_id integer;
ALTER TABLE media_items ADD COLUMN music_artist varchar(200);
ALTER TABLE media_items ADD COLUMN music_album varchar(200);
ALTER TABLE media_items ADD CONSTRAINT media_items_mal_id_positive CHECK (mal_id IS NULL OR mal_id > 0);
