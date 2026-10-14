# 0008. Stripe, one-time purchases with Pix and card

- Status: Accepted
- Date: 2026-10-12

## Context
The product sells cosmetics (themes) and a pack that raises the highlight limit from 4 to 12. The Brazilian audience expects Pix. The owner has an MEI. The optional SaaS challenge needs real revenue.

## Decision
Stripe Checkout (hosted) with Pix and card, one-time payments only. The order is created before redirecting; the entitlement is granted only after a verified webhook (`checkout.session.completed` / async payment succeeded), with signature check and idempotency by event id (BR-09). Test mode until the owner's Stripe account is activated with Pix.

## Consequences
- No card data touches our servers (PCI scope stays with Stripe).
- Pix confirmation is asynchronous; the return page (S12) waits for the webhook.
- Subscriptions (a recurring Pro plan) are roadmap.
- Pending: confirm Pix is enabled on the owner's Stripe account.

## Alternatives considered
- Mercado Pago or Asaas: strong in Brazil, but weaker Java SDK and docs.
- Subscriptions now: more lifecycle states (renewal, failure, cancellation) than the deadline allows.
