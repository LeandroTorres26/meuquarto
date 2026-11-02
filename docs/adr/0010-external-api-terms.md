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
| IGDB (Twitch) | catalog games (Twitch app, client-credentials token; implemented in #22) | pending: IGDB terms (Twitch developer agreement) not yet confirmed for commercial use | pending (credit IGDB on the profile; the footer already does) | documented 4 requests per second; `IgdbCatalog` spaces calls 250 ms apart, one call per search, cached 10 minutes, 5 s timeout, no redirects | pending |
| Deezer | catalog music, 30s previews (public album search, no key, implemented in #22; previews not used yet) | pending: public API terms not confirmed for commercial use | pending (credit Deezer on the profile; the footer already does) | documented quota of about 50 requests per 5 seconds per IP; one call per search, cached 10 minutes, 5 s timeout, no redirects | pending |
| Letterboxd RSS | activity films (public per-user RSS, implemented in #30) | pending: feed terms not yet confirmed from an authoritative source | pending (credit Letterboxd on the profile if required) | none published; fetched only on sync (15 min cooldown, backoff), 5 s timeout, no redirects | pending |
| Last.fm | activity music (`user.getrecenttracks`, API key, implemented in #32) | pending: API terms not yet confirmed from an authoritative source for commercial use | pending (credit Last.fm on the profile; the footer already does) | documented about 5 requests per second per key; we space requests 250 ms apart, use 1 call per link check or sync, 5 s timeout, no redirects | pending |
| MyAnimeList | activity anime/manga (API v2, public lists with the project's client ID; implemented in #126, ADR 0023) | the API agreement counts an app with paid features as commercial: written authorization from MyAnimeList before real charges | credit MyAnimeList as the source; its marks only for that | no number published ("no unreasonable burden"); fetched only on sync | 2026-11-02 (agreement read; authorization not requested yet) |
| Steam Web API | activity games | – | – | – | pending |
| Goodreads RSS | activity books (public per-shelf RSS, `read` and `currently-reading`, implemented in #34) | pending: feed terms not yet confirmed from an authoritative source | pending (credit Goodreads on the profile; the footer already does) | none published; fetched only on sync (15 min cooldown, backoff), 2 requests per sync (one per shelf), 5 s timeout, no redirects | pending |

## Consequences
- Possible blocker for the SaaS challenge if TMDB refuses or charges; the fallback is a different film catalog, which would change ADR 0005.

## Alternatives considered
- Defer the review until after P2: no cost now, but risks blocking monetization late.

## As built: Goodreads RSS (issue #34)
Hosts: requests go to `www.goodreads.com` only (`/review/list_rss/{numeric id}?shelf=read|currently-reading&per_page=200`); covers are shown from `i.gr-assets.com`, `images.gr-assets.com` and `s.gr-assets.com`. No key, no login; the profile and its shelves must be public. Terms for commercial use of the feed are still unconfirmed (the "pending" cells above stay open until the owner checks them).

## As built: Last.fm (issue #32)
Host: requests go to `ws.audioscrobbler.com/2.0/` only, `GET` with `method=user.getrecenttracks`, the user name (validated and URL-encoded), `format=json` and `limit=200` (`limit=1` to check that the account exists). The API key (`LASTFM_API_KEY`) is sent in the query string because Last.fm offers no other way, so the request URL is never logged nor put in an exception message. Without a key the provider reports itself unavailable. Covers are shown from `lastfm.freetls.fastly.net` and `lastfm-img2.akamaized.net`; the grey star placeholder is dropped. The user's recent listening must be public (Last.fm error 17 means private). Terms for commercial use are still unconfirmed (the "pending" cells above stay open until the owner checks them).

## As built: MyAnimeList (issue #126)
Host: requests go to `api.myanimelist.net/v2/` only, `GET users/{name}/animelist` and `users/{name}/mangalist` with `fields=list_status`, `sort=list_updated_at`, `limit=1000` and `offset` (`limit=1` to check that the account exists). The user name (2 to 16 characters of letters, digits, `_` and `-`) is validated and URL-encoded. The project's client ID (`MAL_CLIENT_ID`) travels in the `X-MAL-CLIENT-ID` header, never in the URL, and is never logged or put in an exception message; without a usable one (letters and digits, 16 to 64) the provider reports itself unavailable, nothing is sent and the panel shows "Em breve". 403 and 404 both mean a private list or a missing user (MyAnimeList does not tell them apart) and become "this profile does not exist or is private"; 401 (a client ID MyAnimeList refuses), 429 and 5xx are "unavailable". The `paging.next` URL of an answer is never requested: the next page is built from the offset, at most 10 pages of 1000 entries per list. No redirects, 3 s connect and 5 s request timeouts, body capped at 4 MB, requests spaced 500 ms apart (MyAnimeList publishes no number), at most 500 entries (the most recently updated) per sync.

Only the statuses of BR-10 are kept in memory (anime `completed`, `watching`, `on_hold`; manga `completed`, `reading`, `on_hold`; an entry flagged `is_rewatching` / `is_rereading` counts as finished); `plan_to_*` and `dropped` are dropped before anything else happens. Each kept MyAnimeList id is resolved to the AniList work through `Page { media(idMal_in: [...], type: ANIME|MANGA) }`, 50 ids per request, and the AniList work is what is stored (same key as the AniList catalog, so a profile linked on both platforms gets each work once). Titles, pictures, scores and dates of MyAnimeList are read and dropped; an entry AniList has no match for is skipped and counted in one log line (the count only). Item links to the MyAnimeList page of a work (FR-10a) would use AniList's `idMal`, which is not stored yet; items link to AniList for now.

Written authorization from MyAnimeList is still needed before any real charge (the "pending" cell above stays open until the owner asks for it).
