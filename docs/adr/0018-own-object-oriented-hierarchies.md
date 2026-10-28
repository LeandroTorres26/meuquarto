# 0018. Our own object-oriented hierarchies

- Status: Accepted (complements 0014)
- Date: 2026-10-27

## Context
meuquarto is the P2 project of the "Programação Orientada a Objetos" course (Fatec Baixada Santista). The professor requires the course content applied in the project with the students' **own code**: inheritance, abstract classes, interfaces and subtype polymorphism. He said explicitly that Spring Boot features do not count: implementing `UserDetails`, extending `JpaRepository`, annotations and the like are framework mechanics, not our design. On delivery day he asks questions, so the owner must be able to explain every hierarchy line by line.

Before this decision the project had almost no hierarchies of its own: error handling used one handler per exception class, validation was a chain of `if`s, and the provider design in `docs/architecture.md` was only a sketch.

## Decision
The project has five hierarchies, designed to be small, readable and written in plain Java.

**Rule:** a hierarchy of ours is plain Java. Domain hierarchies live in `domain` (or `shared/domain` when several modules use them) and import nothing from Spring, JPA or any framework. The providers in `media/infra` may use an HTTP client to do their work, but the class hierarchy itself (interface, abstract class, subclasses) is ours: no framework base class counts as one of the five. Code that uses a hierarchy iterates it through the base type and never asks "which class is this?" (no `instanceof` chains): that is the polymorphism the course teaches (the PetShop of Aula 9).

### 1. ActivityProvider (planned, issues #28 to #34)
```
                interface ActivityProvider
                           ^
          +----------------+-----------------+------------------+
          |                |                 |                  |
 abstract RssActivityProvider   AniListProvider   SteamProvider   LastFmProvider
     ^              ^
     |              |
final LetterboxdProvider   final GoodreadsProvider
```
`RssActivityProvider` holds what the two RSS platforms share (fetch the feed with a timeout, parse the XML safely, build the snapshot); each leaf only says which feed URL and how an entry becomes an `ActivityEntry`. The other three providers talk to JSON APIs and implement the interface directly. `SyncService` receives a `List<ActivityProvider>` (through the registry) and calls the same methods on all of them: adding a platform is a new class, no change in `SyncService`.

### 2. CatalogProvider (planned, issues #21 and #22)
```
                interface CatalogProvider
                           ^
                abstract HttpCatalogProvider
     (template method: timeout, cache, error handling)
                           ^
   +-----------+-----------+-----------+-------------+
   |           |           |           |             |
TmdbCatalog AniListCatalog OpenLibraryCatalog IgdbCatalog DeezerCatalog
```
`HttpCatalogProvider.search(...)` is the template: it applies the cache, the timeout and the "one provider down never breaks the others" handling, and calls abstract steps (`buildRequest`, `parseResults`) that each leaf implements.

### 3. Plan (implemented in #24; the pack is wired in #39)
```
        interface Plan
        (code(), highlightLimit(), canApply(ThemeTier),
         default hasRoomFor(count))
             ^
      +------+------+
      |             |
  FreePlan      PackPlan
 (4 highlights, (12 highlights,
  free themes)   owned themes)
```
Replaces `if (hasPack) ... else ...` for BR-02 (highlight limits) and BR-05 (premium themes need ownership). The profile asks its `Plan`; it does not test for the pack itself. As built in #24: `canApply` takes a `ThemeTier` (`FREE` or `PREMIUM`) because themes do not exist yet (#37), and `hasRoomFor(count)` is a `default` method on the interface. The classes live in `profile/domain`; an application-level `PlanResolver` decides which object to hand out (everybody is `FreePlan` until entitlements exist in #39), which is the only place that decides who has what.

### 4. ValidationRule (implemented in this PR)
```
      interface ValidationRule<T, P>
      (check, default accepts, static firstProblem)
             ^                     ^
  abstract LengthRule<T,P>         |
  (template: min/max)              |
        ^          ^               +--- SlugCharactersRule, SlugHyphenRule, SlugReservedRule
        |          |               +--- CommonPasswordRule, PasswordEqualsEmailRule
final SlugLengthRule  final PasswordLengthRule
```
`ValidationRule<T, P>` has two type parameters, like `Dupla<S, W>` from Aula 7: the value checked (`String`, `PasswordCandidate`) and the problem reported (`Slug.Problem`, `AccountRuleException.Rule`). `LengthRule` is abstract because it knows the algorithm (below min, above max) but not how to measure the value nor which problem to report; each subclass supplies those through `protected` methods. `Slug.check` and `PasswordPolicy.check` keep a `List<ValidationRule<...>>` and ask each rule in order through `ValidationRule.firstProblem`, which is the PetShop loop. Problem codes (`Slug.Problem`, `AccountRuleException.Rule`) are unchanged.

### 5. DomainException (implemented in this PR)
```
        abstract DomainException extends RuntimeException
        (code(), kind(), retryAfter() default empty)
             ^
   +---------+------------+--------------------+-------------------------+
   |                      |                    |                         |
final AccountRuleException  final InvalidSlugException  final SlugUnavailableException  final TooManyAttemptsException
```
The code and the kind (our own enum `ErrorKind { INVALID, CONFLICT, TOO_MANY }`) come from the constructor, so each subclass calls `super(code, kind)`. A single handler in the api layer (`shared/api/DomainErrors`) catches `DomainException`, maps `ErrorKind` to the HTTP status and answers `{"error": e.code()}`. `TooManyAttemptsException` overrides `retryAfter()` and the handler adds `Retry-After` without testing the class. The domain never sees HTTP.

**Why a constructor and not an abstract `code()`:** with `super(code, kind)` each subclass states its values once, in the constructor, and the compiler forces it to (the constructor is `protected` and has no default). An abstract method would force every subclass to also implement `code()` and `kind()` just to return a constant. Abstract methods are still shown, where they carry real work, in `LengthRule` (`lengthOf`, `tooShort`, `tooLong`). `DomainException` is abstract anyway because a generic "domain error" has no meaning: you always throw a specific one.

### Optional later: RoomObject (issue #43)
```
          abstract RoomObject
                  ^
     +------+-----+------+--------+
     |      |            |        |
  Poster  Shelf     RecordPlayer   Tv
```
Each object in the room knows how to place itself and what happens on click; the scene iterates `List<RoomObject>`.

## Consequences
- Every course topic (inheritance, `super(...)`, `protected`, `@Override`, abstract class, `final`, interface with `default` and `static`, generics, polymorphism) has a real, small example in the project, with a study guide: `docs/poo-no-projeto.md`.
- Behavior is unchanged: error codes, HTTP statuses and `Retry-After` stay as they were (existing API tests verify them). `InvalidSlugException` now extends `DomainException` instead of `IllegalArgumentException`; nothing caught the old type.
- More small classes (one per rule). Accepted: each is trivial to read and to test without Spring.
- The ArchUnit rules stay green: `shared` depends on no module, `domain` depends on no other layer. The nested exceptions `Accounts.TooManyAttemptsException` and `ProfileAccounts.SlugUnavailableException` became top-level classes in their `application` packages.
- Features that match a hierarchy must follow this design (see `CLAUDE.md`), and `docs/poo-no-projeto.md` is updated in the same PR whenever a hierarchy is added or changed.

## Alternatives considered
- Keep one handler per exception and `if`s for validation: works, but shows nothing of the course content.
- Use framework hierarchies (Spring `UserDetails`, `JpaRepository`, `ResponseStatusException`): the professor excluded them explicitly.
- A deeper hierarchy (for example `AccountException` between `DomainException` and its subclasses): more levels without new behavior; harder to explain.
