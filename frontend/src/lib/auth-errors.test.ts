import { describe, expect, it } from "vitest";
import { messageFor, slugReasonMessage, waitMessage } from "./auth-errors";

describe("messageFor", () => {
  it.each([
    ["INVALID_EMAIL", "email"],
    ["EMAIL_TAKEN", "email"],
    ["PASSWORD_TOO_SHORT", "password"],
    ["PASSWORD_TOO_LONG", "password"],
    ["PASSWORD_TOO_COMMON", "password"],
    ["PASSWORD_SAME_AS_EMAIL", "password"],
    ["SLUG_TOO_SHORT", "slug"],
    ["SLUG_TOO_LONG", "slug"],
    ["SLUG_INVALID_CHARACTERS", "slug"],
    ["SLUG_INVALID_HYPHEN", "slug"],
    ["SLUG_RESERVED", "slug"],
    ["SLUG_TAKEN", "slug"],
    ["INVALID_REQUEST", "form"],
    ["INVALID_CREDENTIALS", "form"],
  ])("maps %s to the %s field with a message", (code, field) => {
    const result = messageFor(code, 400);
    expect(result.field).toBe(field);
    expect(result.message.length).toBeGreaterThan(0);
  });

  it("falls back to a generic form message for unknown codes", () => {
    expect(messageFor("SOMETHING_NEW", 500).field).toBe("form");
    expect(messageFor(undefined, 502).field).toBe("form");
  });

  it("uses the Retry-After wait for throttling", () => {
    expect(messageFor("TOO_MANY_ATTEMPTS", 429, 600).message).toBe(
      "Muitas tentativas. Tente de novo em 10 minutos.",
    );
    expect(messageFor(undefined, 429, 30).message).toContain("1 minuto.");
  });
});

describe("waitMessage", () => {
  it("rounds up to whole minutes and handles the singular", () => {
    expect(waitMessage(61)).toBe("Muitas tentativas. Tente de novo em 2 minutos.");
    expect(waitMessage(60)).toBe("Muitas tentativas. Tente de novo em 1 minuto.");
  });

  it("does not invent a number without Retry-After", () => {
    expect(waitMessage()).toBe("Muitas tentativas. Tente de novo em alguns minutos.");
  });
});

describe("slugReasonMessage", () => {
  it("covers every availability reason", () => {
    for (const reason of [
      "TOO_SHORT",
      "TOO_LONG",
      "INVALID_CHARACTERS",
      "INVALID_HYPHEN",
      "RESERVED",
      "TAKEN",
    ]) {
      expect(slugReasonMessage(reason)).not.toBe(slugReasonMessage("UNKNOWN"));
    }
  });
});
