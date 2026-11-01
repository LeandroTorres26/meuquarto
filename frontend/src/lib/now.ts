import { relativeTimePtBr } from "./linked-accounts";
import { collectionMessage } from "./collection";
import { sourceName, type Category } from "./profile-view";

// "Agora" (S10 and the block on S4): pure helpers. No React, no fetch; unit tested.

export type NowMode = "AUTOMATIC" | "PINNED" | "HIDDEN";

/** The item the profile shows for a category. `source` is "MANUAL" for a pin, else the platform. */
export type NowShown = {
  collectionItemId: number;
  title: string;
  year: number | null;
  imageUrl: string | null;
  source: string;
  at: string | null;
};

/** GET /api/now: one entry per category, in the API's order. */
export type NowEntry = {
  category: Category;
  mode: NowMode;
  /** False where no platform feeds the category (Séries). */
  automaticAvailable: boolean;
  /** What the profile shows now, or null (hidden, or nothing synced yet). */
  shown: NowShown | null;
};

export const MANUAL_SOURCE = "MANUAL";

/** NowCard verbs (docs/design-system/components/NowCard/README.md). */
export const NOW_VERB: Record<Category, string> = {
  MOVIE: "Assistindo",
  SERIES: "Assistindo",
  ANIME: "Assistindo",
  MANGA: "Lendo",
  BOOK: "Lendo",
  GAME: "Jogando",
  MUSIC: "Ouvindo",
};

/** The platform that feeds a category automatically, for "Conecte o …". Séries has none. */
export const AUTO_PLATFORM: Record<Category, string | null> = {
  MOVIE: "LETTERBOXD",
  SERIES: null,
  ANIME: "ANILIST",
  MANGA: "ANILIST",
  BOOK: "GOODREADS",
  GAME: "STEAM",
  MUSIC: "LASTFM",
};

/** Status line of a NowCard: "Letterboxd, há 2 dias" for a sync, "Escolhido por você" for a pin. */
export function nowStatus(source: string, at: string | null, now: number): string {
  if (source === MANUAL_SOURCE) return "Escolhido por você";
  const name = sourceName(source);
  const when = at ? relativeTimePtBr(at, now) : "";
  return when ? `${name}, ${when}` : name;
}

/** "Do Letterboxd" / "Da Steam". */
function fromPlatform(source: string): string {
  return `${source === "STEAM" ? "Da" : "Do"} ${sourceName(source)}`;
}

export type EntryCaption = {
  text: string;
  /** Platform to offer linking ("Conecte o Goodreads"), when the automatic mode has nothing to show. */
  connect?: string;
};

/** The line under the item in S10 (docs/design/telas/painel-agora.html). */
export function entryCaption(entry: NowEntry): EntryCaption {
  if (entry.mode === "HIDDEN") return { text: "Escondido no perfil." };
  if (entry.mode === "PINNED") {
    const platform = AUTO_PLATFORM[entry.category];
    return platform
      ? { text: "Escolhido por você.", connect: platform }
      : { text: "Escolhido por você." };
  }
  if (!entry.automaticAvailable) {
    return { text: "Séries não têm plataforma para sincronizar. Escolha à mão." };
  }
  const platform = AUTO_PLATFORM[entry.category];
  if (entry.shown) {
    return { text: `${fromPlatform(entry.shown.source)}, muda sozinho` };
  }
  return {
    text: "Nada sincronizado ainda.",
    ...(platform ? { connect: platform } : {}),
  };
}

/** Text of S10's "Escolher" picker toggle for the current mode. */
export const pickerLabel = (entry: NowEntry): string =>
  entry.mode === "PINNED" ? "Trocar obra" : "Escolher obra";

/** Error codes of PUT /api/now/{category}, in pt-BR; anything else falls back to the collection messages. */
const MESSAGES: Record<string, string> = {
  NOT_IN_COLLECTION: "Essa obra não está mais na sua coleção. Atualizamos a lista.",
  ITEM_HIDDEN: "Obras ocultas não podem ficar no Agora. Mostre a obra primeiro.",
  CATEGORY_MISMATCH: "Essa obra é de outra categoria.",
  ITEM_REQUIRED: "Escolha uma obra da sua coleção.",
};

export function nowMessage(
  code: string | undefined,
  status?: number,
  retryAfterSeconds?: number,
): string {
  if (code && MESSAGES[code]) return MESSAGES[code];
  return collectionMessage(code, status, retryAfterSeconds);
}

/** Replaces one category's entry in the list (the PUT answers with the new state of that category). */
export function withEntry(entries: NowEntry[], saved: NowEntry): NowEntry[] {
  return entries.map((entry) => (entry.category === saved.category ? saved : entry));
}
