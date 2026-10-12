# Requirements

Source: `docs/briefing.md` + planning session (2026-10-12). Deadline: end of Nov 2026 (~7 weeks).

## Functional requirements (MVP)

### Accounts
- FR-01 Sign up / sign in with e-mail + password; session via HttpOnly cookie.
- FR-02 Delete account (hard delete of all user data; payment records kept anonymized).
- FR-03 Terms, privacy policy and minimal cookie banner.

### Profile
- FR-04 Public profile at `/{slug}`; slug unique (BR-01).
- FR-05 Edit display name, bio, avatar (upload to object storage), chosen theme.
- FR-06 Open Graph preview (title, description, image) when the link is shared.
- FR-07 Adult-content filter: items flagged adult by providers are hidden (BR-08).

### Highlights (CRUD core)
- FR-08 Search each category in its catalog (TMDB, AniList, Open Library, IGDB, Deezer) and pin an item.
- FR-09 Create, reorder, edit (personal note, FR-10) and remove highlights per category.
- FR-10 Optional short personal note per highlight ("why it is here").
- FR-11 A mixed list "Favoritos de tudo" with items of any category.

### Integrations
- FR-12 Link an external account by public username: Letterboxd (RSS), AniList (GraphQL, anime + manga), Last.fm, Steam, Goodreads (RSS). Validate that the account exists and is public.
- FR-13 Scheduled sync (every ~6 h) plus manual "sync now" button (BR-03).
- FR-14 Show sync status per account (last success, last error).
- FR-15 "Agora" block: one item per category, automatic from sync, with manual override or hide (BR-06).
- FR-16 Unlink an account (synced data removed; manual highlights kept).

### Monetization
- FR-17 Theme/cosmetic store (seeded catalog, no admin panel).
- FR-18 Buy a pack via Stripe Checkout (Pix + card); webhook confirms and grants the entitlement.
- FR-19 Purchases listed in the user's account; owned themes selectable (BR-05).

### Room (minimal prototype, technology decided after design)
- FR-20 "Ver quarto" action renders the profile highlights as objects in a fixed isometric scene. Read-only, no inventory or purchases in P2.

## Business rules
| ID | Rule |
|---|---|
| BR-01 | Profile slug is unique, lowercase, 3-30 chars, `[a-z0-9-]`, with a reserved-word list. |
| BR-02 | Free plan: max 4 highlights per category and 4 in "Favoritos de tudo". With a purchased pack: 12 each. |
| BR-03 | A linked account cannot be synced again within 15 min of the previous sync; the scheduler picks accounts older than 6 h. |
| BR-04 | The same work from different sources is one `media_item` (canonical key: category + canonical catalog + external id). |
| BR-05 | A premium theme can only be applied if the user owns it. Purchases are permanent. |
| BR-06 | "Agora" shows the latest synced item per category unless the user pinned or hid it. |
| BR-07 | A highlight can't be duplicated inside the same list. |
| BR-08 | Adult-flagged items are never shown on the public profile in P2. |
| BR-09 | A purchase is granted only after a verified Stripe webhook, idempotent per event id. |

## Non-functional requirements
- NFR-01 Modular monolith; each external platform is one `MediaProvider` implementation (Strategy/Adapter).
- NFR-02 Public profile p95 < 500 ms (cached read model); external APIs never called on profile render.
- NFR-03 External API calls: cached, rate-limit aware, timeouts, graceful failure (one provider down must not break others).
- NFR-04 Versioned migrations (Flyway); OpenAPI documentation generated.
- NFR-05 Unit tests for business rules; integration tests with Testcontainers (PostgreSQL); providers mocked. CI required on every PR.
- NFR-06 Docker Compose deploy on a single VPS with HTTPS; auto-deploy on merge to `main`.
- NFR-07 Security: BCrypt/Argon2 passwords, CSRF protection, secrets only in environment and never exposed to the browser, Stripe webhook signature check.
- NFR-08 Images in S3-compatible storage (R2 or MinIO), never in the database.
- NFR-09 LGPD basics: consent via terms, data deletion, minimal data collected.
- NFR-10 UI language pt-BR; code, repo and technical docs in English.
- NFR-11 API terms review (TMDB attribution and commercial license, others) recorded in an ADR before real monetization.
