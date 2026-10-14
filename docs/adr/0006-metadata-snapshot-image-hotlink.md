# 0006. Metadata snapshot, images by hotlink

- Status: Accepted
- Date: 2026-10-12

## Context
Profiles must render fast (p95 < 500 ms) and never depend on external APIs being up. Hosting third-party images raises cost and licensing risk (TMDB and others).

## Decision
When an item is added or synced, its metadata (title, year, subtitle, adult flag, image URL, provider ids) is saved in `media_items`. Profile pages read only local data plus an in-memory cache. Images are loaded from the provider's CDN URL (hotlink), not copied to our storage. A background refresh updates stale snapshots.

## Consequences
- Profiles keep working when a provider is down; only search and sync are affected.
- A cover can break if the provider changes its URL; the refresh job and a placeholder at the real aspect ratio cover it.
- Snapshot data may be slightly outdated, which is acceptable for this content.

## Alternatives considered
- Always fetch live with a cache: slow cold paths and outages propagate to profiles.
- Copy images into object storage: more robust, but storage cost and stronger licensing exposure.
