# Architecture Decision Records

One file per decision, numbered, never rewritten: a changed decision gets a new ADR that supersedes the old one (and the old one's status is updated).

| # | Decision | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith over microservices | Accepted |
| [0002](0002-separate-nextjs-frontend.md) | Separate Next.js frontend | Accepted |
| [0003](0003-session-cookie-auth.md) | Session cookie over JWT | Accepted, refined by 0012 |
| [0004](0004-username-only-account-linking.md) | Link external accounts by public username, no OAuth | Accepted |
| [0005](0005-canonical-catalog-and-dedup.md) | Canonical catalog per category and dedup key | Accepted |
| [0006](0006-metadata-snapshot-image-hotlink.md) | Metadata snapshot, images by hotlink | Accepted |
| [0007](0007-scheduled-sync-with-cooldown.md) | Scheduled sync with manual cooldown, no queue | Accepted |
| [0008](0008-stripe-one-time-purchases.md) | Stripe, one-time purchases with Pix and card | Accepted |
| [0009](0009-s3-compatible-object-storage.md) | S3-compatible object storage | Accepted |
| [0010](0010-external-api-terms.md) | External API terms and licensing | Accepted, review pending |
| [0011](0011-collection-and-highlights.md) | Unlimited collection, limited highlights | Accepted |
| [0012](0012-authentication-and-cryptography.md) | Authentication and cryptography stack | Accepted |
| 0013 | Room rendering technology | To write in epic E6 |

## Template

```markdown
# NNNN. Title

- Status: Proposed | Accepted | Superseded by NNNN
- Date: YYYY-MM-DD

## Context
## Decision
## Consequences
## Alternatives considered
```
