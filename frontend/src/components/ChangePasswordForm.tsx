"use client";

import { useState, type FormEvent } from "react";
import { AlertBox } from "@/components/AlertBox";
import { FieldError } from "@/components/FieldError";
import {
  accountMessageFor,
  accountNetworkError,
  confirmationError,
  type AccountError,
} from "@/lib/account-errors";
import { ApiError, changePassword } from "@/lib/api-client";

/** S14, "Trocar senha": current password, new password, repetition checked before sending. */
export function ChangePasswordForm() {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<AccountError | null>(null);
  const [saved, setSaved] = useState(false);
  const [sending, setSending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending) return;
    setSaved(false);
    const mismatch = confirmationError(next, confirm);
    if (mismatch) {
      setError({ field: "confirm", message: mismatch });
      return;
    }
    setError(null);
    setSending(true);
    try {
      await changePassword({ currentPassword: current, newPassword: next });
      setCurrent("");
      setNext("");
      setConfirm("");
      setSaved(true);
    } catch (e) {
      setError(
        e instanceof ApiError
          ? accountMessageFor(e.code, e.status, e.retryAfterSeconds)
          : accountNetworkError(),
      );
    } finally {
      setSending(false);
    }
  }

  const bad = (field: AccountError["field"]) => error?.field === field;
  const fieldMessage = (field: AccountError["field"]) => (bad(field) ? error?.message : undefined);

  return (
    <form className="fieldset" aria-labelledby="senha-h" onSubmit={onSubmit}>
      <h2
        id="senha-h"
        className="t-h2"
        style={{ marginBottom: "calc(var(--space-4) - var(--space-6))" }}
      >
        Trocar senha
      </h2>
      {bad("form") && error && <AlertBox>{error.message}</AlertBox>}
      <div className="field">
        <label className="label" htmlFor="atual">
          Senha atual
        </label>
        <input
          id="atual"
          className={`input${bad("current") ? " input--error" : ""}`}
          type="password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          autoComplete="current-password"
          maxLength={128}
          required
          aria-invalid={bad("current") ? true : undefined}
          aria-describedby="atual-erro"
        />
        <FieldError id="atual-erro">{fieldMessage("current")}</FieldError>
      </div>
      <div className="field">
        <label className="label" htmlFor="nova">
          Nova senha
        </label>
        <input
          id="nova"
          className={`input${bad("next") ? " input--error" : ""}`}
          type="password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          autoComplete="new-password"
          maxLength={128}
          required
          aria-invalid={bad("next") ? true : undefined}
          aria-describedby="nova-h nova-erro"
        />
        <p id="nova-h" className="help">
          Pelo menos 8 caracteres.
        </p>
        <FieldError id="nova-erro">{fieldMessage("next")}</FieldError>
      </div>
      <div className="field">
        <label className="label" htmlFor="conf">
          Repita a nova senha
        </label>
        <input
          id="conf"
          className={`input${bad("confirm") ? " input--error" : ""}`}
          type="password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password"
          maxLength={128}
          required
          aria-invalid={bad("confirm") ? true : undefined}
          aria-describedby="conf-erro"
        />
        <FieldError id="conf-erro">{fieldMessage("confirm")}</FieldError>
      </div>
      <button
        type="submit"
        className={`mq-btn mq-btn--primary self-start${sending ? " is-loading" : ""}`}
        disabled={sending}
        aria-busy={sending}
      >
        Salvar nova senha
        {sending && <span className="mq-spinner" aria-hidden="true" />}
      </button>
      <p role="status" className="t-sm">
        {saved
          ? "Senha trocada. As outras sessões abertas com a senha antiga foram encerradas."
          : ""}
      </p>
    </form>
  );
}
