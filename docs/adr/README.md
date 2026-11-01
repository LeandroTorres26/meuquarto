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
| [0014](0014-module-internal-structure.md) | Module internal structure: light layers | Accepted |
| [0015](0015-csp-and-rate-limiting.md) | CSP without nonce and in-memory rate limiting | Accepted |
| [0016](0016-same-origin-api-proxy.md) | Same-origin API through proxy.ts | Accepted |
| [0017](0017-real-client-address-behind-proxies.md) | Real client address behind the web proxy and Caddy | Accepted |
| [0018](0018-own-object-oriented-hierarchies.md) | Our own object-oriented hierarchies (course requirement) | Accepted |
| [0019](0019-shared-kernel-for-cross-module-vocabulary.md) | Shared kernel for cross-module vocabulary | Accepted |
| [0020](0020-media-reaches-profile-through-a-port.md) | Media reaches the profile through a port | Accepted |
| [0021](0021-local-s3-server-and-avatar-serving.md) | Local S3 server and avatar serving through the API | Accepted |

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
