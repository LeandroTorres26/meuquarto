# 0005. Canonical catalog per category and dedup key

- Status: Accepted
- Date: 2026-10-12

## Context
The same work can arrive from several sources: a film from the Letterboxd RSS and from a manual TMDB search, a game from Steam and from IGDB. Showing it twice breaks the collection and the highlight limits (BR-04, BR-07).

## Decision
Each category has one canonical catalog: films and series TMDB, anime and manga AniList, books Open Library, games IGDB, music Deezer. A `media_item` is identified by (category, canonical provider, external id). `media_external_ids` maps ids from other sources to it (Letterboxd RSS carries the TMDB id; Steam app ids map through IGDB). When an id cannot be mapped, a new item is created and can be merged later.

## Consequences
- Deterministic dedup without fuzzy title matching.
- Mapping tables must be maintained; some Steam games or Goodreads books may not map and stay as separate items.
- Changing the canonical catalog of a category later (e.g. Hardcover for books) needs a migration of ids.

## Alternatives considered
- Dedup by title + year: fails with remakes, translations and common titles.
- No dedup: duplicates on the profile and broken business rules.

## As built: who owns the metadata (issue #93)
The canonical catalog owns title, year and image of a row. `MediaSnapshots.snapshot(entry, MetadataSource)` takes who is writing. A source is canonical for an entry when its name is the entry's canonical catalog name (`MetadataSource.overwrites`), so a manual add through TMDB or AniList, and also an AniList activity sync (AniList is the catalog and the feed for anime and manga), replace the stored title, year, image, adult flag and `fetched_at`. A non-canonical source (Letterboxd for films) creates the row when it does not exist, and on an existing row only fills a null year or image; it never changes the title. `media_items.metadata_source` records who wrote last (V10), so a Letterboxd-created row is visibly provisional until a catalog add refreshes it. It is one `INSERT ... ON CONFLICT DO UPDATE` with `CASE`/`COALESCE`, so it stays atomic under concurrency.

## As built: books and music (issue #22)
The canonical provider names stored in `media_items.canonical_provider` are `OPENLIBRARY` (books) and `DEEZER` (music), matching `MediaLinks` and the frontend labels. Books are Open Library **works** (id `OL45804W`), not editions, so every edition of a book is one item. Music is the Deezer **album** (numeric id); the title is stored as "Album - Artist" because an album name alone is ambiguous. The Deezer search has no release date, so a search entry has no year; a detail fetch fills it. Neither catalog marks anything adult (Deezer `explicit_lyrics` is not an adult flag). IGDB (games) is described in the next section.

## As built: Goodreads (issue #34)
Goodreads is not canonical for books and its public RSS has no Open Library id, so `GoodreadsProvider` produces **provisional** rows: `BOOK` / `GOODREADS` / Goodreads `book_id`. There is no fuzzy title matching and no extra request per book (mapping by ISBN would cost one Open Library call per book on every sync, the ISBN in the feed is often empty, and a lookup that sometimes fails would make the same book change identity between syncs). The same Goodreads book is one item for every user. A book added by hand from Open Library is a different item for now, which is the case this ADR already accepts ("stay as separate items"); merging by ISBN is the follow-up. `media_external_ids` is still not written by any sync. Because the two shelves describe one book with different fields, `MetadataSource.overwrites` treats `GOODREADS` as provisional: the first stored values stay and later ones only fill nulls.

## As built: Last.fm (issue #32)
Deezer is the canonical catalog of music, and Last.fm is not: `LastFmProvider` produces **provisional** rows `MUSIC` / `LASTFM` / key, where the unit is the album and the key is the first 40 hex characters of a SHA-256 over the artist and album names after a purely mechanical normalization (Unicode NFKC, lower-case, collapsed whitespace). Mapping to a Deezer album would need a fuzzy search per album (a recent-tracks answer has no Deezer id, and a MusicBrainz id is present on some scrobbles of an album and absent on others, so it cannot be the identity); like Goodreads, it is a follow-up. Limits to know: the same album under a different spelling ("OK Computer" and "OK Computer (Deluxe)"), or added by hand from Deezer, is a different item for now. `MetadataSource.overwrites` treats `LASTFM` as provisional (first values stay, later ones only fill gaps). The title is stored as "Album - Artist", as for Deezer.

## As built: games (issue #22)
The canonical provider name for games is `IGDB`, the id is the IGDB game id (numeric). The work is the main game: the query asks for `version_parent = null & parent_game = null`, so editions, DLC and expansions are not separate items. IGDB belongs to Twitch, so `IgdbCatalog` first buys an app access token with the client-credentials flow (`TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET`), keeps it in memory until a minute before `expires_in`, and renews it once on a 401. Without both variables the catalog is unavailable and game search answers `available: false`. The cover is built by us from `cover.image_id` (`t_cover_big`), the year comes from `first_release_date` (UTC), and theme 42 (erotic) sets `adult` (dropped from search, BR-08). The Steam adapter (#32) will map an appid to an IGDB id later; until then a game from Steam is a different item from the IGDB one, the case this ADR already accepts.
