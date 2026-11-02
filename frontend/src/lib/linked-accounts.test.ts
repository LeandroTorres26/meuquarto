import { describe, expect, it } from "vitest";
import {
  cooldownMinutes,
  cooldownUntilFromRetryAfter,
  isLinkable,
  linkErrorMessage,
  inProviderOrder,
  minutesUntil,
  profileLinkText,
  PROVIDERS,
  providerInfo,
  relativeTimePtBr,
  syncErrorMessage,
  syncNote,
  syncSuccessMessage,
  unlinkedProviders,
  type LinkedAccount,
} from "./linked-accounts";

const NOW = Date.parse("2026-11-01T12:00:00Z");
const MIN = 60_000;
const ago = (ms: number) => new Date(NOW - ms).toISOString();

describe("relativeTimePtBr", () => {
  it("speaks minutes, hours and days in pt-BR with singular and plural", () => {
    expect(relativeTimePtBr(ago(10_000), NOW)).toBe("agora há pouco");
    expect(relativeTimePtBr(ago(MIN), NOW)).toBe("há 1 minuto");
    expect(relativeTimePtBr(ago(3 * MIN + 5000), NOW)).toBe("há 3 minutos");
    expect(relativeTimePtBr(ago(60 * MIN), NOW)).toBe("há 1 hora");
    expect(relativeTimePtBr(ago(2 * 60 * MIN), NOW)).toBe("há 2 horas");
    expect(relativeTimePtBr(ago(24 * 60 * MIN), NOW)).toBe("há 1 dia");
    expect(relativeTimePtBr(ago(5 * 24 * 60 * MIN), NOW)).toBe("há 5 dias");
  });

  it("treats a future instant (clock skew) as just now", () => {
    expect(relativeTimePtBr(new Date(NOW + 5 * MIN).toISOString(), NOW)).toBe("agora há pouco");
  });
});

describe("cooldown", () => {
  it("rounds the remaining time up to whole minutes", () => {
    expect(cooldownMinutes(new Date(NOW + 12 * MIN).toISOString(), NOW)).toBe(12);
    expect(cooldownMinutes(new Date(NOW + 61_000).toISOString(), NOW)).toBe(2);
    expect(cooldownMinutes(new Date(NOW + 1000).toISOString(), NOW)).toBe(1);
  });

  it("is zero when never synced, already past or unreadable", () => {
    expect(cooldownMinutes(null, NOW)).toBe(0);
    expect(cooldownMinutes(new Date(NOW - MIN).toISOString(), NOW)).toBe(0);
    expect(cooldownMinutes("nope", NOW)).toBe(0);
  });

  it("turns Retry-After seconds into an end instant and minutes left", () => {
    const until = cooldownUntilFromRetryAfter(600, NOW);
    expect(until).toBe(NOW + 600_000);
    expect(minutesUntil(until, NOW)).toBe(10);
    expect(minutesUntil(until, until)).toBe(0);
    expect(cooldownUntilFromRetryAfter(undefined, NOW)).toBe(0);
  });
});

describe("providers", () => {
  const account = (provider: LinkedAccount["provider"]): LinkedAccount => ({
    id: 1,
    provider,
    username: "x",
    profileUrl: null,
    syncs: providerInfo(provider).syncs,
    status: "ACTIVE",
    lastSyncedAt: null,
    lastError: null,
    nextManualSyncAt: null,
  });

  it("lists every platform not linked yet, in the fixed order of the cards", () => {
    expect(unlinkedProviders([]).map((p) => p.id)).toEqual([
      "LETTERBOXD",
      "ANILIST",
      "MYANIMELIST",
      "LASTFM",
      "STEAM",
      "GOODREADS",
      "BACKLOGGD",
      "TRAKT",
      "STORYGRAPH",
    ]);
    expect(unlinkedProviders([account("ANILIST"), account("TRAKT")]).map((p) => p.id)).toEqual([
      "LETTERBOXD",
      "MYANIMELIST",
      "LASTFM",
      "STEAM",
      "GOODREADS",
      "BACKLOGGD",
      "STORYGRAPH",
    ]);
  });

  it("orders linked accounts like the platforms, not like the date they were linked", () => {
    expect(
      inProviderOrder([account("STORYGRAPH"), account("LETTERBOXD"), account("LASTFM")]).map(
        (a) => a.provider,
      ),
    ).toEqual(["LETTERBOXD", "LASTFM", "STORYGRAPH"]);
  });

  it("says which platforms update by themselves and which are only a link", () => {
    expect(PROVIDERS.filter((p) => !p.syncs).map((p) => p.id)).toEqual([
      "BACKLOGGD",
      "TRAKT",
      "STORYGRAPH",
    ]);
    expect(syncNote(providerInfo("ANILIST"))).toEqual({
      lead: "Atualiza sozinho.",
      text: "Completos, assistindo, pausados e revendo.",
    });
    expect(syncNote(providerInfo("BACKLOGGD")).lead).toBe("Só o link.");
  });

  it("is honest about Letterboxd: only the diary, the rest is by hand", () => {
    expect(syncNote(providerInfo("LETTERBOXD")).text).toMatch(/diário/);
    expect(syncNote(providerInfo("LETTERBOXD")).text).toMatch(/destaca à mão/);
  });

  it("describes what Last.fm brings: albums played a lot and what is playing, never each play", () => {
    const lastfm = providerInfo("LASTFM");
    expect(lastfm.available).toBe(true);
    expect(lastfm.categories).toEqual(["MUSIC"]);
    expect(syncNote(lastfm).text).toMatch(/^Os álbuns que você mais ouve/);
    expect(lastfm.hint).toMatch(/atividade recente precisa estar pública/);
  });

  it("keeps Steam as coming soon: it has no provider yet", () => {
    expect(PROVIDERS.filter((p) => !p.available).map((p) => p.id)).toEqual(["STEAM"]);
  });

  it("describes MyAnimeList: anime and manga, updates by itself, nothing twice with AniList", () => {
    const mal = providerInfo("MYANIMELIST");
    expect(mal.name).toBe("MyAnimeList");
    expect(mal.categories).toEqual(["ANIME", "MANGA"]);
    expect(mal.syncs).toBe(true);
    expect(mal.label).toBe("Usuário do MyAnimeList");
    expect(mal.hint).toBe("Como aparece em myanimelist.net/profile/nome.");
    expect(syncNote(mal)).toEqual({
      lead: "Atualiza sozinho.",
      text: "Completos, assistindo, pausados e revendo. Com o AniList também vinculado, nada aparece duas vezes.",
    });
  });

  it("puts MyAnimeList between AniList and Last.fm, the order of the profile links", () => {
    expect(PROVIDERS.slice(0, 4).map((p) => p.id)).toEqual([
      "LETTERBOXD",
      "ANILIST",
      "MYANIMELIST",
      "LASTFM",
    ]);
  });

  it("offers the form only when the screen and the server both say the platform works", () => {
    const mal = providerInfo("MYANIMELIST");
    const steam = providerInfo("STEAM");
    expect(isLinkable(mal, [{ provider: "MYANIMELIST", available: true }])).toBe(true);
    expect(isLinkable(mal, [{ provider: "MYANIMELIST", available: false }])).toBe(false); // no client id
    expect(isLinkable(mal, [{ provider: "ANILIST", available: true }])).toBe(true); // not listed: screen decides
    expect(isLinkable(mal, null)).toBe(true); // the server could not be asked
    expect(isLinkable(steam, [{ provider: "STEAM", available: true }])).toBe(false); // no provider yet
    expect(isLinkable(steam, null)).toBe(false);
  });

  it("shows the profile link without the scheme and the trailing slash", () => {
    expect(profileLinkText("https://letterboxd.com/leandro/")).toBe("letterboxd.com/leandro");
    expect(profileLinkText("https://www.last.fm/user/leandro_t")).toBe(
      "www.last.fm/user/leandro_t",
    );
  });
});

describe("messages", () => {
  it("maps link errors to the field and text", () => {
    expect(linkErrorMessage("INVALID_USERNAME", 400, "AniList").field).toBe("username");
    expect(linkErrorMessage("ACCOUNT_NOT_FOUND_ON_PROVIDER", 400, "AniList").message).toMatch(
      /não existe ou está privado/,
    );
    expect(linkErrorMessage("ALREADY_LINKED", 409, "AniList").message).toMatch(/já vinculou/);
    expect(linkErrorMessage("PROVIDER_UNAVAILABLE", 503, "AniList").field).toBe("form");
    expect(linkErrorMessage("???", 500, "AniList").message).toMatch(/Algo deu errado/);
  });

  it("explains throttling with the Retry-After wait", () => {
    expect(linkErrorMessage(undefined, 429, "AniList", 120).message).toBe(
      "Muitas tentativas. Tente de novo em 2 minutos.",
    );
    expect(linkErrorMessage(undefined, 429, "AniList").message).toMatch(/alguns minutos/);
  });

  it("maps sync error codes and falls back to a generic text", () => {
    expect(syncErrorMessage("PROVIDER_UNAVAILABLE", "Letterboxd")).toMatch(
      /Letterboxd não respondeu/,
    );
    expect(syncErrorMessage("ACCOUNT_NOT_FOUND_ON_PROVIDER", "AniList")).toMatch(/privado/);
    expect(syncErrorMessage("SYNC_FAILED", "AniList")).toMatch(/Não conseguimos atualizar/);
    expect(syncErrorMessage(null, "AniList")).toMatch(/Não conseguimos atualizar/);
  });

  it("counts synced works", () => {
    expect(syncSuccessMessage(12)).toBe("12 obras sincronizadas");
    expect(syncSuccessMessage(1)).toBe("1 obra sincronizada");
    expect(syncSuccessMessage(0)).toMatch(/Tudo em dia/);
  });
});
