# Requirements

Source: `docs/briefing.md` + planning session (2026-10-12). Deadline: end of Nov 2026 (~7 weeks).

## Functional requirements (MVP)

### Accounts
- FR-01 Sign up / sign in with e-mail + password; session via HttpOnly cookie.
- FR-01a "Esqueci minha senha": request a reset link by e-mail (single-use, 30 min), set a new password; all other sessions end (ADR 0012).
- FR-02 Delete account (hard delete of all user data).
- FR-03 Terms, privacy policy and minimal cookie banner.

### Profile
- FR-04 Public profile at `/{slug}`; slug unique (BR-01).
- FR-05 Edit display name, bio, avatar (upload to object storage), chosen theme: four themes (noite, dia, locadora, estufa), all free for everyone, applied and saved on click (ADR 0022, 0024).
- FR-06 Open Graph preview (title, description, image) when the link is shared. The image is generated per profile (display name, address and brand, no covers or avatar) and a default one covers pages without an owner; `twitter:card` is `summary_large_image`.
- FR-07 Adult-content filter: items flagged adult by providers are hidden (BR-08).

### Collection and highlights (CRUD core)
- FR-08 Each category has a **collection** with no size limit, fed by sync (BR-10) and by manual additions: search the category's catalog (TMDB, AniList, Open Library, IGDB, Deezer) and add an item.
- FR-09 The owner can add, remove and hide collection items; hiding a synced item keeps it out of the collection on later syncs.
- FR-09a **Highlights** are the collection items the owner marks "Destacar"; they are the only ones shown on the profile home, in the owner's order (BR-02). With no highlight marked, the profile shows the most recent items of the collection.
- FR-10 Optional short personal note per collection item ("why it is here").
- FR-10a Clicking any item (highlight, "Agora" or listing) opens a details modal (S17) with the note, origin and links to the item on its platforms; state kept in the URL (`?item=`). Every link opens the work's own page, built from a stored id (Letterboxd through `letterboxd.com/tmdb/{id}`, MyAnimeList through AniList's `idMal`), never a search; a platform with no id for the work has no link.
- FR-10b "Ver todos" per category opens the public listing `/{slug}/{categoria}` (S18): the whole visible collection as a cover grid with search by title, sort (recent, title, year), pagination or infinite scroll; highlights carry a badge.
- FR-11 A mixed list "Favoritos de tudo" with items picked from any collection (highlight-only, no listing).

### Integrations
- FR-12 Link an external account by public username (ADR 0023). Platforms that sync: Letterboxd (RSS), AniList (GraphQL, anime + manga), MyAnimeList (API v2 with the project's client ID, anime + manga, mapped to the AniList catalog), Last.fm (JSON API with the project's key; recent listening must be public), Steam, Goodreads (RSS); validate that the account exists and is public. Link-only platforms (lists not read): Backloggd, Trakt, StoryGraph; validate the username format only.
- FR-12a The public profile (S4) links to every linked account, in a fixed platform order, built from the username and a fixed URL template per platform (never a URL typed by the user); the links open in a new tab.
- FR-13 Scheduled sync (every ~6 h) plus manual "sync now" button (BR-03).
- FR-14 Show sync status per account (last success, last error).
- FR-15 "Agora" block: one item per category, automatic from sync, with manual override or hide (BR-06). Built (#35): `GET /api/now`, `PUT /api/now/{category}` (`mode` AUTOMATIC, PINNED with `collectionItemId`, or HIDDEN) and `now` in the public profile. Séries is manual only.
- FR-16 Unlink an account: items that came only from it leave the collection, except those the owner highlighted or noted, which become manual.

### Roadmap, out of the P2 (ADR 0024)
The store and paid plans are not part of the P2. These requirements and rules are kept only as the starting point if the store comes back in the TCC; nothing in the code implements them.
- FR-17 (roadmap) Theme/cosmetic store (seeded catalog, no admin panel). The tables and code that had been built for it were removed.
- FR-18 (roadmap) Buy a pack via Stripe Checkout (Pix + card); webhook confirms and grants the entitlement.
- FR-19 (roadmap) Purchases listed in the user's account; owned themes selectable (BR-05, roadmap).

### Room (minimal prototype, technology decided after design)
- FR-20 "Ver quarto" action renders the profile highlights as objects in a fixed isometric scene. Read-only, no inventory in P2.

## Business rules
| ID | Rule |
|---|---|
| BR-01 | Profile slug is unique, 3-30 chars of `a-z`, `0-9` and single hyphens between them (no leading, trailing or double hyphen); input is trimmed and lowercased; reserved words (app routes, infrastructure paths, brand) are refused. The list lives in `profile/domain/Slug.java`; the database repeats the format as a CHECK constraint. |
| BR-02 | Collections have no limit. Highlights (shown on the profile home) are limited to 4 per category and 4 in "Favoritos de tudo", the same for every user. The rule lives in `HighlightList` (`profile/domain`); reaching it is `HIGHLIGHT_LIMIT_REACHED` (HTTP 409). Test: `BR02HighlightLimitTest`. |
| BR-03 | A linked account cannot be synced again within 15 min of the previous sync; the scheduler picks accounts older than 6 h. |
| BR-04 | The same work from different sources is one `media_item` (canonical key: category + canonical catalog + external id). |
| BR-05 | (roadmap, out of the P2, ADR 0024) A premium theme can only be applied if the user owns it; purchases are permanent. Removed with the store: all four themes are free and the backend only checks that the theme id exists (`THEME_UNKNOWN`). |
| BR-06 | "Agora" shows the latest synced item per category unless the user pinned or hid it. |
| BR-07 | A work appears once per collection (synced and manual merge into one item) and once per highlight list. |
| BR-08 | Adult-flagged items are never shown on the public profile in P2. |
| BR-09 | (roadmap, out of the P2, ADR 0024) A purchase is granted only after a verified Stripe webhook, idempotent per event id. |
| BR-10 | What sync adds to the collection, per platform: Letterboxd, every diary entry in the RSS (~50 most recent; the collection accumulates from the link date on, older history via CSV in the roadmap). AniList and MyAnimeList, entries with status completed, current (watching or reading), paused or repeating (never planning or dropped); a MyAnimeList entry is mapped to the AniList work with the same `idMal`, so an account linked on both platforms adds each work once. Goodreads, shelves read (finished) and currently-reading (in progress); the books are provisional Goodreads rows until mapped to Open Library (ADR 0005). Steam, owned games with playtime > 0. Last.fm, albums with at least N scrobbles among the user's 200 most recent (N = 10, a constant of `LastFmProvider`) as one entry each at their newest scrobble, plus the album playing now (FR-15, "Ouvindo"); never each play. Loved tracks are not read (they carry no album, and the unit of music is the album). The albums are provisional Last.fm rows until mapped to Deezer (ADR 0005). |

## Non-functional requirements
- NFR-01 Modular monolith; each external platform is one `MediaProvider` implementation (Strategy/Adapter).
- NFR-02 Public profile p95 < 500 ms (cached read model); external APIs never called on profile render.
- NFR-03 External API calls: cached, rate-limit aware, timeouts, graceful failure (one provider down must not break others).
- NFR-04 Versioned migrations (Flyway); OpenAPI documentation generated.
- NFR-05 Unit tests for business rules; integration tests with Testcontainers (PostgreSQL); providers mocked. CI required on every PR.
- NFR-06 Docker Compose deploy on a single VPS with HTTPS; auto-deploy on merge to `main`.
- NFR-07 Security: only ready-made, maintained components (Spring Security, Argon2id passwords, Spring Session JDBC, TLS by Caddy); no hand-written cryptography or auth. CSRF protection, secrets only in environment and never exposed to the browser, Stripe webhook signature check. Details in `docs/security.md` and ADR 0012.
- NFR-08 Images in S3-compatible storage (R2 or MinIO), never in the database.
- NFR-09 LGPD basics: consent via terms, data deletion, minimal data collected.
- NFR-10 UI language pt-BR; code, repo and technical docs in English.
- NFR-11 API terms review (TMDB attribution and commercial license, others) recorded in an ADR before real monetization.
