# 0017. Real client address behind the web proxy and Caddy

- Status: Accepted
- Date: 2026-10-27

## Context
Since ADR 0016 every browser request reaches the API through the Next.js proxy, so the API's socket peer is the web container. The per-IP limits (ADR 0015) and the per-account+address login throttle used `getRemoteAddr()` and therefore acted site-wide (for example 10 sign-ups per hour for everyone). Forwarding the client address fixes that, but a forwarded header is only as trustworthy as the hop that wrote it: a client can send any `X-Forwarded-For`, and picking its own value means picking its own rate-limit bucket.

What Next.js 16 gives us (checked in `next/dist`, not only the docs):
- `NextRequest.ip` and `geo` no longer exist (removed in 15). `proxy.ts` has no access to the socket peer address.
- Before `proxy.ts` runs, Next fills `x-forwarded-for` with the socket address only when the header is absent (`??=`), so a browser-supplied value is kept and cannot be told apart from a real one.
- The external rewrite (httpxy) copies the request headers, does not append to `x-forwarded-for` (no `xfwd`), and always overwrites `x-forwarded-host` with the request `Host`.
- `NextResponse.rewrite(url, { request: { headers } })` replaces the headers sent upstream, and removing a header there removes it from the proxied request (verified with a local echo server).

## Decision
Trust is explicit at both hops.

1. **API trusts forwarded headers only from private peers.** `server.forward-headers-strategy: native` (Tomcat `RemoteIpValve`) with `server.tomcat.remoteip.remote-ip-header=X-Forwarded-For`, `protocol-header=X-Forwarded-Proto` and `internal-proxies=10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, ::1/128`. These are the Docker network ranges and loopback; Boot's default list is wider (CGNAT, link-local), so it is set explicitly. If the direct peer is not in the list, the valve ignores every `X-Forwarded-*` header. This is safe because the API port is never public: compose binds it to `127.0.0.1` and production publishes only Caddy. The valve skips trusted hops from the right, so extra entries a client prepends to the chain do not change the result. Both the `RateLimitFilter` and the login throttle (`AuthController`) keep calling `getRemoteAddr()`, which the valve now fills.
2. **The Next proxy rebuilds the headers, it never passes them through.** `src/lib/forwarded-headers.ts` (pure, unit tested) removes `x-forwarded-for/proto/port/host`, `forwarded` and `x-real-ip` from the incoming request. Only when `TRUST_PROXY_HEADERS=true` it sets back one `x-forwarded-for` (the right-most entry, which must be a valid IP) and `x-forwarded-proto` (`http` or `https` only). The same single address is sent on the `GET /api/auth/me` session checks made by `proxy.ts` and by server components, so those are counted per visitor and not against the web container.
3. **`TRUST_PROXY_HEADERS` defaults to false.** Because `proxy.ts` cannot read the peer address, the only safe source of the client IP is a reverse proxy that overwrites `X-Forwarded-For`. In production Caddy does (its `reverse_proxy` replaces the header from untrusted clients with the address it saw) and the flag is `true`. With the flag off (plain local compose) the API sees the web container as the client, which is the safe failure: limits become shared, never bypassable.
4. The right-most entry is used instead of the left-most: with one trusted hop it is the address that hop observed, while everything to its left is client-controlled.
5. `x-forwarded-host` is still set by Next from the request `Host`, never from a client-supplied `x-forwarded-host`; the API uses it only for the server name, and nothing security relevant depends on it.

## Consequences
- Limits are per visitor again in production, and a client cannot choose its bucket by sending headers to the web app or to the API.
- Setting `TRUST_PROXY_HEADERS=true` while the web port is reachable directly from the Internet (no Caddy in front) would let clients choose their address. The flag is for production behind Caddy only; compose keeps the port on `127.0.0.1`.
- Another private-network proxy between Caddy and the API is covered by the ranges; a proxy outside them requires extending `internal-proxies`.
- IPv4-mapped IPv6 peers (`::ffff:a.b.c.d`) can appear as the address when no `x-forwarded-for` arrived; that is a separate bucket string for the same host, harmless.

## Alternatives considered
- Left-most `X-Forwarded-For`: client controlled, rejected.
- Trusting `X-Forwarded-For` in the API from any peer: lets a client rotate the header to bypass every limit.
- Trusting the incoming header in `proxy.ts` unconditionally: same problem when the web port is reachable without Caddy.
- A custom Next server or route handler to read `socket.remoteAddress`: re-implements the proxy by hand (see ADR 0016) for a case Caddy already solves.
