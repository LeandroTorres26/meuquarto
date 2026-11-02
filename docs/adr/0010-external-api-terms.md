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
| AniList | catalog + activity anime/manga (public GraphQL, no key, implemented in #31) | pending: terms not yet confirmed from an authoritative source | pending (credit AniList on the profile) | documented 90 requests/min, 30 while degraded (the live response header showed 30 on 2026-10-28); we use 1 call per search or link check and 2 per sync, 5 s timeout, no redirects | pending |
| Open Library | catalog books (public search and work APIs, no key, implemented in #22) | pending: terms not yet confirmed from an authoritative source | pending (credit Open Library on the profile; the footer already does) | asks clients to send an identifying User-Agent (we do) and to stay modest; one call per search, cached 10 minutes, 5 s timeout, no redirects | pending |
| IGDB (Twitch) | catalog games | – | – | – | pending |
| Deezer | catalog music, 30s previews (public album search, no key, implemented in #22; previews not used yet) | pending: public API terms not confirmed for commercial use | pending (credit Deezer on the profile; the footer already does) | documented quota of about 50 requests per 5 seconds per IP; one call per search, cached 10 minutes, 5 s timeout, no redirects | pending |
| Letterboxd RSS | activity films (public per-user RSS, implemented in #30) | pending: feed terms not yet confirmed from an authoritative source | pending (credit Letterboxd on the profile if required) | none published; fetched only on sync (15 min cooldown, backoff), 5 s timeout, no redirects | pending |
| Last.fm | activity music | – | – | – | pending |
| Steam Web API | activity games | – | – | – | pending |
| Goodreads RSS | activity books (public per-shelf RSS, `read` and `currently-reading`, implemented in #34) | pending: feed terms not yet confirmed from an authoritative source | pending (credit Goodreads on the profile; the footer already does) | none published; fetched only on sync (15 min cooldown, backoff), 2 requests per sync (one per shelf), 5 s timeout, no redirects | pending |

## Consequences
- Possible blocker for the SaaS challenge if TMDB refuses or charges; the fallback is a different film catalog, which would change ADR 0005.

## Alternatives considered
- Defer the review until after P2: no cost now, but risks blocking monetization late.

## As built: Goodreads RSS (issue #34)
Hosts: requests go to `www.goodreads.com` only (`/review/list_rss/{numeric id}?shelf=read|currently-reading&per_page=200`); covers are shown from `i.gr-assets.com`, `images.gr-assets.com` and `s.gr-assets.com`. No key, no login; the profile and its shelves must be public. Terms for commercial use of the feed are still unconfirmed (the "pending" cells above stay open until the owner checks them).
