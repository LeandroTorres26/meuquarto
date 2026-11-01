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

## As built (#29)
- `SyncService` (media.application) runs one account: an atomic `UPDATE ... WHERE last_sync_started_at IS NULL OR <= now - 15 min` claims the run (so the cooldown also holds against two clicks or the scheduler), the provider is called outside any transaction, and the result is applied in one transaction. `SyncScheduler` ticks every 15 minutes (`meuquarto.sync.scheduler.interval`; switch off with `meuquarto.sync.scheduler.enabled=false`, which the tests do) and asks for a batch of 20 (`meuquarto.sync.batch-size`).
- Due = never synced or last success 6 h old or more, not backing off (`next_attempt_at`), not started in the last 15 minutes. Both `ACTIVE` and `ERROR` accounts qualify, otherwise a failed account would never recover. Oldest first, never-synced first.
- Backoff: after the n-th consecutive failure the next attempt is `15 min * 2^n` later, capped at 24 h; a success resets it. A manual sync ignores the backoff and respects only the cooldown.
- `POST /api/linked-accounts/{id}/sync` is synchronous (bounded by the provider timeouts) and answers `{status, itemsCount, error}`; a platform failure is a 200 with `FAILED`, the cooldown is a 429 `SYNC_COOLDOWN` with `Retry-After`. Moving it to the background later only changes `SyncService.syncNow`.
- Not done here: the per-provider rate limiter and response cache (NFR-03) belong to the concrete providers (#30 to #34), which own the HTTP clients; with a batch of 20 accounts per 15 minutes the engine itself cannot flood a platform.
