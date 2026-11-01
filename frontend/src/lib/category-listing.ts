// Pure helpers for the category listing (S18): route segments, URL state and paging. No React, no I/O.

import { CATEGORY_META, type Category, type PublicItem } from "./profile-view";

/** GET /api/public/profiles/{slug}/collection: one page of a category, highlights flagged. */
export type ListingItem = PublicItem & { highlighted: boolean };
export type PublicListing = { items: ListingItem[]; page: number; size: number; total: number };

/** Covers per page: four rows of the six-column grid. The API caps the size at 48. */
export const PAGE_SIZE = 24;
/** Same limit as the API (PublicProfiles.MAX_QUERY_LENGTH). */
export const MAX_QUERY_LENGTH = 100;

export type Order = "recentes" | "titulo" | "ano";
export const ORDERS: readonly { value: Order; label: string; api: "recent" | "title" | "year" }[] =
  [
    { value: "recentes", label: "Recentes", api: "recent" },
    { value: "titulo", label: "Título", api: "title" },
    { value: "ano", label: "Ano", api: "year" },
  ];

/** The category of a path segment (`filmes`, `musica`...), or null for anything else. */
export function categoryFromRoute(segment: string): Category | null {
  for (const [category, meta] of Object.entries(CATEGORY_META)) {
    if (meta.route === segment) return category as Category;
  }
  return null;
}

/** The state of the listing as the URL carries it. `page` is 1-based. */
export type ListingState = { q: string; ordem: Order; page: number };

const first = (value: string | string[] | undefined) => (Array.isArray(value) ? value[0] : value);

/** Reads `?q=&ordem=&pagina=` defensively: junk falls back to the defaults, never throws. */
export function parseListingState(raw: {
  q?: string | string[];
  ordem?: string | string[];
  pagina?: string | string[];
}): ListingState {
  const q = (first(raw.q) ?? "").trim().slice(0, MAX_QUERY_LENGTH);
  const ordemRaw = first(raw.ordem);
  const ordem = ORDERS.find((o) => o.value === ordemRaw)?.value ?? "recentes";
  const paginaRaw = first(raw.pagina);
  const page = paginaRaw && /^\d{1,6}$/.test(paginaRaw) ? Math.max(1, Number(paginaRaw)) : 1;
  return { q, ordem, page };
}

/** The API query string of a state (page is 0-based there). */
export function listingApiQuery(category: Category, state: ListingState): string {
  const params = new URLSearchParams({
    category,
    sort: ORDERS.find((o) => o.value === state.ordem)?.api ?? "recent",
    page: String(state.page - 1),
    size: String(PAGE_SIZE),
  });
  if (state.q) params.set("q", state.q);
  return params.toString();
}

/** The page address for a state; defaults are left out so the clean URL stays clean. */
export function listingHref(
  slug: string,
  category: Category,
  state: Partial<ListingState>,
): string {
  const params = new URLSearchParams();
  if (state.q) params.set("q", state.q);
  if (state.ordem && state.ordem !== "recentes") params.set("ordem", state.ordem);
  if (state.page && state.page > 1) params.set("pagina", String(state.page));
  const query = params.toString();
  const base = `/${encodeURIComponent(slug)}/${CATEGORY_META[category].route}`;
  return query ? `${base}?${query}` : base;
}

export function pageCount(total: number, size: number = PAGE_SIZE): number {
  return Math.max(1, Math.ceil(total / size));
}

/** The sentence under the grid (aria-live): "Mostrando 1 a 24 de 100 animes para “x”." */
export function statusText(
  category: Category,
  state: Pick<ListingState, "q" | "page">,
  shown: number,
  total: number,
): string {
  if (total === 0 || shown === 0) return "";
  const from = (state.page - 1) * PAGE_SIZE + 1;
  const to = from + shown - 1;
  const [one, many] = CATEGORY_META[category].unit;
  const range = from === to ? `${from}` : `${from} a ${to}`;
  const suffix = state.q ? ` para “${state.q}”` : "";
  return `Mostrando ${range} de ${total} ${total === 1 ? one : many}${suffix}.`;
}
