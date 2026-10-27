import "server-only";
import { cookies } from "next/headers";
import { apiUrl } from "@/lib/api";
import { fetchSession, type Session } from "@/lib/session-core";

/** Server components: the current user, from the incoming request cookies. Dynamic (reads cookies). */
export async function getSession(): Promise<Session | null> {
  const cookie = (await cookies()).toString();
  return fetchSession(apiUrl(), cookie || null);
}
