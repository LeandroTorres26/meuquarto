// Shared by the server components and proxy.ts: asks the backend who the cookie belongs to.

export type Session = { email: string; slug: string | null };

/**
 * Returns the signed-in user, or null when the cookie is missing, expired or the API is down.
 * `clientAddress` is the already-validated client IP (forwarded-headers.ts), so the API counts this
 * call against the visitor and not against the web container (ADR 0017).
 */
export async function fetchSession(
  apiUrl: string,
  cookie: string | null,
  clientAddress: string | null = null,
): Promise<Session | null> {
  if (!cookie) return null;
  try {
    const res = await fetch(`${apiUrl}/api/auth/me`, {
      headers: {
        cookie,
        accept: "application/json",
        ...(clientAddress ? { "x-forwarded-for": clientAddress } : {}),
      },
      cache: "no-store",
      redirect: "manual",
      signal: AbortSignal.timeout(3000),
    });
    if (!res.ok) return null;
    const body: Partial<Session> = await res.json();
    return typeof body.email === "string" ? { email: body.email, slug: body.slug ?? null } : null;
  } catch {
    return null;
  }
}
