import { describe, expect, it } from "vitest";
import {
  AUTO_PLATFORM,
  entryCaption,
  NOW_VERB,
  nowMessage,
  nowStatus,
  pickerLabel,
  withEntry,
  type NowEntry,
} from "./now";

const NOW = Date.parse("2026-10-10T12:00:00Z");

const shown = (source: string) => ({
  collectionItemId: 1,
  title: "Perfect Days",
  year: 2023,
  imageUrl: null,
  source,
  at: "2026-10-08T12:00:00Z",
});

const entry = (patch: Partial<NowEntry>): NowEntry => ({
  category: "MOVIE",
  mode: "AUTOMATIC",
  automaticAvailable: true,
  shown: null,
  ...patch,
});

describe("NOW_VERB", () => {
  it("follows the NowCard verbs", () => {
    expect(NOW_VERB.MOVIE).toBe("Assistindo");
    expect(NOW_VERB.SERIES).toBe("Assistindo");
    expect(NOW_VERB.ANIME).toBe("Assistindo");
    expect(NOW_VERB.BOOK).toBe("Lendo");
    expect(NOW_VERB.MANGA).toBe("Lendo");
    expect(NOW_VERB.MUSIC).toBe("Ouvindo");
    expect(NOW_VERB.GAME).toBe("Jogando");
  });
});

describe("nowStatus", () => {
  it("joins the platform and how long ago", () => {
    expect(nowStatus("LETTERBOXD", "2026-10-08T12:00:00Z", NOW)).toBe("Letterboxd, há 2 dias");
    expect(nowStatus("LASTFM", "2026-10-10T09:00:00Z", NOW)).toBe("Last.fm, há 3 horas");
  });

  it("says the owner chose it for a pin", () => {
    expect(nowStatus("MANUAL", null, NOW)).toBe("Escolhido por você");
  });

  it("keeps just the platform when the time is missing or invalid", () => {
    expect(nowStatus("STEAM", null, NOW)).toBe("Steam");
  });
});

describe("entryCaption", () => {
  it("credits the platform when automatic has an item", () => {
    expect(entryCaption(entry({ shown: shown("LETTERBOXD") })).text).toBe(
      "Do Letterboxd, muda sozinho",
    );
    expect(entryCaption(entry({ category: "GAME", shown: shown("STEAM") })).text).toBe(
      "Da Steam, muda sozinho",
    );
  });

  it("offers to connect the platform when nothing was synced", () => {
    expect(entryCaption(entry({ category: "BOOK" }))).toEqual({
      text: "Nada sincronizado ainda.",
      connect: "GOODREADS",
    });
  });

  it("explains that Séries is manual only", () => {
    expect(entryCaption(entry({ category: "SERIES", automaticAvailable: false })).text).toBe(
      "Séries não têm plataforma para sincronizar. Escolha à mão.",
    );
  });

  it("says a pin was chosen by the owner and still offers the automatic platform", () => {
    expect(
      entryCaption(entry({ category: "BOOK", mode: "PINNED", shown: shown("MANUAL") })),
    ).toEqual({ text: "Escolhido por você.", connect: "GOODREADS" });
    expect(
      entryCaption(entry({ category: "SERIES", mode: "PINNED", shown: shown("MANUAL") })),
    ).toEqual({ text: "Escolhido por você." });
  });

  it("says a hidden category is hidden", () => {
    expect(entryCaption(entry({ mode: "HIDDEN" })).text).toBe("Escondido no perfil.");
  });
});

describe("AUTO_PLATFORM", () => {
  it("has no platform for Séries only", () => {
    expect(Object.entries(AUTO_PLATFORM).filter(([, p]) => p === null)).toEqual([["SERIES", null]]);
  });
});

describe("pickerLabel", () => {
  it("offers to swap a pin and to choose otherwise", () => {
    expect(pickerLabel(entry({ mode: "PINNED" }))).toBe("Trocar obra");
    expect(pickerLabel(entry({ mode: "AUTOMATIC" }))).toBe("Escolher obra");
  });
});

describe("nowMessage", () => {
  it("translates the Agora codes and falls back to the collection ones", () => {
    expect(nowMessage("ITEM_HIDDEN")).toContain("ocultas");
    expect(nowMessage("NOT_IN_COLLECTION")).toContain("coleção");
    expect(nowMessage("TOO_MANY_REQUESTS", 429)).toContain("Espere");
    expect(nowMessage(undefined, 401)).toBe("Sua sessão expirou. Entre de novo.");
  });
});

describe("withEntry", () => {
  it("swaps only the saved category", () => {
    const list = [entry({ category: "MOVIE" }), entry({ category: "ANIME" })];
    const saved = entry({ category: "ANIME", mode: "HIDDEN" });

    expect(withEntry(list, saved)).toEqual([list[0], saved]);
  });
});
