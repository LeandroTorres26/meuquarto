# 0007. Scheduled sync with manual cooldown, no queue

- Status: Accepted
- Date: 2026-10-12

## Context
Linked accounts must update on their own ("Agora", collection), and users want a "Sincronizar agora" button. External APIs have rate limits. Volume in P2 is small; the VPS is small.

## Decision
A Spring `@Scheduled` job runs every 15 minutes and picks a bounded batch of linked accounts whose last sync is older than 6 hours. A manual sync is allowed once every 15 minutes per account (BR-03). Each run is recorded in `sync_runs`; failures back off exponentially per account. Each provider has its own rate limiter. The job body is an idempotent `SyncAccountCommand`.

## Consequences
- No broker to run or monitor on the VPS.
- Single-instance scheduling; with more instances it needs a lock (ShedLock) or a move to a queue.
- Because the command is idempotent and self-contained, moving it onto a queue later changes only who calls it.

## Alternatives considered
- Queue (RabbitMQ or Redis) from day one: more infra and complexity than P2 needs.
- Manual sync only: loses the "updates by itself" differentiator.
