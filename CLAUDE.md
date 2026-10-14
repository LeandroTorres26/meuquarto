# meuquarto

Link-in-bio for media taste: a public profile at `/{slug}` showing highlights of films, series, anime, manga, books, games and music, synced from the platforms the user already uses (Letterboxd, AniList, Last.fm, Steam, Goodreads) or added by hand. Academic project (POO P2, Fatec Baixada Santista), possible TCC. Deadline for P2: end of Nov 2026.

## Read before working

| What | Where |
|---|---|
| Product context | `docs/briefing.md` |
| Requirements, business rules (BR-xx) | `docs/requirements.md` |
| Architecture and module boundaries | `docs/architecture.md` |
| Decisions and their reasons | `docs/adr/` |
| Data model | `docs/data-model.md` |
| Screens and flows | `docs/screens.md` |
| Visual design: prototypes of every screen | `docs/design/telas/` (start with its `README.md`) |
| Design system: tokens, components, rules | `docs/design-system/` (start with its `README.md`) |
| Scope, milestones, epics | `docs/backlog.md` |

The GitHub issue being worked on is the spec for the task. If an issue conflicts with these docs, stop and ask.

## Stack

- `backend/`: Java 21, Spring Boot 3, Maven, Spring Security (session cookie, CSRF), Spring Data JPA, PostgreSQL, Flyway, springdoc-openapi, Caffeine, Testcontainers.
- `frontend/`: Next.js + TypeScript. Public profile pages are server-rendered (SEO and Open Graph). Calls the backend REST API; never holds secrets.
- `infra/`: Docker Compose (caddy, web, api, postgres, minio), deploy scripts.
- Payments: Stripe Checkout (Pix + card), one-time purchases, entitlement granted only by a verified webhook.
- Object storage: S3-compatible (R2 or MinIO).

## Backend conventions

- Modular monolith, package by module: `com.meuquarto.<module>` with modules `identity`, `profile`, `media`, `billing`, `room`. A module exposes a small public API; it never reads another module's tables or repositories directly.
- Integrations are the OO showcase: one class per platform implementing `CatalogProvider` or `ActivityProvider`, resolved by a registry. Adding a platform must not touch other modules.
- Every business rule (BR-xx in `docs/requirements.md`) lives in the domain/service layer, has a unit test named after it, and is referenced in a comment only where the code would otherwise be unclear.
- Schema changes only through new Flyway migrations; never edit an applied migration.
- External APIs are never called while rendering a public profile; the profile reads local data (snapshot in `media_items`) and a cache.
- External calls have timeouts, respect rate limits and fail per provider (one provider down never breaks the others). Tests mock providers; no test hits a real external API.
- Secrets only from environment variables. Never commit keys; `.env.example` lists the names.

## Frontend conventions

- Implement screens from `docs/design/telas/*.html`, keeping class names from `site.css` and `docs/design-system/components/bundle.css`, and using the design system components (`HighlightCard`, `HighlightGrid`, `CategoryTag`…). Tokens come from `docs/design-system/tokens.json` / `meuquarto.tokens.css`; no hard-coded colors or spacing.
- UI copy in pt-BR (informal "você", sentence case, no emoji, no exclamation marks). Code, identifiers, commits and technical docs in English.
- Accessibility as in the design system README: real buttons and links, labels on inputs, visible focus, contrast 4.5:1, `prefers-reduced-motion` respected.
- Icons: Lucide.

## Workflow

- One GitHub issue at a time. Branch `<type>/<issue-number>-<short-slug>` (types: `feat`, `fix`, `chore`, `docs`), from `main`.
- Commits in Conventional Commits style (`feat(profile): ...`). Small, focused commits.
- Open a PR linked to the issue (`Closes #n`) using the PR template. The owner reviews and merges every PR; never merge, force-push to `main` or enable auto-merge.
- A PR is ready only when: CI passes, new behavior has tests, migrations run on a clean database, and the docs that changed (API, decisions, rules) are updated in the same PR.
- A new architectural decision gets a new ADR in `docs/adr/` (next number, same template).
- Don't add features outside the issue. Out-of-scope findings become a note in the PR or a new issue suggestion.

## Commands

Filled in by the M0 skeleton issue (build, test, run locally, lint for backend and frontend, docker compose up).
