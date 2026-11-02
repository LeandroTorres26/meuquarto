import { describe, expect, it } from "vitest";
import {
  REQUEST_SENT_MESSAGE,
  readResetToken,
  requestErrorMessage,
  resetOutcomeFor,
} from "./password-reset";

const TOKEN = "Abc_def-123".padEnd(43, "x");

describe("readResetToken", () => {
  it("reads a well-formed token from the query string", () => {
    expect(readResetToken(`?token=${TOKEN}`)).toBe(TOKEN);
    expect(readResetToken(`?a=1&token=${TOKEN}`)).toBe(TOKEN);
  });

  it.each([
    "",
    "?",
    "?token=",
    "?token=short",
    `?token=${TOKEN}x`,
    `?token=${TOKEN.slice(1)}!`,
    "?other=" + TOKEN,
  ])("returns null for %j", (search) => {
    expect(readResetToken(search)).toBeNull();
  });
});

describe("resetOutcomeFor", () => {
  it("sends every unusable link to the ask-again state", () => {
    expect(resetOutcomeFor("INVALID_RESET_TOKEN", 400)).toEqual({ kind: "expired" });
  });

  it.each([
    "PASSWORD_TOO_SHORT",
    "PASSWORD_TOO_LONG",
    "PASSWORD_TOO_COMMON",
    "PASSWORD_SAME_AS_EMAIL",
  ])("puts %s under the new password", (code) => {
    const outcome = resetOutcomeFor(code, 400);
    expect(outcome.kind).toBe("error");
    expect(outcome.kind === "error" && outcome.field).toBe("password");
  });

  it("shows throttling and unknown errors at form level", () => {
    const throttled = resetOutcomeFor("TOO_MANY_REQUESTS", 429, 120);
    expect(throttled).toEqual({
      kind: "error",
      field: "form",
      message: "Muitas tentativas. Tente de novo em 2 minutos.",
    });
    expect(resetOutcomeFor(undefined, 500).kind).toBe("error");
  });
});

describe("requestErrorMessage", () => {
  it("explains a bad e-mail and throttling", () => {
    expect(requestErrorMessage("INVALID_REQUEST", 400)).toContain("e-mail");
    expect(requestErrorMessage("TOO_MANY_REQUESTS", 429, 3600)).toBe(
      "Muitas tentativas. Tente de novo em 60 minutos.",
    );
  });
});

describe("REQUEST_SENT_MESSAGE", () => {
  it("is the text from docs/screens.md S19", () => {
    expect(REQUEST_SENT_MESSAGE).toBe("Se existir uma conta com esse e-mail, enviamos um link.");
  });
});
