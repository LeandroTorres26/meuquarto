import { describe, expect, it } from "vitest";
import { avatarFileProblem, charCount, profileMessage, themeCaption } from "./profile-editor";

describe("themes (BR-05)", () => {
  it("explains a locked theme in pt-BR under the theme field", () => {
    const locked = profileMessage("THEME_LOCKED");
    expect(locked.field).toBe("theme");
    expect(locked.message).toContain("loja");
  });

  it("captions free, bought and store themes", () => {
    expect(themeCaption({ id: "dia", premium: false, owned: false, locked: false })).toBe("grátis");
    expect(themeCaption({ id: "estufa", premium: true, owned: true, locked: false })).toBe(
      "comprado",
    );
    expect(themeCaption({ id: "locadora", premium: true, owned: false, locked: true })).toBe(
      "na loja",
    );
  });
});

describe("profileMessage", () => {
  it("puts each backend code under its field in pt-BR", () => {
    expect(profileMessage("DISPLAY_NAME_REQUIRED").field).toBe("name");
    expect(profileMessage("BIO_TOO_LONG").field).toBe("bio");
    expect(profileMessage("SLUG_TAKEN")).toEqual({
      field: "slug",
      message: "Esse endereço já está em uso.",
    });
    expect(profileMessage("THEME_UNKNOWN").field).toBe("theme");
    expect(profileMessage("AVATAR_UNSUPPORTED_TYPE").message).toContain("JPG ou PNG");
    expect(profileMessage("AVATAR_DIMENSIONS_TOO_LARGE").field).toBe("avatar");
  });

  it("keeps the specific message of a known code even on a 5xx", () => {
    expect(profileMessage("STORAGE_UNAVAILABLE", 503).field).toBe("avatar");
    expect(profileMessage("THEME_LOCKED", 400).message).toContain("loja");
  });

  it("offers a retry for a 5xx without a known code", () => {
    const down = profileMessage(undefined, 502);
    expect(down.field).toBe("form");
    expect(down.retry).toBe(true);
    expect(down.message).not.toMatch(/Algo deu errado/);
    expect(profileMessage(undefined, 400).retry).toBeUndefined();
  });

  it("falls back to a form-level message for anything else", () => {
    const unknown = profileMessage("WHATEVER", 500);
    expect(unknown.field).toBe("form");
    expect(unknown.message.length).toBeGreaterThan(0);
    expect(profileMessage(undefined, 429, 120).message).toContain("2 minutos");
  });
});

describe("avatarFileProblem", () => {
  it("accepts JPEG and PNG up to 2 MB", () => {
    expect(avatarFileProblem({ size: 1000, type: "image/jpeg" })).toBeNull();
    expect(avatarFileProblem({ size: 2 * 1024 * 1024, type: "image/png" })).toBeNull();
  });

  it("refuses empty, big and other types", () => {
    expect(avatarFileProblem({ size: 0, type: "image/png" })).toContain("Escolha");
    expect(avatarFileProblem({ size: 2 * 1024 * 1024 + 1, type: "image/png" })).toContain("2 MB");
    expect(avatarFileProblem({ size: 10, type: "image/webp" })).toContain("JPG ou PNG");
    expect(avatarFileProblem({ size: 10, type: "" })).toContain("JPG ou PNG");
  });
});

describe("charCount", () => {
  it("counts code points like the server", () => {
    expect(charCount("abc")).toBe(3);
    expect(charCount("\u{1F3AC}\u{1F3AC}")).toBe(2);
  });
});
