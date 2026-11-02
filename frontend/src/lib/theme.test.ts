import { describe, expect, it } from "vitest";
import { THEME_BOOT_SCRIPT, THEME_IDS, isThemeId, themeCookie, themeFromCookies } from "./theme";

describe("themeFromCookies", () => {
  it("reads the kept theme among other cookies", () => {
    expect(themeFromCookies("XSRF-TOKEN=abc; mq_theme=dia; other=1")).toBe("dia");
    expect(themeFromCookies("mq_theme=estufa")).toBe("estufa");
  });

  it("falls back to noite when the cookie is missing, empty or unknown", () => {
    expect(themeFromCookies(null)).toBe("noite");
    expect(themeFromCookies("")).toBe("noite");
    expect(themeFromCookies("mq_theme=")).toBe("noite");
    expect(themeFromCookies("mq_theme=<script>")).toBe("noite");
    expect(themeFromCookies("not_mq_theme=dia")).toBe("noite");
  });
});

describe("themeCookie", () => {
  it("keeps a theme for a year on every path", () => {
    expect(themeCookie("locadora", false)).toBe(
      "mq_theme=locadora; Path=/; Max-Age=31536000; SameSite=Lax",
    );
  });

  it("is Secure on https and expires at once when deleted", () => {
    expect(themeCookie("dia", true)).toContain("; Secure");
    expect(themeCookie(null, true)).toBe("mq_theme=; Path=/; Max-Age=0; SameSite=Lax; Secure");
  });
});

describe("THEME_BOOT_SCRIPT", () => {
  const run = (cookie: string) => {
    let applied: string | null = null;
    const document = {
      cookie,
      documentElement: { setAttribute: (_: string, value: string) => (applied = value) },
    };
    new Function("document", THEME_BOOT_SCRIPT)(document);
    return applied;
  };

  it("applies a known theme from the cookie", () => {
    expect(run("a=1; mq_theme=dia")).toBe("dia");
  });

  it("leaves the server default for a missing or unknown value", () => {
    expect(run("a=1")).toBeNull();
    expect(run("mq_theme=hacker")).toBeNull();
  });
});

it("knows exactly the themes of the design tokens", () => {
  expect(THEME_IDS).toEqual(["noite", "dia", "locadora", "estufa"]);
  expect(isThemeId("dia")).toBe(true);
  expect(isThemeId("Dia")).toBe(false);
});
