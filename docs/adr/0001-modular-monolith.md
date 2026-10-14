# 0001. Modular monolith over microservices

- Status: Accepted
- Date: 2026-10-12

## Context
One developer (with an agent), a 7-week deadline for P2 and a single small VPS, but the project may grow into a TCC and must not need a rewrite to scale.

## Decision
One Spring Boot application split into modules by package (`identity`, `profile`, `media`, `billing`, `room`). Each module exposes a small public API and owns its tables; no module reads another's tables or repositories. Module rules can later be enforced with ArchUnit.

## Consequences
- One build, one deploy, one database, simple local setup and debugging.
- Boundaries are kept by convention and review, so they can erode; ArchUnit tests are the mitigation.
- A module (e.g. `media` sync) can be extracted into its own service later because its interface is already explicit.

## Alternatives considered
- Microservices: network, deploy and observability cost far above what the scope needs.
- Layered monolith without modules: fastest start, but couples features and makes the TCC growth path harder.
