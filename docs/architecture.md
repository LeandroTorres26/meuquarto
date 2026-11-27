# Architecture

## Overview
```
Browser ── Next.js (TypeScript, SSR for /{slug} + Open Graph)
              │  REST/JSON (OpenAPI), session cookie
              ▼
        Spring Boot 4 (Java 21) — modular monolith
   ┌───────────┬────────────┬─────────────┬──────────┐
   │ identity  │ profile    │ media       │ room     │
   │ (auth)    │ (highlights│ (catalog,   │ (proto)  │
   │           │  now, theme│ providers,  │          │
   │           │  slug)     │ sync jobs)  │          │
   └───────────┴────────────┴─────────────┴──────────┘
        │ PostgreSQL (Flyway)      │ S3-compatible storage (R2/MinIO)
        │ cache (Caffeine)         │ external APIs: TMDB, AniList, Letterboxd RSS,
                                   │ Last.fm, MyAnimeList, Goodreads RSS, Open Library, IGDB, Deezer
```
Runs with Docker Compose (web, api, postgres, storage). Deploy is not built (issues #5, #6).

## Stack
Java 21, Spring Boot 4.x (3.x left open-source support), Maven, Spring Security, Spring Data JPA, Flyway, springdoc-openapi, Caffeine cache, Testcontainers. Front: Next.js 16 + TypeScript. Monorepo: `backend/`, `frontend/`, `docs/`, `infra/`.

## Modules and boundaries
Package by module (`com.meuquarto.<module>`), each with four light layers inside (`api`, `application`, `domain`, `infra`, ADR 0014). Other modules call only a module's `application` services; no module reads another's tables directly. Enforced with ArchUnit (#48).

## Provider design (OO showcase)
```java
interface CatalogProvider { SearchResult search(Category c, String q); MediaItem fetch(ExternalRef ref); }
interface ActivityProvider { Category category(); ProviderId id();
    void validate(String username);           // exists and public
    ActivitySnapshot fetch(String username);  // recent items + "now" candidate
}
```
- The RSS platforms share `RssActivityProvider` and the JSON ones share `JsonActivityProvider` (both abstract, ADR 0018).
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

## Decisions
Recorded in [`docs/adr/`](adr/README.md) (0001 and following). [0013](adr/0013-room-rendering-technology.md) chose CSS 3D in React for the room.
