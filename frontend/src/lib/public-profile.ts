import "server-only";
import { headers } from "next/headers";
import { cache } from "react";
import { apiUrl } from "@/lib/api";
import { apiRequestHeaders, trustProxyHeaders } from "@/lib/forwarded-headers";
import { listingApiQuery, type ListingState, type PublicListing } from "@/lib/category-listing";
import type { Category, PublicProfileData } from "@/lib/profile-view";

export type ProfileResult =
  { kind: "ok"; profile: PublicProfileData } | { kind: "not-found" } | { kind: "unavailable" };

/** Public origin used for absolute metadata URLs (Open Graph). Not secret; set per environment. */
export const siteUrl = () => process.env.SITE_URL?.trim() || "https://meuquarto.com.br";

function isProfile(value: unknown): value is PublicProfileData {
  const v = value as Partial<PublicProfileData> | null;
  return (
    !!v &&
    typeof v.slug === "string" &&
    typeof v.displayName === "string" &&
    Array.isArray(v.favorites) &&
    Array.isArray(v.categories)
  );
}

/**
 * Server components: the public profile from the API, once per request (React `cache`, shared by
 * generateMetadata and the page). The visitor's address goes along as X-Forwarded-For, rebuilt by the
 * same helper as the /api proxy (ADR 0017), so the API's per-IP read limit counts the visitor and
 * not the web container. Anonymous: no cookie is forwarded.
 */
export const getPublicProfile = cache(async (slug: string): Promise<ProfileResult> => {
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
    if (res.status === 404) return { kind: "not-found" };
    if (!res.ok) return { kind: "unavailable" };
    const body: unknown = await res.json();
    return isProfile(body) ? { kind: "ok", profile: body } : { kind: "unavailable" };
  } catch {
    return { kind: "unavailable" };
  }
});

export type ListingResult =
  { kind: "ok"; listing: PublicListing } | { kind: "not-found" } | { kind: "unavailable" };

function isListing(value: unknown): value is PublicListing {
  const v = value as Partial<PublicListing> | null;
  return !!v && Array.isArray(v.items) && typeof v.total === "number" && typeof v.page === "number";
}

/**
 * Server components: one page of a category (S18) from GET /api/public/profiles/{slug}/collection.
 * Same rules as the profile: anonymous, the visitor's address forwarded for the per-IP limit, local
 * data only. The query string is built from validated state (category enum, bounded page, trimmed
 * and encoded search), never from raw request text.
 */
export const getPublicListing = cache(
  async (slug: string, category: Category, state: ListingState): Promise<ListingResult> => {
    const forwarded = apiRequestHeaders(
      await headers(),
      trustProxyHeaders(process.env.TRUST_PROXY_HEADERS),
    );
    const address = forwarded.get("x-forwarded-for");
    try {
      const res = await fetch(
        `${apiUrl()}/api/public/profiles/${encodeURIComponent(slug)}/collection?${listingApiQuery(category, state)}`,
        {
          headers: {
            accept: "application/json",
            ...(address ? { "x-forwarded-for": address } : {}),
          },
          cache: "no-store",
          redirect: "manual",
          signal: AbortSignal.timeout(5000),
        },
      );
      if (res.status === 404) return { kind: "not-found" };
      if (!res.ok) return { kind: "unavailable" };
      const body: unknown = await res.json();
      return isListing(body) ? { kind: "ok", listing: body } : { kind: "unavailable" };
    } catch {
      return { kind: "unavailable" };
    }
  },
);
