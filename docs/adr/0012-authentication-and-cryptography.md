# 0012. Authentication and cryptography stack

- Status: Accepted (refines 0003)
- Date: 2026-10-15

## Context
ADR 0003 chose server sessions over JWT but left the password hash open and kept sessions in memory, which would sign everyone out on every deploy. There was also no password reset. The owner's rule: use proven, ready-made components; never write cryptography or authentication by hand.

## Decision
- **Authentication:** Spring Security only (no custom filters for credentials, no hand-made tokens). Login via a JSON endpoint backed by Spring Security's `AuthenticationManager`; session ID rotated on login; logout invalidates the session.
- **Passwords:** Argon2id via Spring Security's `Argon2PasswordEncoder` (parameters at or above the OWASP recommendation), wrapped in `DelegatingPasswordEncoder` so the algorithm can be upgraded later. Minimum length 8, maximum 128; checked against a list of common passwords.
- **Sessions:** Spring Session JDBC in PostgreSQL. Cookie `HttpOnly`, `Secure`, `SameSite=Lax`, scoped to the parent domain; idle timeout 30 days with "manter conectado", otherwise browser session.
- **CSRF:** Spring Security CSRF tokens (cookie-to-header pattern for the Next.js app).
- **Password reset:** "Esqueci minha senha" sends a single-use link. Token: 256 bits from `SecureRandom`, stored only as a SHA-256 hash, expires in 30 minutes, invalidated on use and on password change. The response is the same whether or not the e-mail exists. A reset ends every other session of that user.
- **E-mail:** transactional provider (Resend or Brevo) through Spring Mail/SMTP, with SPF, DKIM and DMARC on the domain.
- **Transport:** TLS 1.2/1.3 terminated by Caddy with automatic Let's Encrypt certificates and its default cipher suites; HSTS.
- **At rest:** no card data (Stripe); backups encrypted with restic (AES-256) and stored off the VPS. Secrets only in environment variables.

## Consequences
- Every security primitive comes from a maintained library or tool; our code only configures them.
- Deploys no longer sign users out; sessions live in two Spring Session tables.
- New dependency on an e-mail provider (account and DNS records by the owner).
- E-mail confirmation on sign up becomes easy to add later with the same token mechanism.

## Alternatives considered
- Keycloak: complete and ready, but a heavy extra server on a small VPS and one more system to secure.
- Managed auth (Auth0, Clerk, Supabase Auth): no auth code at all, but third-party dependency, pricing limits, and moves the security work the course evaluates out of the Java backend.
- BCrypt: also safe and the Spring default; Argon2id is the current OWASP first choice and costs nothing extra.
- In-memory or Redis sessions: in-memory loses sessions on deploy; Redis adds a service without need at this size.
