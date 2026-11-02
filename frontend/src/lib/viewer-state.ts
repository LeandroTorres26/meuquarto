import type { Session } from "@/lib/session-core";

/**
 * Who is looking at a public screen (docs/screens.md, "Session states"):
 * - `visitor`: no session.
 * - `other`: signed in, on a page that is not their own profile (or on a page with no owner).
 * - `own`: signed in, on their own profile.
 */
export type ViewerState = "visitor" | "other" | "own";

/** `pageSlug` is the profile the page belongs to, or null/undefined for pages with no owner (landing, legal, 404). */
export function viewerState(
  pageSlug: string | null | undefined,
  session: Pick<Session, "slug"> | null,
): ViewerState {
  if (!session) return "visitor";
  if (pageSlug && session.slug && pageSlug.toLowerCase() === session.slug.toLowerCase()) {
    return "own";
  }
  return "other";
}

export const PANEL_HREF = "/painel";
export const HIGHLIGHTS_HREF = "/painel/destaques";
export const ACCOUNTS_HREF = "/painel/contas";

/** Address of a profile page. */
export function profileHref(slug: string): string {
  return `/${encodeURIComponent(slug)}`;
}
