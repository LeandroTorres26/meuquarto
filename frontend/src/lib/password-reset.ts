import { messageFor, networkError } from "./auth-errors";

/** The reset page path. The token is read once from the address and then removed from it. */
export const RESET_PAGE = "/redefinir-senha";

/** Same shape the API accepts: 256 bits as 43 URL-safe characters. A quick check, the API decides. */
const TOKEN_SHAPE = /^[A-Za-z0-9_-]{43}$/;

/** The token in a `?token=` query string, or null when it is missing or cannot be one of ours. */
export function readResetToken(search: string): string | null {
  const value = new URLSearchParams(search).get("token");
  return value !== null && TOKEN_SHAPE.test(value) ? value : null;
}

/** Where a failed S20 submission is shown: under the new password, the repetition, or the whole form. */
export type ResetField = "password" | "confirm" | "form";

/** What S20 does after the API answers with an error. */
export type ResetOutcome =
  { kind: "expired" } | { kind: "error"; field: ResetField; message: string };

/**
 * Maps the error of POST /api/auth/password-reset/confirm. INVALID_RESET_TOKEN (unknown, tampered,
 * expired or already used, the API does not say which) moves the page to the "ask again" state; the
 * password rule codes belong under the new password; everything else is a form-level message.
 */
export function resetOutcomeFor(
  code: string | undefined,
  status?: number,
  retryAfterSeconds?: number,
): ResetOutcome {
  if (code === "INVALID_RESET_TOKEN") return { kind: "expired" };
  const mapped = messageFor(code, status, retryAfterSeconds);
  return {
    kind: "error",
    field: mapped.field === "password" ? "password" : "form",
    message: mapped.message,
  };
}

/** Message for POST /api/auth/password-reset failures. The API only fails on a bad body or throttling. */
export function requestErrorMessage(
  code: string | undefined,
  status?: number,
  retryAfterSeconds?: number,
): string {
  if (code === "INVALID_REQUEST" || status === 400) {
    return "Esse e-mail não parece válido. Confira e tente de novo.";
  }
  return messageFor(code, status, retryAfterSeconds).message;
}

/** The request never got an answer (offline, server down). */
export const resetNetworkError = (): string => networkError().message;

/** Shown after S19 is submitted: identical whether or not the account exists. */
export const REQUEST_SENT_MESSAGE = "Se existir uma conta com esse e-mail, enviamos um link.";
