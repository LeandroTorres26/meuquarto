import { CATEGORY_META, type Category } from "./profile-view";

// S7 and S8 (painel-destaques.html, busca.html): pure helpers for the collection and highlights
// screens. No React, no fetch; everything here is unit tested.

export const CATEGORIES: Category[] = [
  "MOVIE",
  "SERIES",
  "ANIME",
  "MANGA",
  "BOOK",
  "GAME",
  "MUSIC",
];

/** A tab of S7: "Favoritos de tudo" (highlight list with no category) or one category. */
export type Tab = "FAVORITES" | Category;

export const TABS: Tab[] = ["FAVORITES", ...CATEGORIES];

export const FAVORITES_LABEL = "Favoritos de tudo";

/** `?aba=` value of a tab: "favoritos" or the category's route segment ("animes"). */
export function tabParam(tab: Tab): string {
  return tab === "FAVORITES" ? "favoritos" : CATEGORY_META[tab].route;
}

/** The tab named by `?aba=`; anything unknown falls back to the first category (as the prototype does). */
export function tabFromParam(value: string | string[] | undefined | null): Tab {
  const raw = Array.isArray(value) ? value[0] : value;
  if (raw === "favoritos") return "FAVORITES";
  return CATEGORIES.find((c) => CATEGORY_META[c].route === raw) ?? "MOVIE";
}

export const tabLabel = (tab: Tab): string =>
  tab === "FAVORITES" ? FAVORITES_LABEL : CATEGORY_META[tab].heading;

/** The category of the highlight list behind a tab (null is the favorites list). */
export const listCategory = (tab: Tab): Category | null => (tab === "FAVORITES" ? null : tab);

// ---------- API shapes ----------

export type Media = {
  provider: string;
  externalId: string;
  title: string;
  year: number | null;
  imageUrl: string | null;
};

export type CollectionItem = {
  id: number;
  category: Category;
  media: Media;
  note: string | null;
  hidden: boolean;
  /** "MANUAL" and/or provider names ("LETTERBOXD", "ANILIST"). */
  sources: string[];
  removable: boolean;
  addedAt: string;
  lastActivityAt: string;
};

export type CollectionPage = { items: CollectionItem[]; page: number; size: number; total: number };

export type Highlight = { id: number; collectionItemId: number; position: number; media: Media };
export type HighlightList = { category: Category | null; items: Highlight[] };
export type HighlightOverview = { plan: string; limit: number; lists: HighlightList[] };

export type CatalogResult = {
  category: Category;
  provider: string;
  externalId: string;
  title: string;
  year: number | null;
  imageUrl: string | null;
};
export type CatalogSearch = { available: boolean; results: CatalogResult[] };

// ---------- collection listing ----------

export type SortKey = "recentes" | "titulo" | "ano";

export const SORT_OPTIONS: { key: SortKey; label: string }[] = [
  { key: "recentes", label: "Recentes" },
  { key: "titulo", label: "Título" },
  { key: "ano", label: "Ano" },
];

/** The `sort` parameter of GET /api/collection. */
export const sortParam = (key: SortKey): "recent" | "title" | "year" =>
  key === "titulo" ? "title" : key === "ano" ? "year" : "recent";

export const PAGE_SIZE = 24;

export function collectionQuery(input: {
  category: Category;
  sort: SortKey;
  page: number;
  includeHidden: boolean;
}): string {
  const params = new URLSearchParams({
    category: input.category,
    sort: sortParam(input.sort),
    page: String(input.page),
    size: String(PAGE_SIZE),
    includeHidden: String(input.includeHidden),
  });
  return `/api/collection?${params.toString()}`;
}

const PROVIDER_NAMES: Record<string, string> = {
  LETTERBOXD: "Letterboxd",
  ANILIST: "AniList",
  STEAM: "Steam",
  LASTFM: "Last.fm",
  GOODREADS: "Goodreads",
};

/** Origin badges of an item: the platforms that fed it, or "Manual" when only the owner added it. */
export function sourceLabels(sources: string[]): string[] {
  const synced = sources
    .filter((s) => s !== "MANUAL")
    .map((s) => PROVIDER_NAMES[s] ?? s.charAt(0) + s.slice(1).toLowerCase());
  return synced.length > 0 ? synced : ["Manual"];
}

/** True when the item also came from a sync (so it can only be hidden, never removed: FR-09). */
export const isSynced = (item: Pick<CollectionItem, "removable">): boolean => !item.removable;

/** More pages to load: what we hold is less than the total the server reported. */
export const hasMore = (loaded: number, total: number): boolean => loaded < total;

const normalize = (text: string): string => text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

/** Search box of the collection: accent- and case-insensitive match on the title, over the items loaded so far. */
export function filterByTitle<T extends { media: Pick<Media, "title"> }>(
  items: T[],
  query: string,
): T[] {
  const needle = normalize(query.trim());
  if (!needle) return items;
  return items.filter((item) => normalize(item.media.title).includes(needle));
}

// ---------- notes ----------

export const NOTE_MAX = 140;

const codePoints = (text: string): number => Array.from(text).length;

/** Characters the counter shows (code points, as the backend counts them). */
export const noteLength = (raw: string): number => codePoints(raw.trim());

const CONTROL = /[\u0000-\u001f\u007f-\u009f]/;

export type NoteCheck = { ok: true; value: string } | { ok: false; message: string };

/** Mirrors the backend rule (Note.clean): trimmed, at most 140 characters, no control characters. */
export function checkNote(raw: string): NoteCheck {
  const value = raw.trim();
  if (codePoints(value) > NOTE_MAX) {
    return { ok: false, message: `A nota pode ter no máximo ${NOTE_MAX} caracteres.` };
  }
  if (CONTROL.test(value)) {
    return {
      ok: false,
      message: "A nota não pode ter quebras de linha nem caracteres de controle.",
    };
  }
  return { ok: true, value };
}

/** Nothing to save when the trimmed text equals what is already stored. */
export const noteChanged = (raw: string, stored: string | null): boolean =>
  raw.trim() !== (stored ?? "");

// ---------- highlight limit ----------

/** The most any plan allows today (the pack); at this limit there is nothing to upsell. */
export const PACK_LIMIT = 12;

export const isFull = (count: number, limit: number): boolean => count >= limit;

/** Chip counter: "3/4". */
export const counterLabel = (count: number, limit: number): string => `${count}/${limit}`;

/** Meter text: "4 de 4". */
export const meterLabel = (count: number, limit: number): string => `${count} de ${limit}`;

/** Free plan and full list: the upsell box. With the pack a plain note is enough. */
export const showUpsell = (count: number, limit: number): boolean =>
  isFull(count, limit) && limit < PACK_LIMIT;

/** The highlight list behind a tab, or an empty one when the API has not created it yet. */
export function listOf(overview: HighlightOverview, category: Category | null): HighlightList {
  return overview.lists.find((l) => l.category === category) ?? { category, items: [] };
}

// ---------- reorder ----------

/** Moves the entry at `from` to position `to` (drag and drop). Returns the same array when nothing changes. */
export function moveTo(ids: number[], from: number, to: number): number[] {
  if (from === to || from < 0 || to < 0 || from >= ids.length || to >= ids.length) return ids;
  const next = [...ids];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/** Moves `id` one place (`-1` earlier, `1` later). Returns the same array when it cannot move. */
export function moveId(ids: number[], id: number, direction: -1 | 1): number[] {
  const from = ids.indexOf(id);
  return moveTo(ids, from, from + direction);
}

/** After a button move the pressed button may be disabled (the item reached an end): the opposite one gets focus. */
export function focusDirectionAfterMove(
  newIndex: number,
  length: number,
  direction: -1 | 1,
): -1 | 1 {
  const atEnd = direction === -1 ? newIndex === 0 : newIndex === length - 1;
  return atEnd ? (direction === -1 ? 1 : -1) : direction;
}

/** Orders the entries by a new id order (ids not in `order` go to the end, keeping their relative place). */
export function applyOrder<T extends { id: number }>(items: T[], order: number[]): T[] {
  const rank = new Map(order.map((id, index) => [id, index]));
  return [...items].sort(
    (a, b) => (rank.get(a.id) ?? order.length) - (rank.get(b.id) ?? order.length),
  );
}

// ---------- search dialog ----------

export const SEARCH_MIN_CHARS = 2;
export const SEARCH_DEBOUNCE_MS = 350;

/** The query worth sending: trimmed, at least two characters. Null means "do not search yet". */
export function searchTerm(raw: string): string | null {
  const term = raw.trim();
  return Array.from(term).length >= SEARCH_MIN_CHARS ? term : null;
}

export const catalogSearchPath = (category: Category, term: string): string =>
  `/api/catalog/search?${new URLSearchParams({ category, q: term }).toString()}`;

/** Key to tell whether a result is already in the collection. */
export const workKey = (category: Category, provider: string, externalId: string): string =>
  `${category}:${provider}:${externalId}`;

/** "Série de 1998": the caption under a result title. */
export function resultCaption(category: Category, year: number | null): string {
  const sticker = CATEGORY_META[category].sticker;
  return year ? `${sticker} de ${year}` : sticker;
}

/** Message for a category with no catalog (or a catalog without a key). */
export function unavailableMessage(category: Category): string {
  switch (category) {
    case "MOVIE":
    case "SERIES":
      return "A busca de filmes e séries ainda não está disponível.";
    default:
      return `A busca de ${CATEGORY_META[category].heading.toLowerCase()} ainda não está disponível.`;
  }
}

/** Calls `fn` once, `wait` ms after the last call. `cancel` drops a pending call. */
export function debounce<A extends unknown[]>(fn: (...args: A) => void, wait: number) {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const run = (...args: A) => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      timer = undefined;
      fn(...args);
    }, wait);
  };
  run.cancel = () => {
    clearTimeout(timer);
    timer = undefined;
  };
  return run;
}

// ---------- errors ----------

/** Error codes of CollectionException, HighlightException and the catalog, in pt-BR. */
const MESSAGES: Record<string, string> = {
  ITEM_NOT_FOUND: "Essa obra não está mais na sua coleção. Atualizamos a lista.",
  CANNOT_REMOVE_SYNCED:
    "Essa obra veio de uma conta vinculada e voltaria na próxima sincronização. Oculte em vez de remover.",
  CATALOG_UNAVAILABLE: "O catálogo dessa categoria não está disponível agora. Tente mais tarde.",
  ADULT_NOT_ALLOWED: "Obras para maiores de idade ainda não podem ser adicionadas.",
  NOTE_TOO_LONG: `A nota pode ter no máximo ${NOTE_MAX} caracteres.`,
  NOTE_INVALID: "A nota não pode ter quebras de linha nem caracteres de controle.",
  HIGHLIGHT_LIMIT_REACHED: "Os destaques dessa lista estão cheios. Remova um para destacar outro.",
  ALREADY_HIGHLIGHTED: "Essa obra já está nos destaques.",
  ITEM_HIDDEN: "Obras ocultas não podem ser destacadas. Mostre a obra primeiro.",
  CATEGORY_MISMATCH: "Essa obra é de outra categoria e não cabe nessa lista.",
  INVALID_ORDER: "A ordem mudou em outro lugar. Atualizamos a lista; tente de novo.",
  HIGHLIGHT_NOT_FOUND: "Esse destaque não existe mais. Atualizamos a lista.",
  NOT_IN_COLLECTION: "Essa obra não está na sua coleção. Atualizamos a lista.",
};

export const NETWORK_ERROR =
  "Não foi possível falar com o servidor. Confira sua conexão e tente de novo.";
const GENERIC = "Algo deu errado do nosso lado. Tente de novo em instantes.";

/** Maps `{error}` (plus status and Retry-After) of the collection, highlights and catalog endpoints to pt-BR text. */
export function collectionMessage(
  code: string | undefined,
  status?: number,
  retryAfterSeconds?: number,
): string {
  if (code && MESSAGES[code]) return MESSAGES[code];
  if (status === 429 || code === "TOO_MANY_REQUESTS") {
    const minutes = retryAfterSeconds ? Math.ceil(retryAfterSeconds / 60) : 0;
    return minutes > 1
      ? `Muitas tentativas seguidas. Espere ${minutes} minutos e tente de novo.`
      : "Muitas tentativas seguidas. Espere um minuto e tente de novo.";
  }
  if (status === 401) return "Sua sessão expirou. Entre de novo.";
  return GENERIC;
}

/** Errors after which the lists on screen may be stale, so the screen reloads them. */
export function needsRefresh(code: string | undefined): boolean {
  return (
    code === "HIGHLIGHT_LIMIT_REACHED" ||
    code === "INVALID_ORDER" ||
    code === "HIGHLIGHT_NOT_FOUND" ||
    code === "ALREADY_HIGHLIGHTED" ||
    code === "ITEM_NOT_FOUND" ||
    code === "NOT_IN_COLLECTION" ||
    code === "ITEM_HIDDEN"
  );
}

// ---------- overview updates ----------

/** The overview with the list of `category` replaced (a PUT /api/highlights/order answer). */
export function withList(overview: HighlightOverview, list: HighlightList): HighlightOverview {
  const exists = overview.lists.some((l) => l.category === list.category);
  return {
    ...overview,
    lists: exists
      ? overview.lists.map((l) => (l.category === list.category ? list : l))
      : [...overview.lists, list],
  };
}

/** The overview with the highlights of `category` shown in the order of `ids` (optimistic reorder). */
export function withOrder(
  overview: HighlightOverview,
  category: Category | null,
  ids: number[],
): HighlightOverview {
  const list = listOf(overview, category);
  return withList(overview, { ...list, items: applyOrder(list.items, ids) });
}
