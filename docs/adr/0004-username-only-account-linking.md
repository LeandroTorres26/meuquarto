# 0004. Link external accounts by public username, no OAuth

- Status: Accepted
- Date: 2026-10-12

## Context
Five platforms are synced: Letterboxd (RSS), AniList (GraphQL), Last.fm, Steam Web API and Goodreads (RSS). Letterboxd and Goodreads have no OAuth for this use; the others do, but the data needed is public.

## Decision
The user links an account by typing its public username (or Steam vanity URL / id). The app validates that the account exists and is public, and stores only the username. No OAuth tokens are stored.

## Consequences
- One uniform flow for every platform; no token storage, refresh or encryption.
- Anyone could link someone else's public profile. Accepted risk: the data is already public. Proof of ownership (code in bio) and OAuth are roadmap.
- Private profiles cannot be synced; the UI explains that the profile must be public.

## Alternatives considered
- OAuth where available: more secure, but a different flow per platform and tokens to protect.
- Verification code in the platform bio: not every platform has an editable bio reachable by API.
