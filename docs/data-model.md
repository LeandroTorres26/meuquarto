# Data model (draft v1)

Categories enum: `MOVIE, SERIES, ANIME, MANGA, BOOK, GAME, MUSIC`. Providers enum: `TMDB, ANILIST, OPEN_LIBRARY, IGDB, DEEZER, LETTERBOXD, LASTFM, STEAM, GOODREADS`.

| Table | Key columns | Notes |
|---|---|---|
| `users` | id, email (unique), password_hash, created_at | identity |
| `password_reset_tokens` | id, user_id, token_hash (SHA-256), expires_at, used_at | single-use, 30 min (ADR 0012) |
| `spring_session`, `spring_session_attributes` | managed by Spring Session JDBC | created by its schema script through a Flyway migration |
| `profiles` | id, user_id (unique FK), slug (unique), display_name, bio, avatar_key, theme_id, created_at | 1:1 with user |
| `media_items` | id, category, canonical_provider, external_id, title, year, image_url, adult, metadata_json, fetched_at | unique (category, canonical_provider, external_id); snapshot (BR-04). `metadata_json` is not created yet: it arrives in the migration of the first feature that needs it (V5 has the other columns) |
| `media_external_ids` | media_item_id, provider, external_id | maps Steam/Letterboxd ids to the canonical item; primary key (provider, external_id) |
| `collection_items` | id, profile_id, media_item_id, category, note, hidden, added_at, last_activity_at | the owner's collection, no limit; unique (profile_id, media_item_id) (BR-07); `hidden` blocks re-adding by sync |
| `collection_item_sources` | collection_item_id, source (MANUAL / provider), linked_account_id (nullable), first_seen_at | why an item is in the collection; drives unlink (FR-16) and the S17 origin |
| `highlight_lists` | id, profile_id, category (nullable = "Favoritos de tudo") | unique (profile_id, category) `NULLS NOT DISTINCT`, so there is one favorites list per profile; created on first use |
| `highlights` | id, list_id, collection_item_id, position | the subset shown on the profile home; unique (list_id, collection_item_id); count limited by BR-02 (a domain rule, from the user's `Plan`); positions are 0..n-1; deleting the collection item cascades, hiding removes it in the domain |
| `linked_accounts` | id, profile_id, provider (LETTERBOXD, ANILIST, STEAM, LASTFM, GOODREADS), username, status (ACTIVE, ERROR), last_synced_at, last_error, created_at | unique (profile_id, provider); profile deletion cascades; no token is stored (ADR 0004) |
| `synced_entries` | id, linked_account_id, media_item_id, activity_type, occurred_at | recent activity from providers |
| `now_entries` | id, profile_id, category, media_item_id, source (AUTO/MANUAL), hidden | one per (profile, category) (BR-06) |
| `sync_runs` | id, linked_account_id, started_at, finished_at, status, items_count, error | observability |
| `products` | id, sku, name, type (THEME/PACK), price_cents, active, stripe_price_ref | seeded by Flyway |
| `themes` | id, product_id (nullable = free), name, tokens_json | `profiles.theme_id` |
| `orders` | id, user_id, product_id, amount_cents, status, stripe_session_id, paid_at | financial record kept on account deletion, anonymized |
| `entitlements` | id, user_id, product_id, granted_at, order_id | drives BR-02 and BR-05 |
| `stripe_events` | event_id (PK), type, processed_at | webhook idempotency (BR-09) |
| `room_items` | id, code, category_binding, asset_ref | prototype catalog (seed) |
| `room_layouts` | id, profile_id, layout_json | prototype only |

Notes
- Free plan limits derive from `entitlements` (pack owned → 12, else 4). They apply to `highlights` only; `collection_items` is unlimited.
- The public listing (S18) reads `collection_items` where `hidden = false`, paginated, with an index on (profile_id, category, last_activity_at).
- `synced_entries` keeps the raw activity (dates, "assistido em"); sync upserts `collection_items` from it following BR-10.
- `collection_items` is written only through `ProfileCollection` (profile module); it points to `media_items` by id and never copies metadata. `note` is at most 140 characters of plain text. `collection_item_sources.linked_account_id` references `linked_accounts` (`ON DELETE SET NULL`, a safety net: unlinking releases the items first, FR-16; V8). Primary key of the sources table is (collection_item_id, source).
- Account deletion cascades to profile, highlights, linked accounts, entries; `orders` rows are anonymized (user_id nulled).
- Deleting a profile doesn't delete shared `media_items` (they are a global cache).
- Entities: 19 tables plus the 2 Spring Session tables (requirement from the professor: ≥ 3).
