# Architecture

## Overview
```
Browser ── Next.js (TypeScript, SSR for /{slug} + Open Graph)
              │  REST/JSON (OpenAPI), session cookie
              ▼
        Spring Boot 3 (Java 21) — modular monolith
   ┌───────────┬────────────┬─────────────┬────────────┬──────────┐
   │ identity  │ profile    │ media       │ billing    │ room     │
   │ (auth)    │ (highlights│ (catalog,   │ (Stripe,   │ (proto)  │
   │           │  now, theme│ providers,  │ orders,    │          │
   │           │  slug)     │ sync jobs)  │ entitle.)  │          │
   └───────────┴────────────┴─────────────┴────────────┴──────────┘
        │ PostgreSQL (Flyway)      │ S3-compatible storage (R2/MinIO)
        │ cache (Caffeine)         │ external APIs: TMDB, AniList, Letterboxd RSS,
                                   │ Last.fm, Steam, Goodreads RSS, Open Library, IGDB, Deezer, Stripe
```
Deploy: one VPS, Docker Compose (caddy, web, api, postgres, minio optional), GitHub Actions builds images and deploys on merge to `main`. Production only; Stripe in test mode until the switch.

## Stack
Java 21, Spring Boot 3.x, Maven, Spring Security, Spring Data JPA, Flyway, springdoc-openapi, Caffeine cache, Testcontainers. Front: Next.js + TypeScript. Monorepo: `backend/`, `frontend/`, `docs/`, `infra/`.

## Modules and boundaries
Package by module (`com.meuquarto.<module>`), each exposing a small public API; no module reads another's tables directly. Enforce with ArchUnit later (TCC).

## Provider design (OO showcase)
```java
interface CatalogProvider { SearchResult search(Category c, String q); MediaItem fetch(ExternalRef ref); }
interface ActivityProvider { Category category(); ProviderId id();
    void validate(String username);           // exists and public
    ActivitySnapshot fetch(String username);  // recent items + "now" candidate
}
```
- One class per platform (Strategy/Adapter), each converts to the common `MediaItem`/`ActivityEntry`.
- `ProviderRegistry` resolves by id; adding a platform = new class, no other change.
- Mappers resolve activity items to the canonical catalog item (e.g. Letterboxd RSS carries the TMDB id).

## Sync
- `@Scheduled` job every 15 min picks `linked_accounts` with `last_synced_at` older than 6 h, bounded batch, per-provider rate limiter.
- Manual sync endpoint enforces the 15 min cooldown (BR-03).
- Each run is recorded in `sync_runs` (status, counts, error). Failures back off exponentially per account.
- Path to a queue later: job body already an idempotent `SyncAccountCommand`.

## Read model
Public profile is served from a pre-assembled view (profile + highlights + now) cached with Caffeine and evicted on any change; external APIs are never called while rendering.

## Billing flow
Checkout Session (Pix + card) → user pays → `checkout.session.completed` / `payment_intent.succeeded` webhook (signature verified, idempotent by event id) → `orders` paid → `entitlements` granted.

## Decisions
Recorded in [`docs/adr/`](adr/README.md) (0001 to 0012). Still open: 0013, room rendering technology, decided in epic E6.
