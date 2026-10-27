import "server-only";
import { cookies, headers } from "next/headers";
import { apiUrl } from "@/lib/api";
import { apiRequestHeaders, trustProxyHeaders } from "@/lib/forwarded-headers";
import { fetchSession, type Session } from "@/lib/session-core";

/** Server components: the current user, from the incoming request cookies. Dynamic (reads cookies). */
export async function getSession(): Promise<Session | null> {
  const cookie = (await cookies()).toString();
  const forwarded = apiRequestHeaders(
    await headers(),
    trustProxyHeaders(process.env.TRUST_PROXY_HEADERS),
  );
  return fetchSession(apiUrl(), cookie || null, forwarded.get("x-forwarded-for"));
}
