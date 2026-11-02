import { describe, expect, it } from "vitest";
import { avatarFileProblem, charCount, profileMessage } from "./profile-editor";

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
