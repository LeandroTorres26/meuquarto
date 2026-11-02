-- Link-only platforms (ADR 0023): Backloggd, Trakt and StoryGraph are linked by username and shown as links on
-- the public profile, but their lists are never read, so they never enter the sync. MyAnimeList comes with its
-- own migration. provider is VARCHAR(16): STORYGRAPH (10 characters) fits.
ALTER TABLE linked_accounts DROP CONSTRAINT linked_accounts_provider;
ALTER TABLE linked_accounts
    ADD CONSTRAINT linked_accounts_provider CHECK (provider IN (
        'LETTERBOXD', 'ANILIST', 'STEAM', 'LASTFM', 'GOODREADS', 'BACKLOGGD', 'TRAKT', 'STORYGRAPH'));
