# Scope and backlog

## Scope: MVP (P2) vs roadmap
**In P2:** everything in `requirements.md` FR-01..FR-20 (room as minimal prototype).
**Roadmap (TCC):** full room (inventory, layout editor, premium objects), taste compatibility, embeddable widgets, story cards, yearly recap, "recommend me something", mixed collections, spoiler/private profile, visit stats, subscriptions, dedicated item page `/{slug}/item/{id}` and global work page `/obra/{id}` ("who else has it"), more integrations (see table below), CSV import (Skoob etc.), OAuth account linking, account export, admin panel, MAL, Google login, queue-based sync.

### Roadmap: future integrations (each = one new `ActivityProvider`)
Feasibility is a first guess and must be checked per platform (API availability and terms) before committing.

| Category | Platform | Likely route | Feasibility |
|---|---|---|---|
| Games | Epic Games Store | No public library API; CSV/manual import or third-party tools | Low |
| Games | Xbox / Game Pass | Xbox profile via third-party API (e.g. OpenXBL); Game Pass catalog is not a user library | Medium-low |
| Games | PlayStation (PSN) | Unofficial APIs only | Low |
| Games | GOG | Public profile / Galaxy export | Medium |
| Games | Backloggd, HowLongToBeat | No official API; CSV import | Low |
| Movies/Series | Trakt, Simkl | Official public APIs | High |
| Movies/Series | IMDb | CSV export import | Medium |
| Anime/Manga | MyAnimeList | API v2 with client id | High |
| Anime/Manga | Kitsu | Public API | High |
| Books | StoryGraph, Skoob | CSV import (no scraping) | Medium |
| Music | Spotify (top items, now playing), ListenBrainz | OAuth / public API | Medium-high |

## Timeline (2026-10-12 → 2026-11-30)
| Week | Milestone | Focus |
|---|---|---|
| 1 (Oct 12-18) | M0 Foundation | Repo, monorepo skeleton, CI, VPS + deploy "hello world"; start Claude Design in parallel |
| 2-3 (Oct 19-Nov 1) | M1 Profile | Auth, profile, highlights CRUD, catalog search, public page, Open Graph |
| 3-5 (Oct 26-Nov 15) | M2 Integrations | Provider interface, Letterboxd, AniList, Last.fm, Steam, Goodreads, sync job, "Agora" |
| 5-6 (Nov 9-22) | M3 Monetization | Stripe checkout, webhook, themes, limits |
| 6-7 (Nov 16-30) | M4 Room + polish | Room prototype, LGPD, docs, validation survey, presentation. Buffer. |

Cut order if late: Goodreads > Steam > room prototype > Last.fm. Never cut: CRUD, business rules, deploy.

## GitHub setup
Labels: `feature bug chore docs`, `backend frontend infra`, `P0 P1 P2`. Project columns: Backlog, A fazer, Em andamento, Em revisão, Feito. One branch per issue, PR linked (`Closes #n`), PR/issue templates. Actions: CI on PR, deploy on `main`, project automations. Agent works one issue at a time; owner reviews and merges every PR.

## Epics > features > issues
### E0 Foundation (M0)
- Monorepo skeleton: Spring Boot (Java 21, Maven), Next.js, `infra/docker-compose`
- Flyway baseline + Testcontainers test harness
- GitHub Actions CI (build, test, lint) for backend and frontend
- VPS provisioning, domain `.com.br`, HTTPS (Caddy), deploy workflow on merge
- Issue/PR templates, labels, Project board and automations
- `docs/` + ADR 1-4 committed
- API terms review (TMDB, AniList, IGDB, Deezer, Last.fm, Steam, Open Library) → ADR 10

### E1 Identity (M1)
- Sign up / sign in / sign out (session cookie, CSRF)
- Password change; account deletion (FR-02)
- Terms, privacy and cookie banner

### E2 Profile (M1)
- Profile entity + slug rule (BR-01) + reserved words
- Edit profile; avatar upload to S3-compatible storage
- Public profile page with cached read model (NFR-02)
- Open Graph metadata and image
- Item details modal (S17) with `?item=` URL state and external links

### E3 Catalog and highlights (M1)
- `CatalogProvider` interface + TMDB (movies/series)
- AniList catalog (anime/manga), Open Library (books), IGDB (games), Deezer (music)
- `media_items` snapshot + canonical key/dedup (BR-04)
- Collection per category: add manually, remove, hide, note (BR-07)
- Highlights: mark "Destacar" inside the collection + reorder (BR-02)
- Public category listing `/{slug}/{categoria}` (S18): search, sort, pagination
- "Favoritos de tudo" list
- Plan limit rule 4/12 (BR-02) with upsell state

### E4 Integrations (M2)
- `ActivityProvider` interface, `ProviderRegistry`, linked accounts CRUD and validation
- Letterboxd RSS adapter (TMDB id mapping)
- AniList activity adapter
- Last.fm adapter (now playing, top tracks/artists)
- Steam adapter (recently played; appid → IGDB mapping)
- Goodreads RSS adapter
- Sync upserts collection items per platform rules (BR-10); unlink keeps highlighted/noted items as manual (FR-16)
- Sync scheduler + manual sync with cooldown (BR-03) + `sync_runs` + backoff
- "Agora" block auto + override (BR-06) + adult filter (BR-08)
- Provider response cache and rate limiting (NFR-03)

### E5 Monetization (M3)
- Products/themes seed; theme application on profile (BR-05)
- Stripe Checkout (Pix + card), webhook with signature check and idempotency (BR-09)
- Entitlements and plan limits, purchases page
- Stripe test→live checklist (MEI, account activation)

### E6 Room prototype (M4)
- Design spike and ADR 0012 (technology)
- Scene from highlights (posters, shelf, vinyl, console, board)
- "Ver quarto" entry from profile; lazy loading

### E7 Quality and delivery (M4)
- OpenAPI docs published
- E2E smoke test of main flows
- Observability: structured logs, basic health endpoint, uptime check
- Taste survey (15-20 people) → slide
- Presentation deck and demo script; OO design slide (Strategy/Adapter)

## Pending outside the code (owner)
- Confirm with professor all P2 requirements are met (hosting, individual work)
- Check domain `meuquarto.com.br`, social handles, INPI
- Create Stripe account (MEI) and confirm Pix is enabled
- Create Twitch app for IGDB, TMDB and Last.fm API keys
- Choose VPS provider
