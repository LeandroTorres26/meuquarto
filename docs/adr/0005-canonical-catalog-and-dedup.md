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
