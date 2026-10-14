# 0014. Module internal structure: light layers

- Status: Accepted (complements 0001)
- Date: 2026-10-15

## Context
ADR 0001 splits the backend vertically into business modules (`identity`, `profile`, `media`, `billing`, `room`). It did not say how code is organized inside a module. Options were light layers, one folder per use case, or hexagonal ports and adapters.

## Decision
Vertical between modules, light horizontal layers inside each module:

```
com.meuquarto.<module>/
  api/           REST controllers and request/response DTOs (the HTTP edge)
  application/   use cases / services: orchestrate, transactions, authorization checks
  domain/        entities, value objects and business rules (BR-xx), no web or HTTP types
  infra/         Spring Data repositories, external clients (providers, Stripe, mail, storage)
```

Rules:
- Dependencies point inward: `api` → `application` → `domain`; `infra` implements what `application` or `domain` needs. `domain` never imports `api`, `application` or `infra`.
- Business rules live in `domain` (methods on entities or small domain services) and are unit-tested without Spring.
- JPA annotations are allowed on domain entities (pragmatic choice; no separate persistence model).
- Controllers are thin: validate the request, call one application service, map the result. No rules in controllers.
- Another module may only call a module's `application` services and their DTOs; never its `domain` entities, repositories or `infra`. Enforced later by ArchUnit (#48).
- `shared` holds cross-cutting infrastructure (security config, error handling) with no business rules.

## Consequences
- Easy to explain and navigate: same four folders in every module.
- Rules are testable in isolation, which also serves the course's focus on business rules and OO.
- Some use cases touch several layers for small changes; accepted for clarity.
- Moving to hexagonal later (TCC) is incremental: `infra` already isolates external systems.

## Alternatives considered
- One folder per use case: strong isolation, but shared entities need their own package and the tree grows quickly.
- Hexagonal (ports and adapters): purest, but more interfaces and ceremony than the P2 deadline allows.
