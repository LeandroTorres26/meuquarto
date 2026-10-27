import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { fetchSession } from "@/lib/session-core";

// Runtime API address (read on every request, so one Docker image works in any environment).
const apiUrl = () => process.env.API_URL ?? "http://localhost:8080";

const AUTH_PAGES = new Set(["/entrar", "/cadastro"]);

export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  // Same-origin API: the browser only talks to this origin; the request goes on to the backend with
  // its method, body, cookies and headers, and the response (including every Set-Cookie) comes back.
  if (pathname === "/api" || pathname.startsWith("/api/")) {
    return NextResponse.rewrite(new URL(pathname + search, apiUrl()));
  }

  // Real HTTP redirects for the session gates (a redirect() inside a Suspense boundary would only
  // be a client-side redirect on a 200 response).
  const session = await fetchSession(apiUrl(), request.headers.get("cookie"));
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
