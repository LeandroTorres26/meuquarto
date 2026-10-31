import "server-only";
import { cookies, headers } from "next/headers";
import { apiUrl } from "@/lib/api";
import { apiRequestHeaders, trustProxyHeaders } from "@/lib/forwarded-headers";
import type { LinkedAccount } from "@/lib/linked-accounts";

/**
 * Server components: the signed-in owner's linked accounts (GET /api/linked-accounts), with the
 * session cookie and the visitor address forwarded as in getSession (ADR 0017). Null when the API
 * cannot answer, so the page can say so instead of showing an empty list that looks like "nothing linked".
 */
export async function fetchLinkedAccounts(): Promise<LinkedAccount[] | null> {
  const cookie = (await cookies()).toString();
  const forwarded = apiRequestHeaders(
    await headers(),
    trustProxyHeaders(process.env.TRUST_PROXY_HEADERS),
  );
  const address = forwarded.get("x-forwarded-for");
  try {
    const res = await fetch(`${apiUrl()}/api/linked-accounts`, {
      headers: {
        cookie,
        accept: "application/json",
        ...(address ? { "x-forwarded-for": address } : {}),
      },
      cache: "no-store",
      redirect: "manual",
      signal: AbortSignal.timeout(5000),
    });
    if (!res.ok) return null;
    return (await res.json()) as LinkedAccount[];
  } catch {
    return null;
  }
}
