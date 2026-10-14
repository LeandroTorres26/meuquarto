# 0002. Separate Next.js frontend

- Status: Accepted
- Date: 2026-10-12

## Context
The course requires Java on the backend; the professor confirmed the frontend may use JavaScript/TypeScript. The public profile is opened from social links, so it needs fast first paint, SEO and Open Graph previews. The design includes interactive pieces (item dialog, room scene).

## Decision
A Next.js (TypeScript) app in `frontend/`, consuming the Spring Boot REST API documented with OpenAPI. Public pages (`/{slug}`, `/{slug}/{categoria}`) are server-rendered; the painel is client-heavy. Both live in the monorepo.

## Consequences
- Clean API boundary, reusable later by widgets or a mobile app.
- Two runtimes to build and deploy (two containers behind Caddy).
- Cookies and CSRF must work across the web and API origins (same parent domain, see ADR 0003).

## Alternatives considered
- Thymeleaf + HTMX inside Spring: one runtime, but weaker for the interactive screens and Open Graph per profile is no easier.
- Pure SPA (Vite + React): no server rendering, bad for link previews and SEO.
