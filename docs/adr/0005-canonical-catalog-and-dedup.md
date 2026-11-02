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
The canonical provider names stored in `media_items.canonical_provider` are `OPENLIBRARY` (books) and `DEEZER` (music), matching `MediaLinks` and the frontend labels. Books are Open Library **works** (id `OL45804W`), not editions, so every edition of a book is one item. Music is the Deezer **album** (numeric id); the title is stored as "Album - Artist" because an album name alone is ambiguous. The Deezer search has no release date, so a search entry has no year; a detail fetch fills it. Neither catalog marks anything adult (Deezer `explicit_lyrics` is not an adult flag). IGDB (games) is still missing.

## As built: Goodreads (issue #34)
Goodreads is not canonical for books and its public RSS has no Open Library id, so `GoodreadsProvider` produces **provisional** rows: `BOOK` / `GOODREADS` / Goodreads `book_id`. There is no fuzzy title matching and no extra request per book (mapping by ISBN would cost one Open Library call per book on every sync, the ISBN in the feed is often empty, and a lookup that sometimes fails would make the same book change identity between syncs). The same Goodreads book is one item for every user. A book added by hand from Open Library is a different item for now, which is the case this ADR already accepts ("stay as separate items"); merging by ISBN is the follow-up. `media_external_ids` is still not written by any sync. Because the two shelves describe one book with different fields, `MetadataSource.overwrites` treats `GOODREADS` as provisional: the first stored values stay and later ones only fill nulls.
