# 0015. CSP without nonce and in-memory rate limiting

- Status: Accepted
- Date: 2026-10-27

## Context
Issue #52 asks for a strict CSP on the web app and rate limits on the abuse-prone endpoints. The frontend uses Cache Components with partial prerendering so public profiles are fast and SEO friendly. The Next.js CSP guide says a nonce-based CSP needs fully dynamic rendering and is incompatible with partial prerendering. The API runs as a single container.

## Decision
- Frontend: a static CSP set in `next.config.ts`, no nonce. `script-src 'self' 'unsafe-inline'` (Next needs inline bootstrap scripts), `style-src 'self' 'unsafe-inline'`, everything else locked down: `default-src 'self'`, `img-src 'self' data: https:` (provider covers are hotlinked, ADR 0006), `connect-src 'self'`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'`. `'unsafe-eval'` only in development. XSS is still prevented first by React escaping and no raw HTML (docs/security.md). Revisit when Next.js SRI-based CSP leaves experimental.
- API: Spring Security headers (`default-src 'none'; frame-ancestors 'none'`, HSTS, nosniff, DENY, referrer policy, permissions policy). CORS stays off: the browser only talks to the Next.js origin.
- Rate limiting: a small in-memory fixed-window limiter in `shared/ratelimit`, applied by a filter inside the security chain, per client IP (`getRemoteAddr()`). Defaults: sign-up 10/hour, sign-in 30/15 min, slug availability 60/min, catalog search 60/min, add to collection 30/min, public profile reads (`GET /api/public/**`) 120/min, anonymous ceiling 300/min. Configurable under `meuquarto.rate-limit.*`. Answers 429 with `Retry-After` and `{"error":"TOO_MANY_REQUESTS"}`.

## Consequences
- Public pages stay prerendered and cacheable; the CSP does not block injected inline scripts, so it is a second line of defense, not the first.
- Limiter state is per instance and lost on restart. Fine for one API container; moving to several instances needs a shared store (Redis) or edge limits.
- Behind the web proxy and Caddy, the client address comes from trusted forwarded headers, otherwise every client shares the proxy address (ADR 0017).

## Alternatives considered
- Nonce-based CSP via `proxy.ts`: strictest, but forces dynamic rendering of every page and disables partial prerendering.
- Experimental SRI hash-based CSP: keeps static rendering, but experimental.
- Bucket4j or Redis for limits: more features, new dependency or service not needed yet.
