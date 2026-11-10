# 0024. No store and no paid plans in the P2

- Status: Accepted
- Date: 2026-11-10
- Supersedes: 0008 (for the P2)
- Amends: 0011 (the highlight limit is 4 for everyone), 0018 (the `Plan` hierarchy is removed), 0022 (no locked theme)

## Context
ADR 0008 planned a store: premium themes and a highlight pack sold through Stripe Checkout (Pix and card), with entitlements granted by a verified webhook. The store catalog, the `entitlements` table, the `billing` module and the `Plan` hierarchy were built for it (#24, #36, #37), and the Stripe checkout and the pack were still to come (#38, #39).

The owner decided not to take the monetization challenge in the P2. The course grade does not depend on it, the professor will question the owner on every part of the project, and a payment flow adds a lot to explain (webhooks, idempotency, refunds, Pix being asynchronous, legal text) for no grade. It also adds security surface that has to be defended.

## Decision
The P2 has no store, no payments and no paid plans.
- The four themes (noite, dia, locadora, estufa) are free for everyone. The backend only checks that the theme id exists.
- Every highlight list holds 4 highlights, the same for everyone (BR-02). It stays a business rule, in `HighlightList` (`profile/domain`) with its own unit test; there is no pack and no upsell. The collection stays unlimited (ADR 0011).
- The `billing` module, the `Plan` hierarchy, the `entitlements`, `products` and `themes` tables (migration `V18`), the `STRIPE_*` settings and the store screens (S11, S12, S13) are removed from the P2.
- The store design is kept as a roadmap reference for the TCC: ADR 0008, FR-17 to FR-19, BR-05, BR-09, the prototypes `loja.html` and `compras.html`, and the Stripe rows of `docs/security.md`. If the store comes back, it starts from a new ADR.

## Consequences
- Less to build, test, secure and explain; five object-oriented hierarchies remain (ADR 0018).
- The profile theme picker has no locked state, no captions about price and no store link; the public profile logo is the same on every theme.
- The API contract shrinks: no `plan`, `premium`, `owned` or `locked` fields.
- Users lose nothing: nobody ever bought anything (no entitlement was ever granted), so dropping the tables loses no data.
- The legal pages no longer mention purchases or payment processors.

## Alternatives considered
- Keep the code and hide the screens: dead code that still has to be explained and kept secure.
- Build the store in test mode only: the cost is the same as building it, and it still has to be explained.
- Keep the `Plan` hierarchy with only a free plan: a hierarchy with one subclass shows nothing of polymorphism and would be harder to defend than a plain constant.
