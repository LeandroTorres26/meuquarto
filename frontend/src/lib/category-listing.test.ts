import { describe, expect, it } from "vitest";
import {
  categoryFromRoute,
  listingApiQuery,
  listingHref,
  pageCount,
  parseListingState,
  statusText,
} from "./category-listing";
import { CATEGORY_META } from "./profile-view";

describe("categoryFromRoute", () => {
  it("maps every pt-BR segment back to its category", () => {
    for (const [category, meta] of Object.entries(CATEGORY_META)) {
      expect(categoryFromRoute(meta.route)).toBe(category);
    }
  });

  it("rejects anything else, including the API spelling and case variants", () => {
    for (const bad of ["", "movie", "MOVIE", "Filmes", "filme", "painel", "../x"]) {
      expect(categoryFromRoute(bad)).toBeNull();
    }
  });
});

describe("parseListingState", () => {
  it("defaults to recent, first page, no search", () => {
    expect(parseListingState({})).toEqual({ q: "", ordem: "recentes", page: 1 });
  });

  it("reads valid values and trims the search", () => {
    expect(parseListingState({ q: "  akira ", ordem: "ano", pagina: "3" })).toEqual({
      q: "akira",
      ordem: "ano",
      page: 3,
    });
  });

  it("falls back on junk and caps the search length", () => {
    expect(parseListingState({ ordem: "random", pagina: "-1" })).toMatchObject({
      ordem: "recentes",
      page: 1,
    });
    expect(parseListingState({ pagina: "0" }).page).toBe(1);
    expect(parseListingState({ pagina: "1e9" }).page).toBe(1);
    expect(parseListingState({ q: "x".repeat(300) }).q).toHaveLength(100);
  });

  it("uses the first of repeated parameters", () => {
    expect(parseListingState({ q: ["a", "b"], ordem: ["titulo", "ano"] })).toMatchObject({
      q: "a",
      ordem: "titulo",
    });
  });
});

describe("listingApiQuery", () => {
  it("is zero-based, bounded and encodes the search", () => {
    const query = listingApiQuery("ANIME", { q: "a&b c", ordem: "titulo", page: 2 });
    const params = new URLSearchParams(query);
    expect(params.get("category")).toBe("ANIME");
    expect(params.get("sort")).toBe("title");
    expect(params.get("page")).toBe("1");
    expect(params.get("size")).toBe("24");
    expect(params.get("q")).toBe("a&b c");
    expect(query).not.toContain("a&b");
  });

  it("omits an empty search", () => {
    expect(listingApiQuery("MOVIE", { q: "", ordem: "recentes", page: 1 })).not.toContain("q=");
  });
});

describe("listingHref", () => {
  it("leaves defaults out", () => {
    expect(listingHref("leandro", "ANIME", {})).toBe("/leandro/animes");
    expect(listingHref("leandro", "MUSIC", { ordem: "recentes", page: 1 })).toBe("/leandro/musica");
  });

  it("carries search, order and page", () => {
    expect(listingHref("leandro", "ANIME", { q: "ghibli é", ordem: "ano", page: 2 })).toBe(
      "/leandro/animes?q=ghibli+%C3%A9&ordem=ano&pagina=2",
    );
  });
});

describe("pageCount", () => {
  it("is at least one and rounds up", () => {
    expect(pageCount(0)).toBe(1);
    expect(pageCount(24)).toBe(1);
    expect(pageCount(25)).toBe(2);
    expect(pageCount(101)).toBe(5);
  });
});

describe("statusText", () => {
  it("describes the range with the count noun", () => {
    expect(statusText("ANIME", { q: "", page: 2 }, 24, 100)).toBe(
      "Mostrando 25 a 48 de 100 animes.",
    );
    expect(statusText("MUSIC", { q: "", page: 1 }, 1, 1)).toBe("Mostrando 1 de 1 álbum.");
  });

  it("mentions the search and is empty without results", () => {
    expect(statusText("MOVIE", { q: "x", page: 1 }, 3, 3)).toBe(
      "Mostrando 1 a 3 de 3 filmes para “x”.",
    );
    expect(statusText("MOVIE", { q: "x", page: 1 }, 0, 0)).toBe("");
  });
});
