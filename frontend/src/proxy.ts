import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { apiRequestHeaders, trustProxyHeaders } from "@/lib/forwarded-headers";
import { fetchSession } from "@/lib/session-core";

// Runtime API address (read on every request, so one Docker image works in any environment).
const apiUrl = () => process.env.API_URL ?? "http://localhost:8080";

const AUTH_PAGES = new Set(["/entrar", "/cadastro"]);

export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  // Same-origin API: the browser only talks to this origin; the request goes on to the backend with
  // its method, body, cookies and headers, and the response (including every Set-Cookie) comes back.
  const forwarded = apiRequestHeaders(
    request.headers,
    trustProxyHeaders(process.env.TRUST_PROXY_HEADERS),
  );
  if (pathname === "/api" || pathname.startsWith("/api/")) {
    // Forwarding headers are rebuilt here, never passed through (ADR 0017): the API takes the client
    // address from X-Forwarded-For and a browser could otherwise choose its own rate-limit bucket.
    return NextResponse.rewrite(new URL(pathname + search, apiUrl()), {
      request: { headers: forwarded },
    });
  }

  // Real HTTP redirects for the session gates (a redirect() inside a Suspense boundary would only
  // be a client-side redirect on a 200 response).
  const session = await fetchSession(
    apiUrl(),
    request.headers.get("cookie"),
    forwarded.get("x-forwarded-for"),
  );
  if (AUTH_PAGES.has(pathname) && session) {
    return NextResponse.redirect(new URL("/painel/conta", request.url));
  }
  if (pathname.startsWith("/painel") && !session) {
    return NextResponse.redirect(new URL("/entrar", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/api/:path*", "/entrar", "/cadastro", "/painel/:path*"],
};
