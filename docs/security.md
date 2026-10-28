# Security

Security is part of every issue, not a phase. This file is the baseline: what we protect, the threats we expect, and the controls each layer must have. A PR that touches one of these areas must keep its controls (see the PR checklist).

## What we protect
- User credentials and sessions.
- Personal data: e-mail, profile, linked usernames, purchase history (LGPD).
- Payments and entitlements (nobody gets a paid item without paying; nobody pays twice).
- Secrets: API keys (TMDB, IGDB, Last.fm, Steam), Stripe keys and webhook secret, database and storage credentials, SSH keys.
- Availability of public profiles.

## Cryptography and authentication (ADR 0012)
Rule: only ready-made, maintained components; no hand-written cryptography, token formats or auth filters.

| Need | Component |
|---|---|
| Authentication, sessions, CSRF, access control | Spring Security |
| Password hashing | Argon2id (`Argon2PasswordEncoder`) behind `DelegatingPasswordEncoder` |
| Session storage | Spring Session JDBC (PostgreSQL), ID rotated on login |
| Reset tokens | `SecureRandom` 256 bits, stored as SHA-256 hash, single use, 30 min |
| Transport | TLS 1.2/1.3 by Caddy, Let's Encrypt, HSTS |
| Payments | Stripe Checkout (no card data on our side) |
| Backups | restic (AES-256), off the VPS |
| E-mail | Transactional provider with SPF, DKIM, DMARC |

## Main threats
| Threat | Where | Controls |
|---|---|---|
| Account takeover (credential stuffing, brute force) | sign in, password reset | Argon2id, common-password check, reset links single-use and short-lived with neutral responses (no account enumeration), rate limit and lockout backoff on sign in, generic error messages, session rotation on login, logout invalidates session |
| Session theft / CSRF | painel | HttpOnly + Secure + SameSite=Lax cookie (set on Spring Session's `CookieSerializer`, tested), CSRF tokens on state-changing requests, short idle timeout |
| IDOR (editing someone else's data) | every painel endpoint | owner taken from the session, never from the request body or path; authorization test per endpoint |
| XSS | profile name, bio, notes, provider data | React escaping only, no `dangerouslySetInnerHTML`, strict CSP, sanitize on input length/charset, provider strings treated as untrusted |
| Provider keys and responses (catalogs) | `TMDB_API_KEY` and other catalog tokens | Read only from the environment, sent only in an `Authorization` header (never in a URL), never logged (a failed call logs the provider and the kind of failure only), never sent to the browser. Catalog calls go to a fixed base URL, ids are validated before they enter a URL, queries are URL-encoded, redirects are not followed, responses are capped at 2 MB, and poster URLs must be a plain path on the provider image host. The search endpoint needs a session and is rate limited |
| SSRF / injection via usernames | linked accounts | The platform decides the username format (`ActivityProvider.usernameRules`: length, then a strict ASCII pattern matched against the whole name) and nothing is sent before it passes, both at link time and again inside the RSS template. The name is URL-encoded and only fills a path: each provider has a fixed origin (`baseUri`), and the built URL must keep that scheme, host and port and carry no user info, query or fragment. No user-provided URL is ever fetched, redirects are not followed, requests have connect and read timeouts, bodies are capped at 2 MB and at 200 items per feed, a provider failure is one value or one error code and the log names only the provider and the kind of failure. Linking is private (session), limited per IP (10 per 10 min) and the one-account-per-provider check runs before the outbound request |
| XXE / XML bombs in provider feeds | RSS providers (Letterboxd, Goodreads) | `RssActivityProvider` parses with `disallow-doctype-decl` (a DOCTYPE is refused outright, so no external entities, external DTDs or entity expansion), secure processing on, `ACCESS_EXTERNAL_DTD` and `ACCESS_EXTERNAL_SCHEMA` empty, XInclude off; the response size cap bounds the rest. Tested with external entity, file entity, external DTD and billion-laughs payloads |
| Malicious uploads | avatar | allowlist of image types checked by content, size limit, re-encode/resize (strips metadata), random object keys, served with correct content type |
| Payment fraud / double grant | Stripe webhook | signature verification, idempotency by event id, entitlement only from webhook, amounts from our catalog, never from the client |
| Secret leakage | repo, CI, logs | `.gitignore` for `.env`, secret scanning in CI, secrets only in GitHub environments and server env, no secrets or tokens in logs |
| Vulnerable dependencies | backend, frontend, images | Dependabot alerts and updates, CodeQL, pinned base images, regular updates |
| Abuse of public endpoints | search, sync, profile | rate limits per IP/user, manual sync cooldown (BR-03), pagination caps |
| Server compromise | VPS | SSH keys only, no root login, firewall (only 80/443/SSH), unattended security updates, fail2ban, database and MinIO not exposed publicly |
| Data loss | database, storage | daily encrypted backups off the server, restore tested |

## Controls by layer

### Repository and CI
- `.env` and key files ignored; only `.env.example` with names.
- Secret scanning in layers: optional local pre-commit hook (`.githooks/pre-commit`), GitHub push protection, gitleaks over the full history on every PR (required check), GitHub secret scanning alerts.
- Dependabot alerts and version updates; CodeQL (`security-extended`) for Java, TypeScript and the workflows on every PR and weekly.
- Owner reviews every PR; no direct pushes to `main` once CI exists.

### Backend
- Spring Security defaults on; deny by default, explicit public routes (`/api/public/**`, auth endpoints, Stripe webhook).
- Bean Validation on every request DTO; size limits on strings and lists.
- JPA parameters only (no string-built SQL).
- Error responses without stack traces or internal details.
- Security headers on every API response (Spring Security): `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, HSTS (1 year, includeSubDomains; Spring sends it on HTTPS only), `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`. Tested.
- CORS is disabled: the browser only talks to the Next.js origin, which proxies `/api`. A cross-origin preflight is refused (tested).
- Per-IP rate limiting (`shared/ratelimit`, ADR 0015), in memory, answering 429 with `Retry-After`: sign up 10/hour, sign in 30/15 min (on top of the per-account throttle), slug availability 60/min, catalog search 60/min, add to collection 30/min, link an account 10/10 min, public profile reads 120/min, anonymous ceiling 300/min. Limits are `meuquarto.rate-limit.*`. Manual sync (BR-03): the per-account cooldown (15 min, 429 `SYNC_COOLDOWN`) plus 30 requests per 10 min per IP on `POST /api/linked-accounts/*/sync` (`manual-sync`).
- Sync stores and shows only our own error codes (`sync_runs.error`, `linked_accounts.last_error`), never provider response text; a provider failure is logged as provider name plus exception class, without URLs or usernames. The provider is called outside database transactions, with the account's own validated username.
- The client address is `getRemoteAddr()`, filled by Tomcat's `RemoteIpValve` (`server.forward-headers-strategy: native`) from `X-Forwarded-For` only when the direct peer is in `server.tomcat.remoteip.internal-proxies` (Docker private ranges and loopback); from any other peer the header is ignored (ADR 0017, tested). The API port must never be public: compose binds it to `127.0.0.1` and production publishes only Caddy.
- The web app proxies /api to the API (ADR 0016) and rebuilds the forwarding headers itself: it drops every incoming `X-Forwarded-*`, `Forwarded` and `X-Real-IP`, and only when `TRUST_PROXY_HEADERS=true` sets back one validated `X-Forwarded-For` (right-most entry) and `X-Forwarded-Proto`. Set `TRUST_PROXY_HEADERS=true` only when Caddy (which overwrites `X-Forwarded-For`) is the sole way to reach the web container; default false, in which case the API sees the web container as the client (limits shared, never bypassable). Next.js 16 exposes no socket address in `proxy.ts`, hence the flag.
- Structured logs without passwords, tokens, cookies or full e-mails.
- Least-privilege database user for the app (no superuser); Flyway runs with the same or a migration user.

### Public profile read API (issues #16 and #26)
- Three GET-only routes are open (`permitAll`): `/api/public/profiles/{slug}`, `/api/public/profiles/{slug}/items/{ref}` and `/api/public/profiles/{slug}/collection`. Any other method on `/api/public/**` needs a session (401). They share one per-IP limit (120/min, `meuquarto.rate-limit.public-read.*`), 404s included, because probing slugs is the abuse to slow down. No session or `SESSION` cookie is created for these reads (tested).
- Built only from local data (snapshot in `media_items`, ADR 0006) and a Caffeine cache of the profile home (60 s TTL, at most 2000 profiles, evicted after commit by every use case that changes the collection or highlights). No provider is called on these paths (tested with a counting fake catalog).
- Exposed: slug, display name (the slug until #15), per item the title, year, category, cover URL, the owner's note, a `ref`, how it got into the collection (`MANUAL` or provider name and first-seen time) and a link to the catalog page built from a fixed list of hosts (`MediaLinks`), never from stored URLs.
- Not exposed: e-mail, user id, profile id, hidden items (not listed, not counted, not openable), adult-flagged works (BR-08; filtered by the media module so no caller can forget), linked account ids, sync times, plan. `ref` is the collection item id: it is only valid under the profile it belongs to (another profile answers 404 as if it did not exist) and nothing can be done with it anonymously.
- Unknown, reserved, malformed and oversized slugs, and hidden, adult or foreign items, all answer 404 with the same body shape (`PROFILE_NOT_FOUND` / `ITEM_NOT_FOUND`), so the API does not tell a reserved slug from a free one. Listing inputs are bounded: `size` at most 48, search text at most 100 characters (LIKE wildcards escaped), `sort` and `category` are enums.
- The web server renders pages by calling these routes: its requests reach the API from the web container, so per-IP limits only work per visitor if the server-side fetch forwards the visitor address (ADR 0017).

### Frontend
- Security headers via `next.config.ts` (CSP, HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `frame-ancestors 'none'`). The CSP is static with `'unsafe-inline'` for scripts and styles because a nonce would disable partial prerendering (ADR 0015); no `unsafe-eval` in production.
- No secrets in the client bundle (only `NEXT_PUBLIC_*` values that are public by design).
- External links with `rel="noopener noreferrer"`.

### Infrastructure
- HTTPS everywhere (Caddy), HTTP redirects to HTTPS.
- Containers run as non-root; only Caddy publishes ports.
- Production secrets in a root-only env file on the server and in GitHub environments; rotated if leaked.
- Backups encrypted and stored off the VPS.

### Privacy (LGPD)
- Collect only what is needed; account deletion removes personal data (orders anonymized).
- Privacy policy lists data, purpose, retention and third parties (Stripe, providers).

## Reporting
Security issues found during development are fixed in the current PR when small, otherwise opened as an issue labeled `security` with priority P0.
