# 0023. Every platform is a profile link; sync where the platform allows

- Status: Proposed (layout waiting for the owner's approval)
- Date: 2026-11-02
- Amends: 0004 (linking by username stays), 0010 (MyAnimeList row)

## Context
The product's premise is that people keep using the platforms they already use, and the profile works as a hub: it shows their favourite works and also links to their profiles on those platforms, like a link-in-bio page. Three things did not match it:
- The public profile never linked to the linked accounts, so the hub part was missing.
- Platforms behaved differently in the panel: some synced, some were "Em breve", and a card promised a CSV import. The owner rejected manual file exports as the main path, because making people export their data breaks the premise, and asked for one consistent model.
- MyAnimeList, the main anime platform for the owner and in the briefing, was left for the roadmap.

What each platform allows, checked on 2026-11-02:
- Letterboxd: the API is by request and closed to projects like this one; the public RSS carries only diary entries and reviews (about 50 most recent). Films marked as watched outside the diary are not in any official feed, and scraping is out (ADR 0010).
- MyAnimeList: API v2 reads public lists with a client ID (`X-MAL-CLIENT-ID`); the API agreement treats an app with paid features as commercial (written authorization needed before charging) and asks apps not to keep users' content on their servers.
- AniList, Last.fm, Steam, Goodreads: as in ADR 0004 and 0010.
- Backloggd, StoryGraph: no public API for user lists. Trakt has a public API, but reading it is not planned for P2.

## Decision
1. **One model for every platform.** Linking takes a public username (ADR 0004 stays: no OAuth, no proof of ownership, as the owner decided on 2026-11-02). Every linked account becomes a link to that profile on the public profile (S4), built from the username and a fixed URL template per platform; a URL typed by the user is never stored or shown.
2. **Sync is a capability of the platform, not a different kind of account.** Platforms with a readable list also update the collection on their own (BR-10); the others are "Só o link". S9 shows both in the same card (head, body, note, action rows); the note says which one it is and what enters the collection, with the limits stated plainly (Letterboxd: only the diary).
3. **No file import as the main path.** The CSV card is removed from S9. Importing exports may come back later only as an optional way to bring old history.
4. **MyAnimeList is added before the store**, linked by username and read with the project's client ID (`MAL_CLIENT_ID`). Its entries are mapped to the AniList catalog through AniList's `idMal`, so a user with both accounts sees each work once and no MyAnimeList metadata is kept; only the AniList work and the list status. Before real charges, ask MyAnimeList for written authorization (recorded in ADR 0010).
5. **Link-only platforms at launch:** Backloggd (games), Trakt (series and films) and StoryGraph (books). Adding one is a name, a category list, a URL template and its mark; it needs no provider class.
6. **Platform marks in their own colors.** Links and S9 cards show the platform's mark on a small tile in the platform's colors (marks from Simple Icons, CC0; the marks belong to their owners and only name the platform a link goes to). The owner chose color so each platform keeps its identity; the tile keeps dark brands visible on every theme. A platform without a mark uses its category icon on its category color.
7. **Item links go to the work, not a search** (FR-10a): Letterboxd through `letterboxd.com/tmdb/{id}`, MyAnimeList through AniList's `idMal`, the catalogs by their ids.

## Consequences
- The profile becomes the hub the briefing describes, for every viewer.
- The user sees one rule in S9: link your profiles; what can update by itself, does.
- Letterboxd history before linking is still not brought automatically; the copy says so instead of promising it.
- A new link-only platform is configuration; a new synced platform is still one `ActivityProvider` (ADR 0018).
- MyAnimeList depends on a revocable client ID and on authorization before monetization.

## Alternatives considered
- File import for every platform, integrations only as links: no automatic updates, no "Agora", and Steam and Last.fm have no user export at all.
- OAuth for AniList and MyAnimeList with proof of ownership: safer, declined for now (ADR 0004 stays).
- Reading Letterboxd's watched films from the profile pages: scraping, against their terms and ADR 0010.
