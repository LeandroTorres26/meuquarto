# 0020. Media reaches the profile through a port

- Status: Accepted (complements 0001 and 0014)
- Date: 2026-10-28

## Context
Linked accounts (#28) and sync (#29) live in the `media` module, but they need two things that belong to `profile`: the profile of the signed-in user, and the collection (unlinking an account releases its items, FR-16; sync writes items, BR-10). `profile` already depends on `media` (the collection snapshots works through `MediaSnapshots`). If `media` also called `ProfileCollection`, the modules would form a cycle, which the ArchUnit rule "modules are free of cycles" forbids.

## Decision
`media.application` declares a small interface, `AccountOwners` (`profileIdOf(userId)`, `releaseCollectionItems(profileId, linkedAccountId)`). `profile.application` implements it (`ProfileAccountOwners`, delegating to `ProfileCollection`). `media` calls the interface; Spring injects the implementation. The dependency arrow stays `profile -> media`.

Sync (#29) follows the same pattern for writing items: a port in `media.application`, implemented by `profile.application` on top of `ProfileCollection.recordSyncedItem`.

## Consequences
- No cycle, no change to the ArchUnit rules, and `media` can be tested with a fake `AccountOwners`.
- One more small interface per thing `media` needs from `profile`. Accepted: each is a few lines and states exactly what crosses the boundary.
- The unlink runs in one transaction that spans both modules (the port call joins the caller's transaction), so the collection and the account row change together or not at all.

## Alternatives considered
- `media` calls `ProfileCollection` directly: creates the cycle.
- Move linked accounts and sync into `profile`: breaks the ownership in ADR 0001 (providers, sync and linked accounts are the media module's job) and puts HTTP clients in the module that should only read local data.
- Application events (`UnlinkedEvent`): decouples more but makes the FR-16 transaction asynchronous or implicit; harder to explain and to test.
