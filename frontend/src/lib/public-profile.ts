import "server-only";
import { headers } from "next/headers";
import { cache } from "react";
import { apiUrl } from "@/lib/api";
import { apiRequestHeaders, trustProxyHeaders } from "@/lib/forwarded-headers";
import type { PublicProfileData } from "@/lib/profile-view";

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
