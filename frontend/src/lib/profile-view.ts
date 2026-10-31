// Pure helpers for the public profile (S4) and the item dialog (S17): no React, no I/O, unit tested.

export type Category = "MOVIE" | "SERIES" | "ANIME" | "MANGA" | "BOOK" | "GAME" | "MUSIC";

/** GET /api/public/profiles/{slug}: items as an anonymous visitor sees them. */
export type PublicItem = {
  ref: number;
  category: Category;
  title: string;
  year: number | null;
  imageUrl: string | null;
  note: string | null;
};
export type PublicSection = {
  category: Category;
  total: number;
  mostRecent: boolean;
  items: PublicItem[];
};
export type PublicProfileData = {
  slug: string;
  displayName: string;
  favorites: PublicItem[];
  categories: PublicSection[];
};

/** GET /api/public/profiles/{slug}/items/{ref}. */
export type ItemOrigin = { source: string; firstSeenAt: string };
export type ItemLink = { provider: string; url: string };
export type PublicItemDetail = { item: PublicItem; origin: ItemOrigin[]; links: ItemLink[] };

/** Shapes of the HighlightCard (docs/design-system/components/HighlightCard/README.md). */
export type Shape = "poster" | "book" | "manga" | "vinyl" | "game" | "dvd";

type CategoryMeta = {
  /** Section heading. */
  heading: string;
  /** Text of the category sticker. */
  sticker: string;
  /** Count noun: "1 filme" / "16 filmes". */
  unit: readonly [string, string];
  /** Lowercase noun for the card's accessible name: "Perfect Days, filme de 2023". */
  noun: string;
  /** Modifier of `mq-tag--*`. */
  tag: string;
  shape: Shape;
  /** Intrinsic size of the cover <img>; the CSS keeps the medium's own ratio, these avoid layout shift. */
  width: number;
  height: number;
  /** Path segment of the category listing (S18). */
  route: string;
};

export const CATEGORY_META: Record<Category, CategoryMeta> = {
  MOVIE: {
    heading: "Filmes",
    sticker: "Filme",
    unit: ["filme", "filmes"],
    noun: "filme",
    tag: "movie",
    shape: "poster",
    width: 200,
    height: 300,
    route: "filmes",
  },
  SERIES: {
    heading: "Séries",
    sticker: "Série",
    unit: ["série", "séries"],
    noun: "série",
    tag: "series",
    shape: "poster",
    width: 200,
    height: 300,
    route: "series",
  },
  ANIME: {
    heading: "Animes",
    sticker: "Anime",
    unit: ["anime", "animes"],
    noun: "anime",
    tag: "anime",
    shape: "dvd",
    width: 200,
    height: 280,
    route: "animes",
  },
  MANGA: {
    heading: "Mangás",
    sticker: "Mangá",
    unit: ["mangá", "mangás"],
    noun: "mangá",
    tag: "manga",
    shape: "manga",
    width: 200,
    height: 296,
    route: "mangas",
  },
  BOOK: {
    heading: "Livros",
    sticker: "Livro",
    unit: ["livro", "livros"],
    noun: "livro",
    tag: "book",
    shape: "book",
    width: 200,
    height: 300,
    route: "livros",
  },
  GAME: {
    heading: "Games",
    sticker: "Game",
    unit: ["game", "games"],
    noun: "game",
    tag: "game",
    shape: "game",
    width: 200,
    height: 280,
    route: "games",
  },
  MUSIC: {
    heading: "Música",
    sticker: "Música",
    unit: ["álbum", "álbuns"],
    noun: "álbum",
    tag: "music",
    shape: "vinyl",
    width: 300,
    height: 300,
    route: "musica",
  },
};

/** Modifier classes of `mq-item` for a medium. Books and mangás are "volumes". */
export function shapeClasses(shape: Shape): string {
  const base = `mq-item--${shape}`;
  return shape === "book" || shape === "manga" ? `${base} mq-item--volume` : base;
}

function fnv1a(id: string): number {
  let hash = 2166136261;
  for (let i = 0; i < id.length; i++) {
    hash ^= id.charCodeAt(i);
    hash = Math.imul(hash, 16777619) >>> 0;
  }
  return hash >>> 0;
}

/**
 * Deterministic rotation factor of a work (HighlightCard README, "Rotation"): FNV-1a of the id, so
 * the same work leans the same way on the server, in the dialog and everywhere else.
 */
export function tiltFactor(id: string | number): number {
  const hash = fnv1a(String(id));
  const unit = (hash % 1000) / 999;
  const magnitude = 0.9 / 2.5 + (1 - 0.9 / 2.5) * unit;
  return Math.round(((hash >>> 10) & 1 ? 1 : -1) * magnitude * 1000) / 1000;
}

/** Tape or pin holding the card to the wall, also derived from the id. */
export function fixKind(id: string | number): "tape" | "pin" {
  return (fnv1a(String(id)) >>> 11) & 1 ? "tape" : "pin";
}

/** "1 filme", "16 filmes", "1 álbum". */
export function countLabel(category: Category, total: number): string {
  const [one, many] = CATEGORY_META[category].unit;
  return `${total} ${total === 1 ? one : many}`;
}

/** "Perfect Days, filme de 2023": the card link's accessible name (the cover image is decorative). */
export function itemLabel(item: Pick<PublicItem, "title" | "category" | "year">): string {
  const noun = CATEGORY_META[item.category].noun;
  return item.year ? `${item.title}, ${noun} de ${item.year}` : `${item.title}, ${noun}`;
}

/** Only https covers are rendered (the CSP allows nothing else). Anything else means "no cover". */
export function safeImageUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  try {
    return new URL(url).protocol === "https:" ? url : null;
  } catch {
    return null;
  }
}

/** Same rule for external links: only absolute https URLs from the API become an <a href>. */
export const safeLinkUrl = safeImageUrl;

/**
 * "Ver todos" only appears when the collection has more items than the section shows. It points to
 * the category listing (S18), which is not built yet: while CATEGORY_PAGE_READY is false the link
 * stays hidden instead of leading to a 404.
 */
export const CATEGORY_PAGE_READY = false;

export function showSeeAll(
  section: Pick<PublicSection, "total" | "items">,
  pageReady: boolean = CATEGORY_PAGE_READY,
): boolean {
  return pageReady && section.total > section.items.length;
}

export function seeAllHref(slug: string, category: Category): string {
  return `/${encodeURIComponent(slug)}/${CATEGORY_META[category].route}`;
}

/** The sections that have something to show, in the order the API sent them. */
export function visibleSections(sections: PublicSection[]): PublicSection[] {
  return sections.filter((section) => section.items.length > 0);
}

export function isProfileEmpty(profile: Pick<PublicProfileData, "favorites" | "categories">) {
  return profile.favorites.length === 0 && visibleSections(profile.categories).length === 0;
}

/** The collection item id as the API wants it: digits only, at most 18. Null for anything else. */
export function parseItemRef(raw: string | null | undefined): string | null {
  return raw && /^\d{1,18}$/.test(raw) ? raw : null;
}

const SOURCE_NAMES: Record<string, string> = {
  LETTERBOXD: "Letterboxd",
  ANILIST: "AniList",
  LASTFM: "Last.fm",
  STEAM: "Steam",
  GOODREADS: "Goodreads",
  TMDB: "TMDB",
  OPENLIBRARY: "Open Library",
  IGDB: "IGDB",
  DEEZER: "Deezer",
};

export function sourceName(source: string): string {
  return SOURCE_NAMES[source] ?? source.charAt(0) + source.slice(1).toLowerCase();
}

/** Link label: "Ver no TMDB", "Ver na Steam". */
export function linkLabel(provider: string): string {
  const preposition = provider === "STEAM" ? "na" : "no";
  return `Ver ${preposition} ${sourceName(provider)}`;
}

const MONTHS = [
  "jan.",
  "fev.",
  "mar.",
  "abr.",
  "maio",
  "jun.",
  "jul.",
  "ago.",
  "set.",
  "out.",
  "nov.",
  "dez.",
];

/** "11 out. 2026", in UTC so the server and the browser agree. Empty string for an invalid date. */
export function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return `${date.getUTCDate()} ${MONTHS[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
}

/**
 * Origin caption of S17 (collection_item_sources): "Adicionado à mão" when it only came by hand,
 * otherwise "Do Letterboxd, desde 11 out. 2026" (the date is when the first platform saw it).
 */
export function originCaption(origin: ItemOrigin[]): string {
  const platforms = origin.filter((o) => o.source !== "MANUAL");
  if (platforms.length === 0) return "Adicionado à mão";
  const names = platforms.map((o) => sourceName(o.source));
  const from =
    names.length === 1
      ? `${platforms[0].source === "STEAM" ? "Da" : "Do"} ${names[0]}`
      : `De ${names.slice(0, -1).join(", ")} e ${names[names.length - 1]}`;
  const earliest = platforms
    .map((o) => o.firstSeenAt)
    .filter((iso) => formatDate(iso) !== "")
    .sort()[0];
  return earliest ? `${from}, desde ${formatDate(earliest)}` : from;
}
