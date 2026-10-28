import { describe, expect, it } from "vitest";
import { accountMessageFor, confirmationError } from "./account-errors";

describe("accountMessageFor", () => {
  it("puts WRONG_PASSWORD under the password being confirmed", () => {
    const result = accountMessageFor("WRONG_PASSWORD", 400);
    expect(result.field).toBe("current");
    expect(result.message.length).toBeGreaterThan(0);
  });

  it.each([
    "PASSWORD_TOO_SHORT",
    "PASSWORD_TOO_LONG",
    "PASSWORD_TOO_COMMON",
    "PASSWORD_SAME_AS_EMAIL",
  ])("puts %s under the new password", (code) => {
    expect(accountMessageFor(code, 400).field).toBe("next");
  });

  it("shows request and unknown errors at form level", () => {
    expect(accountMessageFor("INVALID_REQUEST", 400).field).toBe("form");
    expect(accountMessageFor("SOMETHING_NEW", 500).field).toBe("form");
    expect(accountMessageFor(undefined, 502).field).toBe("form");
  });

  it("tells how many minutes to wait on throttling", () => {
    const result = accountMessageFor("TOO_MANY_ATTEMPTS", 429, 600);
    expect(result.field).toBe("form");
    expect(result.message).toBe("Muitas tentativas. Tente de novo em 10 minutos.");
  });
});

describe("confirmationError", () => {
  it("accepts a matching repetition", () => {
    expect(confirmationError("correct horse", "correct horse")).toBeNull();
  });

  it("rejects a different repetition, case and spaces included", () => {
    expect(confirmationError("correct horse", "correct Horse")).not.toBeNull();
    expect(confirmationError("correct horse", "correct horse ")).not.toBeNull();
    expect(confirmationError("abc", "")).not.toBeNull();
  });
});
