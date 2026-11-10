-- The store and paid plans are out of the P2 (ADR 0024): nothing reads or writes these tables any more.
-- entitlements goes first because it points at products. IF EXISTS keeps this safe on any database that
-- ran V14 (all three tables exist, with no entitlements ever granted); the seeded catalog rows go with them.
-- profiles.theme_id has no foreign key to themes, so no profile is affected: the four themes are validated in code.
DROP TABLE IF EXISTS entitlements;
DROP TABLE IF EXISTS themes;
DROP TABLE IF EXISTS products;
