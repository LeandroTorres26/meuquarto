import { messageFor, networkError } from "./auth-errors";

/** Where an account-screen error is shown: under one of the fields, or above the form. */
export type AccountField = "current" | "next" | "confirm" | "form";

export type AccountError = { field: AccountField; message: string };

const WRONG_PASSWORD = "A senha não confere. Confira e tente de novo.";

/**
 * Maps the error of POST /api/account/password and /api/account/delete to the field and the pt-BR
 * text. WRONG_PASSWORD belongs under the password being confirmed; the password-rule codes belong
 * under the new password; everything else (including throttling with the Retry-After wait) is a
 * form-level message.
 */
export function accountMessageFor(
  code: string | undefined,
  status?: number,
  retryAfterSeconds?: number,
): AccountError {
  if (code === "WRONG_PASSWORD") return { field: "current", message: WRONG_PASSWORD };
  const mapped = messageFor(code, status, retryAfterSeconds);
  return { field: mapped.field === "password" ? "next" : "form", message: mapped.message };
}

/** Client-side check before sending: the repeated password must match. Null when it does. */
export function confirmationError(newPassword: string, confirmation: string): string | null {
  return newPassword === confirmation ? null : "As senhas não são iguais.";
}

/** The request never got an answer (offline, server down). */
export function accountNetworkError(): AccountError {
  return { field: "form", message: networkError().message };
}
