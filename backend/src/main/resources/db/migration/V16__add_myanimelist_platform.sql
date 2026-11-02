-- MyAnimeList (ADR 0023, decision 4): a syncing platform linked by username. Its entries are stored as AniList
-- works (ADR 0005), so only the linked account needs the new value. provider is VARCHAR(16): MYANIMELIST
-- (11 characters) fits.
ALTER TABLE linked_accounts DROP CONSTRAINT linked_accounts_provider;
ALTER TABLE linked_accounts
    ADD CONSTRAINT linked_accounts_provider CHECK (provider IN (
        'LETTERBOXD', 'ANILIST', 'MYANIMELIST', 'STEAM', 'LASTFM', 'GOODREADS', 'BACKLOGGD', 'TRAKT', 'STORYGRAPH'));
