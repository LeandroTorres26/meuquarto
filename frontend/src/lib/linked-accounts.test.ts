import { describe, expect, it } from "vitest";
import {
  comingSoonProviders,
  cooldownMinutes,
  cooldownUntilFromRetryAfter,
  linkableProviders,
  linkErrorMessage,
  minutesUntil,
  relativeTimePtBr,
  syncErrorMessage,
  syncSuccessMessage,
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
    status: "ACTIVE",
    lastSyncedAt: null,
    lastError: null,
    nextManualSyncAt: null,
  });

  it("only offers linking for platforms with a backend provider that are not linked yet", () => {
    expect(linkableProviders([]).map((p) => p.id)).toEqual(["LETTERBOXD", "ANILIST", "GOODREADS"]);
    expect(linkableProviders([account("ANILIST")]).map((p) => p.id)).toEqual([
      "LETTERBOXD",
      "GOODREADS",
    ]);
  });

  it("lists the platforms still without a provider as coming soon", () => {
    expect(comingSoonProviders().map((p) => p.id)).toEqual(["STEAM", "LASTFM"]);
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
