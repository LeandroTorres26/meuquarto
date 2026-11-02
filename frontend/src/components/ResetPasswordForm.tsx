"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AlertBox } from "@/components/AlertBox";
import { FieldError } from "@/components/FieldError";
import { confirmationError } from "@/lib/account-errors";
import { ApiError, confirmPasswordReset } from "@/lib/api-client";
import {
  RESET_PAGE,
  readResetToken,
  resetNetworkError,
  resetOutcomeFor,
  type ResetField,
} from "@/lib/password-reset";

type Stage = "loading" | "form" | "expired" | "done";

/**
 * S20: choose a new password with the token from the e-mailed link. The token is read from the
 * address once and the address is then rewritten without it, so it stays out of the history, of
 * screenshots and of anything that copies the URL. A reload therefore lands on the "link
 * inválido" state, which offers a new link.
 */
export function ResetPasswordForm() {
  const token = useRef<string | null | undefined>(undefined);
  const [stage, setStage] = useState<Stage>("loading");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<{ field: ResetField; message: string } | null>(null);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    // Effects run twice in development (Strict Mode): the second run must not find the token gone.
    if (token.current === undefined) {
      token.current = readResetToken(window.location.search);
      window.history.replaceState(window.history.state, "", RESET_PAGE);
    }
    setStage(token.current === null ? "expired" : "form");
  }, []);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending || !token.current) return;
    const mismatch = confirmationError(password, confirm);
    if (mismatch) {
      setError({ field: "confirm", message: mismatch });
      return;
    }
    setError(null);
    setSending(true);
    try {
      await confirmPasswordReset({ token: token.current, password });
      token.current = null;
      setPassword("");
      setConfirm("");
      setStage("done");
    } catch (e) {
      if (!(e instanceof ApiError)) {
        setError({ field: "form", message: resetNetworkError() });
      } else {
        const outcome = resetOutcomeFor(e.code, e.status, e.retryAfterSeconds);
        if (outcome.kind === "expired") {
          token.current = null;
          setStage("expired");
        } else {
          setError({ field: outcome.field, message: outcome.message });
        }
      }
    } finally {
      setSending(false);
    }
  }

  if (stage === "loading") {
    return <h1 className="t-h1">Nova senha</h1>;
  }

  if (stage === "expired") {
    return (
      <>
        <h1 className="t-h1">Link inválido ou expirado</h1>
        <p role="status">
          Esse link não funciona mais. Ele vale por 30 minutos e só pode ser usado uma vez.
        </p>
        <Link href="/esqueci-senha" className="mq-btn mq-btn--primary btn-block">
          Pedir um novo link
        </Link>
        <p className="t-sm">
          <Link href="/entrar">Voltar para entrar</Link>
        </p>
      </>
    );
  }

  if (stage === "done") {
    return (
      <>
        <h1 className="t-h1">Senha alterada</h1>
        <p role="status">
          Pronto, sua senha foi trocada. As sessões que estavam abertas foram encerradas.
        </p>
        <Link href="/entrar" className="mq-btn mq-btn--primary btn-block">
          Entrar
        </Link>
      </>
    );
  }

  const bad = (field: ResetField) => error?.field === field;
  const fieldMessage = (field: ResetField) => (bad(field) ? error?.message : undefined);

  return (
    <>
      <h1 className="t-h1">Nova senha</h1>
      {bad("form") && error && <AlertBox>{error.message}</AlertBox>}
      <form className="form" onSubmit={onSubmit}>
        <div className="field">
          <label className="label" htmlFor="nova">
            Nova senha
          </label>
          <input
            id="nova"
            className={`input${bad("password") ? " input--error" : ""}`}
            name="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            maxLength={128}
            required
            aria-invalid={bad("password") ? true : undefined}
            aria-describedby="nova-h nova-erro"
          />
          <p id="nova-h" className="help">
            Pelo menos 8 caracteres.
          </p>
          <FieldError id="nova-erro">{fieldMessage("password")}</FieldError>
        </div>
        <div className="field">
          <label className="label" htmlFor="conf">
            Repita a nova senha
          </label>
          <input
            id="conf"
            className={`input${bad("confirm") ? " input--error" : ""}`}
            name="confirm"
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
          className={`mq-btn mq-btn--primary btn-block${sending ? " is-loading" : ""}`}
          disabled={sending}
          aria-busy={sending}
        >
          Salvar nova senha
          {sending && <span className="mq-spinner" aria-hidden="true" />}
        </button>
      </form>
    </>
  );
}
