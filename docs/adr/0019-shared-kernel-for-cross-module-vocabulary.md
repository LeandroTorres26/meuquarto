# 0019. Shared kernel for cross-module vocabulary

- Status: Accepted (complements 0001 and 0014)
- Date: 2026-10-27

## Context
`MediaCategory` (MOVIE, SERIES, ANIME, MANGA, BOOK, GAME, MUSIC) started in `media/domain`. The collection (#23) lives in `profile`, and highlights, "Agora" and sync will use it too. Under ADR 0001 and 0014 a module may not import another module's `domain`, so each module would need its own copy of the enum, or `media` would have to leak its domain.

## Decision
`com.meuquarto.shared.domain` is a small **shared kernel**: vocabulary that several modules must agree on.

May live there:
- Enums and immutable value types with no behavior tied to one module (`MediaCategory`, `ErrorKind`).
- The base types of our own hierarchies that every module extends (`DomainException`, `ValidationRule`, `LengthRule`).

May not live there:
- Entities, repositories, services or anything with persistence or Spring annotations.
- Business rules (BR-xx) of a single module, even if another module reads the result.
- A type only because two modules "might" share it later. It moves in when the second module really needs it.

ArchUnit keeps the direction: `shared` depends on no business module, and every module may depend on `shared`.

## Consequences
- `profile`, `media`, `identity` and future modules use the same `MediaCategory` without touching each other's domain.
- Adding a category is one change in one place (plus the `CHECK` constraints of the tables that store it).
- The kernel is a place where growth must be watched in review: new members need a reason in the PR.

## Alternatives considered
- Keep it in `media.domain` and make `profile` import it: breaks the module boundary rule.
- One copy per module with mapping code: duplicates vocabulary and invites drift.
- A separate Maven module for the kernel: heavier than a package at this size.
