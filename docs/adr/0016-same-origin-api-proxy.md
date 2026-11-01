# 0016. Same-origin API through proxy.ts

- Status: Accepted
- Date: 2026-10-27

## Context
The session cookie is HttpOnly and SameSite=Lax, CSRF is cookie-to-header, CORS is off (ADR 0015) and the CSP says `connect-src 'self'`. The browser therefore has to talk to one origin only. The Next.js image is built once and runs in several environments (`API_URL` is `http://localhost:8080` in dev, `http://api:8080` in compose), so the API address cannot be frozen at build time (`rewrites()` in `next.config.ts` is evaluated during the build).

## Decision
- `frontend/src/proxy.ts` rewrites `/api/*` to `API_URL`, read from the environment on every request. Next forwards method, body, cookies and headers, and returns the response with every `Set-Cookie`. The browser only ever sees `/api/...` on the web origin.
- The same file does the session gates with real HTTP redirects: `/entrar` and `/cadastro` redirect to `/painel/conta` when `GET /api/auth/me` says there is a session; `/painel/*` redirects to `/entrar` when there is none. A `redirect()` inside a Suspense boundary (required by Cache Components for pages that read cookies) would only be a client-side redirect on a 200 response.
- Server components call the API directly (`src/lib/session.ts`), forwarding the incoming cookies.
- The browser client (`src/lib/api-client.ts`) fetches `GET /api/auth/csrf` when the `XSRF-TOKEN` cookie is missing and sends `X-XSRF-TOKEN` on every POST.

## Consequences
- The API address and its port are never in the client bundle; CSP and cookies need no change.
- Every request to the API passes through the web container, and the backend sees the web container as the client address. The per-IP limits (ADR 0015) only work because the proxy rebuilds `X-Forwarded-For` and the API trusts it from private peers only (ADR 0017).
- The proxy runs a short `GET /api/auth/me` for the pages above on every request (3 s timeout; failure counts as signed out).

## Alternatives considered
- `rewrites()` in `next.config.ts`: build-time value, one image per environment.
- A catch-all route handler: works at runtime but re-implements header, cookie and streaming forwarding by hand.
- Calling the API from the browser with CORS: breaks `connect-src 'self'`, needs `SameSite=None` or a shared parent domain.
