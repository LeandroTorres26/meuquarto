import type { NextConfig } from "next";

const isDev = process.env.NODE_ENV === "development";

// Static CSP, no nonce: the app uses Cache Components with partial prerendering, and the Next.js docs
// say a nonce needs fully dynamic rendering (ADR 0015). So script-src keeps 'unsafe-inline', which
// Next needs for its inline bootstrap and RSC payload scripts; every other source is locked down.
// 'unsafe-eval' is development only (React debugging); style-src 'unsafe-inline' is for the inline
// <style> tags Next emits. Provider covers are hotlinked over https (ADR 0006), hence img-src https:.
const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: https:",
  "font-src 'self'",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: contentSecurityPolicy },
  // Browsers only honor HSTS over HTTPS (Caddy terminates TLS); ignored on http://localhost.
  { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  output: "standalone",
  poweredByHeader: false,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
