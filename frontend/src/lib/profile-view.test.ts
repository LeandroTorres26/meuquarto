import { describe, expect, it } from "vitest";
import {
  CATEGORY_META,
  countLabel,
  fixKind,
  formatDate,
  isProfileEmpty,
  itemLabel,
  linkLabel,
  originCaption,
  parseItemRef,
  safeImageUrl,
  seeAllHref,
  shapeClasses,
  showSeeAll,
  tiltFactor,
  visibleSections,
  type PublicItem,
} from "./profile-view";

const item = (ref: number): PublicItem => ({
  ref,
  category: "MOVIE",
  title: `Item ${ref}`,
  year: 2000,
  imageUrl: null,
  note: null,
});

describe("tiltFactor and fixKind", () => {
  it("match the design system reference function", () => {
    expect(tiltFactor("perfect-days")).toBe(0.815);
    expect(tiltFactor("1")).toBe(0.644);
    expect(tiltFactor(42)).toBe(0.367);
    expect(fixKind("perfect-days")).toBe("pin");
  });

  it("is deterministic and keeps the angle between 0.36 and 1 in magnitude", () => {
    for (let ref = 1; ref <= 500; ref++) {
      const f = tiltFactor(ref);
      expect(tiltFactor(ref)).toBe(f);
      expect(Math.abs(f)).toBeGreaterThanOrEqual(0.36);
      expect(Math.abs(f)).toBeLessThanOrEqual(1);
    }
  });

  it("treats a numeric ref and its string the same", () => {
    expect(tiltFactor(77)).toBe(tiltFactor("77"));
    expect(fixKind(77)).toBe(fixKind("77"));
  });
});

describe("categories", () => {
  it("maps every medium to its card shape and listing route", () => {
    expect(CATEGORY_META.MOVIE.shape).toBe("poster");
    expect(CATEGORY_META.ANIME.shape).toBe("dvd");
    expect(CATEGORY_META.MUSIC.shape).toBe("vinyl");
    expect(CATEGORY_META.MANGA.route).toBe("mangas");
    expect(CATEGORY_META.MUSIC.route).toBe("musica");
  });

  it("marks books and mangas as volumes", () => {
    expect(shapeClasses("book")).toBe("mq-item--book mq-item--volume");
    expect(shapeClasses("manga")).toBe("mq-item--manga mq-item--volume");
    expect(shapeClasses("poster")).toBe("mq-item--poster");
  });

  it("keeps the intrinsic cover size at the medium ratio", () => {
    expect(CATEGORY_META.MOVIE.width / CATEGORY_META.MOVIE.height).toBeCloseTo(2 / 3, 2);
    expect(CATEGORY_META.MUSIC.width).toBe(CATEGORY_META.MUSIC.height);
    expect(CATEGORY_META.GAME.width / CATEGORY_META.GAME.height).toBeCloseTo(5 / 7, 2);
    expect(CATEGORY_META.MANGA.width / CATEGORY_META.MANGA.height).toBeCloseTo(5 / 7.4, 2);
  });

  it("writes counts in pt-BR", () => {
    expect(countLabel("MOVIE", 1)).toBe("1 filme");
    expect(countLabel("ANIME", 24)).toBe("24 animes");
    expect(countLabel("MUSIC", 3)).toBe("3 álbuns");
  });

  it("labels a card link with title, medium and year", () => {
    expect(itemLabel({ title: "Perfect Days", category: "MOVIE", year: 2023 })).toBe(
      "Perfect Days, filme de 2023",
    );
    expect(itemLabel({ title: "Hades", category: "GAME", year: null })).toBe("Hades, game");
  });
});

describe("which Ver todos to show", () => {
  const section = (total: number, shown: number) => ({
    total,
    items: Array.from({ length: shown }, (_, i) => item(i + 1)),
  });

  it("shows it only when the collection has more than the section shows", () => {
    expect(showSeeAll(section(24, 4), true)).toBe(true);
    expect(showSeeAll(section(4, 4), true)).toBe(false);
    expect(showSeeAll(section(0, 0), true)).toBe(false);
  });

  it("stays hidden while the listing page does not exist", () => {
    expect(showSeeAll(section(24, 4))).toBe(false);
  });

  it("builds the listing address with an encoded slug", () => {
    expect(seeAllHref("leandro", "MANGA")).toBe("/leandro/mangas");
    expect(seeAllHref("a b", "MUSIC")).toBe("/a%20b/musica");
  });
});

describe("profile content", () => {
  it("is empty without favorites and without items in any section", () => {
    const empty = { total: 0, mostRecent: false, category: "MOVIE" as const, items: [] };
    expect(isProfileEmpty({ favorites: [], categories: [] })).toBe(true);
    expect(isProfileEmpty({ favorites: [], categories: [empty] })).toBe(true);
    expect(isProfileEmpty({ favorites: [item(1)], categories: [] })).toBe(false);
    expect(visibleSections([empty, { ...empty, items: [item(2)] }])).toHaveLength(1);
  });
});

describe("links and images", () => {
  it("accepts only https cover URLs", () => {
    expect(safeImageUrl("https://image.tmdb.org/t/p/w500/a.jpg")).toBe(
      "https://image.tmdb.org/t/p/w500/a.jpg",
    );
    expect(safeImageUrl("http://example.com/a.jpg")).toBeNull();
    expect(safeImageUrl("javascript:alert(1)")).toBeNull();
    expect(safeImageUrl("data:image/png;base64,AAAA")).toBeNull();
    expect(safeImageUrl("not a url")).toBeNull();
    expect(safeImageUrl(null)).toBeNull();
  });

  it("accepts only digits as an item ref", () => {
    expect(parseItemRef("12")).toBe("12");
    expect(parseItemRef("12a")).toBeNull();
    expect(parseItemRef("../1")).toBeNull();
    expect(parseItemRef("1234567890123456789")).toBeNull();
    expect(parseItemRef("")).toBeNull();
    expect(parseItemRef(null)).toBeNull();
  });

  it("labels platform links", () => {
    expect(linkLabel("TMDB")).toBe("Ver no TMDB");
    expect(linkLabel("ANILIST")).toBe("Ver no AniList");
    expect(linkLabel("STEAM")).toBe("Ver na Steam");
    expect(linkLabel("NEWTHING")).toBe("Ver no Newthing");
  });
});

describe("origin caption", () => {
  it("formats dates in pt-BR using UTC", () => {
    expect(formatDate("2026-10-11T12:00:00Z")).toBe("11 out. 2026");
    expect(formatDate("2026-05-01T00:30:00Z")).toBe("1 maio 2026");
    expect(formatDate("garbage")).toBe("");
  });

  it("says hand-added when the only source is manual", () => {
    expect(originCaption([{ source: "MANUAL", firstSeenAt: "2026-10-11T12:00:00Z" }])).toBe(
      "Adicionado à mão",
    );
    expect(originCaption([])).toBe("Adicionado à mão");
  });

  it("names the platform and the first date", () => {
    expect(originCaption([{ source: "LETTERBOXD", firstSeenAt: "2026-10-11T12:00:00Z" }])).toBe(
      "Do Letterboxd, desde 11 out. 2026",
    );
    expect(originCaption([{ source: "ANILIST", firstSeenAt: "2026-07-14T12:00:00Z" }])).toBe(
      "Do AniList, desde 14 jul. 2026",
    );
  });

  it("lists several platforms and ignores the manual source", () => {
    expect(
      originCaption([
        { source: "MANUAL", firstSeenAt: "2026-01-01T00:00:00Z" },
        { source: "ANILIST", firstSeenAt: "2026-07-14T12:00:00Z" },
        { source: "LASTFM", firstSeenAt: "2026-03-02T12:00:00Z" },
      ]),
    ).toBe("De AniList e Last.fm, desde 2 mar. 2026");
  });

  it("drops the date when it is invalid", () => {
    expect(originCaption([{ source: "STEAM", firstSeenAt: "x" }])).toBe("Da Steam");
  });
});
