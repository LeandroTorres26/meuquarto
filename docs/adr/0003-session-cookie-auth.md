# 0003. Session cookie over JWT

- Status: Accepted, refined by 0012 (Argon2id, Spring Session JDBC, password reset)
- Date: 2026-10-12

## Context
Users sign in with e-mail and password. The only clients are our own web app; there is no third-party API consumer.

## Decision
Spring Security server-side sessions in an `HttpOnly`, `Secure`, `SameSite=Lax` cookie, CSRF protection on state-changing requests, passwords hashed with BCrypt (or Argon2). Web and API share the parent domain (e.g. `meuquarto.com.br` and `api.meuquarto.com.br`) so the cookie works for both. Google login is roadmap.

## Consequences
- Tokens are never readable by JavaScript; logout and revocation are immediate.
- Sessions are server state; with one instance this is in memory or JDBC-backed. Scaling out later means Spring Session (JDBC or Redis).
- Next.js server-side requests on behalf of a signed-in user must forward the cookie.

## Alternatives considered
- JWT in `localStorage`: exposed to XSS, revocation is hard, no gain for a first-party web app.
- JWT in a cookie: same exposure as a session cookie with more moving parts.
