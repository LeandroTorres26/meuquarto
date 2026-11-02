import "server-only";
import { cookies, headers } from "next/headers";
import { apiUrl } from "@/lib/api";
import { collectionQuery, type CollectionPage, type HighlightOverview } from "@/lib/collection";
import { apiRequestHeaders, trustProxyHeaders } from "@/lib/forwarded-headers";
import type { NowEntry } from "@/lib/now";
import type { OwnProfile } from "@/lib/profile-editor";
import type { Category, PublicProfileData } from "@/lib/profile-view";

/** GET `path` on the API as the signed-in owner: session cookie and visitor address forwarded (ADR 0017). Null on any failure. */
async function getAsOwner<T>(path: string): Promise<T | null> {
  const cookie = (await cookies()).toString();
  const forwarded = apiRequestHeaders(
    await headers(),
    trustProxyHeaders(process.env.TRUST_PROXY_HEADERS),
  );
  const address = forwarded.get("x-forwarded-for");
  try {
    const res = await fetch(`${apiUrl()}${path}`, {
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
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

/** Server components: the owner's highlight lists (GET /api/highlights). Null when the API cannot answer. */
export const fetchHighlights = () => getAsOwner<HighlightOverview>("/api/highlights");

/** Server components: the owner's "Agora" state, one entry per category (GET /api/now). */
export const fetchNow = () => getAsOwner<NowEntry[]>("/api/now");

/** Server components: the owner's editable profile (GET /api/profile). */
export const fetchOwnProfile = () => getAsOwner<OwnProfile>("/api/profile");

/** Server components: the first page of one category's collection, newest first. */
export const fetchCollectionPage = (category: Category) =>
  getAsOwner<CollectionPage>(
    collectionQuery({ category, sort: "recentes", page: 0, includeHidden: false }),
  );

/**
 * The favorites list does not say which category each work is (the highlight response has none), but
 * the public profile does and `ref` is the collection item id. Used only to give favorites their
 * medium's shape; null (or a missing id) falls back to the poster shape.
 */
export async function fetchFavoriteCategories(slug: string): Promise<Record<number, Category>> {
  const forwarded = apiRequestHeaders(
    await headers(),
    trustProxyHeaders(process.env.TRUST_PROXY_HEADERS),
  );
  const address = forwarded.get("x-forwarded-for");
  try {
    const res = await fetch(`${apiUrl()}/api/public/profiles/${encodeURIComponent(slug)}`, {
      headers: { accept: "application/json", ...(address ? { "x-forwarded-for": address } : {}) },
      cache: "no-store",
      redirect: "manual",
      signal: AbortSignal.timeout(5000),
    });
    if (!res.ok) return {};
    const profile = (await res.json()) as Partial<PublicProfileData>;
    const map: Record<number, Category> = {};
    for (const item of profile.favorites ?? []) map[item.ref] = item.category;
    return map;
  } catch {
    return {};
  }
}
