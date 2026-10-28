# 0010. External API terms and licensing

- Status: Accepted, review pending
- Date: 2026-10-12

## Context
The product will be monetized (cosmetics). Several data sources restrict commercial use or require attribution (TMDB in particular). Letterboxd's API is closed to this kind of project; its public RSS is used instead.

## Decision
- Use TMDB with its required attribution ("This product uses the TMDB API but is not endorsed or certified by TMDB") and logo rules, and contact TMDB about a commercial license before real monetization.
- Credit every source in the profile footer (TMDB, AniList, Open Library, Goodreads, IGDB, Steam, Deezer, Last.fm).
- Sell only original cosmetics, never the data or franchise art ("sell the effect, not the work").
- No scraping: only official APIs and public RSS feeds; other platforms via CSV import (roadmap).
- Review the terms of each API in M0 and record the result in the table below.

## Review

| Source | Use | Commercial use | Attribution | Rate limit | Reviewed |
|---|---|---|---|---|---|
| TMDB | catalog films/series | license needed for commercial | required | – | pending |
| AniList | catalog + activity anime/manga | – | – | – | pending |
| Open Library | catalog books | – | – | – | pending |
| IGDB (Twitch) | catalog games | – | – | – | pending |
| Deezer | catalog music, 30s previews | – | – | – | pending |
| Letterboxd RSS | activity films (public per-user RSS, implemented in #30) | pending: feed terms not yet confirmed from an authoritative source | pending (credit Letterboxd on the profile if required) | none published; fetched only on sync (15 min cooldown, backoff), 5 s timeout, no redirects | pending |
| Last.fm | activity music | – | – | – | pending |
| Steam Web API | activity games | – | – | – | pending |
| Goodreads RSS | activity books | – | – | – | pending |

## Consequences
- Possible blocker for the SaaS challenge if TMDB refuses or charges; the fallback is a different film catalog, which would change ADR 0005.

## Alternatives considered
- Defer the review until after P2: no cost now, but risks blocking monetization late.
