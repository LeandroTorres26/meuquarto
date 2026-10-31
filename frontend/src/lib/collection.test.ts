import { afterEach, describe, expect, it, vi } from "vitest";
import {
  applyOrder,
  checkNote,
  collectionMessage,
  collectionQuery,
  counterLabel,
  debounce,
  filterByTitle,
  focusDirectionAfterMove,
  hasMore,
  isFull,
  listOf,
  moveId,
  moveTo,
  needsRefresh,
  noteChanged,
  noteLength,
  searchTerm,
  showUpsell,
  sourceLabels,
  tabFromParam,
  tabParam,
  unavailableMessage,
  withList,
  withOrder,
  type HighlightOverview,
} from "./collection";

describe("tabs", () => {
  it("reads ?aba= and falls back to the first category", () => {
    expect(tabFromParam("favoritos")).toBe("FAVORITES");
    expect(tabFromParam("animes")).toBe("ANIME");
    expect(tabFromParam("musica")).toBe("MUSIC");
    expect(tabFromParam("nada")).toBe("MOVIE");
    expect(tabFromParam(undefined)).toBe("MOVIE");
    expect(tabFromParam(["mangas", "animes"])).toBe("MANGA");
  });
  it("writes the same parameter back", () => {
    expect(tabParam("FAVORITES")).toBe("favoritos");
    expect(tabParam("BOOK")).toBe("livros");
  });
});

describe("limit and counters", () => {
  it("is full at the limit, not before", () => {
    expect(isFull(3, 4)).toBe(false);
    expect(isFull(4, 4)).toBe(true);
    expect(isFull(12, 12)).toBe(true);
  });
  it("shows the upsell only on a full free list", () => {
    expect(showUpsell(4, 4)).toBe(true);
    expect(showUpsell(3, 4)).toBe(false);
    expect(showUpsell(12, 12)).toBe(false);
  });
  it("formats the chip counter", () => {
    expect(counterLabel(2, 4)).toBe("2/4");
  });
});

describe("reorder", () => {
  it("moves one place either way", () => {
    expect(moveId([1, 2, 3], 2, -1)).toEqual([2, 1, 3]);
    expect(moveId([1, 2, 3], 2, 1)).toEqual([1, 3, 2]);
  });
  it("returns the same array at the ends and for unknown ids", () => {
    const ids = [1, 2, 3];
    expect(moveId(ids, 1, -1)).toBe(ids);
    expect(moveId(ids, 3, 1)).toBe(ids);
    expect(moveId(ids, 9, 1)).toBe(ids);
  });
  it("moves to a position (drag and drop) without touching the input", () => {
    const ids = [1, 2, 3, 4];
    expect(moveTo(ids, 0, 2)).toEqual([2, 3, 1, 4]);
    expect(moveTo(ids, 3, 0)).toEqual([4, 1, 2, 3]);
    expect(ids).toEqual([1, 2, 3, 4]);
    expect(moveTo(ids, 1, 1)).toBe(ids);
    expect(moveTo(ids, 1, 9)).toBe(ids);
  });
  it("hands focus to the other button when the pressed one reaches an end", () => {
    expect(focusDirectionAfterMove(0, 4, -1)).toBe(1);
    expect(focusDirectionAfterMove(3, 4, 1)).toBe(-1);
    expect(focusDirectionAfterMove(1, 4, -1)).toBe(-1);
    expect(focusDirectionAfterMove(2, 4, 1)).toBe(1);
  });
  it("orders entries by an id order", () => {
    const items = [{ id: 1 }, { id: 2 }, { id: 3 }];
    expect(applyOrder(items, [3, 1, 2]).map((i) => i.id)).toEqual([3, 1, 2]);
    expect(applyOrder(items, [2]).map((i) => i.id)).toEqual([2, 1, 3]);
  });
});

function media(title: string) {
  return { provider: "ANILIST", externalId: "1", title, year: null, imageUrl: null };
}

describe("overview updates", () => {
  const overview: HighlightOverview = {
    plan: "FREE",
    limit: 4,
    lists: [
      { category: null, items: [] },
      {
        category: "ANIME",
        items: [
          { id: 1, collectionItemId: 10, position: 0, media: media("a") },
          { id: 2, collectionItemId: 11, position: 1, media: media("b") },
        ],
      },
    ],
  };
  it("finds a list, or an empty one", () => {
    expect(listOf(overview, "ANIME").items).toHaveLength(2);
    expect(listOf(overview, "BOOK").items).toEqual([]);
  });
  it("reorders optimistically only the list asked for", () => {
    const next = withOrder(overview, "ANIME", [2, 1]);
    expect(listOf(next, "ANIME").items.map((h) => h.id)).toEqual([2, 1]);
    expect(listOf(next, null)).toBe(overview.lists[0]);
    expect(overview.lists[1].items.map((h) => h.id)).toEqual([1, 2]);
  });
  it("replaces or adds a list", () => {
    expect(listOf(withList(overview, { category: "ANIME", items: [] }), "ANIME").items).toEqual([]);
    expect(withList(overview, { category: "BOOK", items: [] }).lists).toHaveLength(3);
  });
});

describe("notes", () => {
  it("counts trimmed code points", () => {
    expect(noteLength("  oi  ")).toBe(2);
    expect(noteLength("\u{1F3AC}\u{1F3AC}")).toBe(2);
  });
  it("accepts up to 140 characters and trims", () => {
    expect(checkNote(" bom ")).toEqual({ ok: true, value: "bom" });
    expect(checkNote("a".repeat(140)).ok).toBe(true);
    expect(checkNote("")).toEqual({ ok: true, value: "" });
  });
  it("rejects 141 characters and control characters", () => {
    expect(checkNote("a".repeat(141)).ok).toBe(false);
    expect(checkNote("duas\nlinhas").ok).toBe(false);
  });
  it("sees whether there is anything to save", () => {
    expect(noteChanged(" a ", "a")).toBe(false);
    expect(noteChanged("", null)).toBe(false);
    expect(noteChanged("b", null)).toBe(true);
    expect(noteChanged("", "a")).toBe(true);
  });
});

describe("collection listing", () => {
  it("builds the query the API expects", () => {
    expect(
      collectionQuery({ category: "ANIME", sort: "titulo", page: 2, includeHidden: true }),
    ).toBe("/api/collection?category=ANIME&sort=title&page=2&size=24&includeHidden=true");
    expect(
      collectionQuery({ category: "BOOK", sort: "recentes", page: 0, includeHidden: false }),
    ).toContain("sort=recent");
    expect(
      collectionQuery({ category: "BOOK", sort: "ano", page: 0, includeHidden: false }),
    ).toContain("sort=year");
  });
  it("knows when there is more to load", () => {
    expect(hasMore(24, 30)).toBe(true);
    expect(hasMore(30, 30)).toBe(false);
  });
  it("filters by title ignoring case and accents", () => {
    const items = [{ media: { title: "Amélie" } }, { media: { title: "Akira" } }];
    expect(filterByTitle(items, "AMELIE")).toHaveLength(1);
    expect(filterByTitle(items, "  ")).toBe(items);
    expect(filterByTitle(items, "zz")).toEqual([]);
  });
  it("labels the origin", () => {
    expect(sourceLabels(["MANUAL"])).toEqual(["Manual"]);
    expect(sourceLabels(["ANILIST"])).toEqual(["AniList"]);
    expect(sourceLabels(["LETTERBOXD", "MANUAL"])).toEqual(["Letterboxd"]);
    expect(sourceLabels(["OUTRO"])).toEqual(["Outro"]);
  });
});

describe("search", () => {
  it("needs two characters after trimming", () => {
    expect(searchTerm("a")).toBeNull();
    expect(searchTerm("  a ")).toBeNull();
    expect(searchTerm(" ab ")).toBe("ab");
  });
  it("explains an unavailable catalog per category", () => {
    expect(unavailableMessage("MOVIE")).toContain("filmes e séries");
    expect(unavailableMessage("SERIES")).toContain("filmes e séries");
    expect(unavailableMessage("BOOK")).toContain("livros");
  });
});

describe("debounce", () => {
  afterEach(() => vi.useRealTimers());
  it("calls once, after the last call", () => {
    vi.useFakeTimers();
    const fn = vi.fn();
    const run = debounce(fn, 300);
    run("a");
    vi.advanceTimersByTime(200);
    run("b");
    vi.advanceTimersByTime(299);
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith("b");
  });
  it("can be cancelled", () => {
    vi.useFakeTimers();
    const fn = vi.fn();
    const run = debounce(fn, 300);
    run();
    run.cancel();
    vi.advanceTimersByTime(1000);
    expect(fn).not.toHaveBeenCalled();
  });
});

describe("error messages", () => {
  it.each([
    "ITEM_NOT_FOUND",
    "CANNOT_REMOVE_SYNCED",
    "CATALOG_UNAVAILABLE",
    "ADULT_NOT_ALLOWED",
    "NOTE_TOO_LONG",
    "NOTE_INVALID",
    "HIGHLIGHT_LIMIT_REACHED",
    "ALREADY_HIGHLIGHTED",
    "ITEM_HIDDEN",
    "CATEGORY_MISMATCH",
    "INVALID_ORDER",
    "NOT_IN_COLLECTION",
  ])("has pt-BR text for %s", (code) => {
    const text = collectionMessage(code, 400);
    expect(text).not.toBe(collectionMessage("DESCONHECIDO"));
    expect(text).not.toMatch(/[A-Z_]{6,}/);
  });
  it("handles throttling and unknown errors", () => {
    expect(collectionMessage(undefined, 429, 120)).toContain("2 minutos");
    expect(collectionMessage(undefined, 429)).toContain("um minuto");
    expect(collectionMessage(undefined, 401)).toContain("sessão");
    expect(collectionMessage("X", 500)).toContain("do nosso lado");
  });
  it("reloads the lists after errors that mean they are stale", () => {
    expect(needsRefresh("HIGHLIGHT_LIMIT_REACHED")).toBe(true);
    expect(needsRefresh("INVALID_ORDER")).toBe(true);
    expect(needsRefresh("NOTE_TOO_LONG")).toBe(false);
    expect(needsRefresh(undefined)).toBe(false);
  });
});
