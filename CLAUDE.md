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
| Threats and security controls | `docs/security.md` |

The GitHub issue being worked on is the spec for the task. If an issue conflicts with these docs, stop and ask.

## Stack

- `backend/`: Java 21, Spring Boot 4, Maven, Spring Security (session cookie, CSRF), Spring Data JPA, PostgreSQL, Flyway, springdoc-openapi, Caffeine, Testcontainers.
- `frontend/`: Next.js 16 + TypeScript (Cache Components). Public profile pages are server-rendered (SEO and Open Graph). Calls the backend REST API; never holds secrets.
- `infra/`: Docker Compose (caddy, web, api, postgres, minio), deploy scripts.
- Payments: Stripe Checkout (Pix + card), one-time purchases, entitlement granted only by a verified webhook.
- Object storage: S3-compatible (R2 or MinIO).

## Security (every issue)

Security is a requirement of every issue, not a later phase. Read `docs/security.md` before working on auth, endpoints, uploads, integrations, payments, infra or CI. Non-negotiable:

- Never commit secrets. `.env` is ignored; add new variable names to `.env.example` without values. If a secret ever lands in git, stop and tell the owner (it must be rotated, not just deleted).
- Every endpoint is private by default; the owner of any data comes from the session, never from the request. Each new endpoint gets an authorization test (anonymous and other-user cases).
- Validate every input (Bean Validation, size limits); no string-built SQL; no user-provided URLs fetched; usernames sent to providers are validated and URL-encoded.
- No raw HTML from users or providers in the UI; no secrets in the frontend bundle; no passwords, tokens or cookies in logs.
- New dependencies: well-maintained, pinned versions; mention them in the PR.
- If something in the issue would weaken security, stop and ask instead of working around it.

## Backend conventions

- Modular monolith, package by module: `com.meuquarto.<module>` with modules `identity`, `profile`, `media`, `billing`, `room` (ADR 0001).
- Inside each module, four light layers (ADR 0014): `api/` (controllers, request/response DTOs), `application/` (use cases, transactions, authorization), `domain/` (entities, value objects, business rules), `infra/` (repositories, external clients). Dependencies point inward: `api` → `application` → `domain`; `domain` imports nothing from the other three.
- Controllers are thin: validate, call one application service, map the result. No business rules in controllers or repositories.
- Between modules, call only the other module's `application` services and DTOs; never its `domain` entities, repositories, tables or `infra`.
- Integrations are the OO showcase: one class per platform implementing `CatalogProvider` or `ActivityProvider` (in `media/infra`), resolved by a registry. Adding a platform must not touch other modules.
- Every business rule (BR-xx in `docs/requirements.md`) lives in `domain`, is unit-tested without Spring in a test named after it, and is referenced in a comment only where the code would otherwise be unclear.
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

Backend (from `backend/`, needs JDK 21; Maven comes from the wrapper):
- `./mvnw verify`: compile and run all tests (the CI gate)
- `./mvnw spring-boot:run`: run the API on http://localhost:8080 (health at `/api/health`)

Frontend (from `frontend/`, needs Node 22):
- `npm ci`: install exactly what the lockfile pins
- `npm run dev`: dev server on http://localhost:3000 (expects the API at `API_URL`, default `http://localhost:8080`)
- `npm run lint`, `npm run typecheck`, `npm run format:check`, `npm run build`: the CI gate
- `npm run format`: apply Prettier

Whole stack (from the repo root, needs Docker; copy `.env.example` to `.env` first):
- `docker compose -f infra/docker-compose.yml --env-file .env up --build`
- `docker compose -f infra/docker-compose.yml --env-file .env down` (add `-v` to wipe the database volume). Every compose command needs `--env-file .env`.

Next.js 16 differs from older versions (Cache Components are on): read `frontend/AGENTS.md` and the docs in `frontend/node_modules/next/dist/docs/` before writing frontend code.
