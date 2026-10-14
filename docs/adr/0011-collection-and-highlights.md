# 0011. Unlimited collection, limited highlights

- Status: Accepted
- Date: 2026-10-14

## Context
The first model had only highlights, limited to 4 (free) or 12 (pack) per category. The design showed counts like "40 na vitrola" and a "Ver todos" link, and the owner clarified that users can keep as many works as they want; the limit applies only to what the profile home shows.

## Decision
- Each category has a **collection** (`collection_items`) with no size limit, fed by sync (per-platform rules in BR-10) and by manual additions. The owner can hide synced items (they are not re-added) and remove manual ones.
- **Highlights** are collection items the owner marks "Destacar", ordered, limited by BR-02. They are the only items on the profile home; with none marked, the most recent items show.
- "Ver todos" opens the public listing `/{slug}/{categoria}` (S18) with search, sort and pagination.
- Notes belong to the collection item; `collection_item_sources` records why an item is there (manual or which linked account). Unlinking an account removes items that came only from it, except highlighted or noted ones, which become manual.

## Consequences
- The paid pack keeps its meaning (more highlights) without limiting what users keep.
- Collections can be large (Steam, AniList): the listing needs pagination and an index on (profile_id, category, last_activity_at).
- Sync now writes to the collection, not only to activity, so its rules (BR-10) need tests per provider.

## Alternatives considered
- Highlights only, with "Ver todos" showing the full highlight list: keeps the model small but contradicts how the owner wants the product to work.
- Sync items as pending suggestions to accept: more curation, but an extra state and screen in the MVP.
