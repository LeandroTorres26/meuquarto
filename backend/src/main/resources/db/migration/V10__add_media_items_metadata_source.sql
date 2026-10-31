-- Who wrote the metadata of the row last (BR-04, ADR 0005): the canonical catalog name (TMDB, ANILIST...)
-- or the activity source that created the row (LETTERBOXD...). A canonical write always overwrites;
-- an activity source only fills the gaps of an existing row.
ALTER TABLE media_items ADD COLUMN metadata_source text;
UPDATE media_items SET metadata_source = canonical_provider;
ALTER TABLE media_items ALTER COLUMN metadata_source SET NOT NULL;
