# Security

Security is part of every issue, not a phase. This file is the baseline: what we protect, the threats we expect, and the controls each layer must have. A PR that touches one of these areas must keep its controls (see the PR checklist).

## What we protect
- User credentials and sessions.
- Personal data: e-mail, profile, linked usernames, purchase history (LGPD).
- Payments and entitlements (nobody gets a paid item without paying; nobody pays twice).
- Secrets: API keys (TMDB, IGDB, Last.fm, Steam), Stripe keys and webhook secret, database and storage credentials, SSH keys.
- Availability of public profiles.

## Main threats
| Threat | Where | Controls |
|---|---|---|
| Account takeover (credential stuffing, brute force) | sign in | BCrypt/Argon2, rate limit and lockout backoff on sign in, generic error messages, session rotation on login, logout invalidates session |
| Session theft / CSRF | painel | HttpOnly + Secure + SameSite=Lax cookie, CSRF tokens on state-changing requests, short idle timeout |
| IDOR (editing someone else's data) | every painel endpoint | owner taken from the session, never from the request body or path; authorization test per endpoint |
| XSS | profile name, bio, notes, provider data | React escaping only, no `dangerouslySetInnerHTML`, strict CSP, sanitize on input length/charset, provider strings treated as untrusted |
| SSRF / injection via usernames | linked accounts | usernames validated by a strict regex per provider and URL-encoded; requests only to a fixed allowlist of provider hosts; no user-provided URLs fetched |
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
- Secret scanning in CI (gitleaks) on every PR; Dependabot alerts and version updates; CodeQL for Java and TypeScript.
- Owner reviews every PR; no direct pushes to `main` once CI exists.

### Backend
- Spring Security defaults on; deny by default, explicit public routes (`/api/public/**`, auth endpoints, Stripe webhook).
- Bean Validation on every request DTO; size limits on strings and lists.
- JPA parameters only (no string-built SQL).
- Error responses without stack traces or internal details.
- Rate limiting (sign in, sign up, search, manual sync).
- Structured logs without passwords, tokens, cookies or full e-mails.
- Least-privilege database user for the app (no superuser); Flyway runs with the same or a migration user.

### Frontend
- Security headers via Next.js/Caddy: CSP, HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `frame-ancestors 'none'`.
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
