"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { AlertBox } from "@/components/AlertBox";
import { ApiError, requestPasswordReset } from "@/lib/api-client";
import { REQUEST_SENT_MESSAGE, requestErrorMessage, resetNetworkError } from "@/lib/password-reset";

/** S19: ask for a reset link. The confirmation is the same whether or not the account exists. */
export function ForgotPasswordForm() {
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const [sending, setSending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending) return;
    setSending(true);
    setError(null);
    try {
      await requestPasswordReset({ email: email.trim() });
      setSent(true);
    } catch (e) {
      setError(
        e instanceof ApiError
          ? requestErrorMessage(e.code, e.status, e.retryAfterSeconds)
          : resetNetworkError(),
      );
    } finally {
      setSending(false);
    }
  }

  if (sent) {
    return (
      <>
        <h1 className="t-h1">Confira seu e-mail</h1>
        <p role="status">{REQUEST_SENT_MESSAGE}</p>
        <p className="t-sm">
          O link vale por 30 minutos e só funciona uma vez. Se não chegar, olhe a caixa de spam.
        </p>
        <p className="t-sm">
          <Link href="/entrar">Voltar para entrar</Link>
        </p>
      </>
    );
  }

  return (
    <>
      <h1 className="t-h1">Esqueci minha senha</h1>
      <p>Digite o e-mail da sua conta e enviamos um link para você escolher uma nova senha.</p>
      {error && <AlertBox>{error}</AlertBox>}
      <form className="form" onSubmit={onSubmit}>
        <div className="field">
          <label className="label" htmlFor="email">
            E-mail
          </label>
          <input
            id="email"
            className="input"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            maxLength={254}
            required
          />
        </div>
        <button
          type="submit"
          className={`mq-btn mq-btn--primary btn-block${sending ? " is-loading" : ""}`}
          disabled={sending}
          aria-busy={sending}
        >
          Enviar link
          {sending && <span className="mq-spinner" aria-hidden="true" />}
        </button>
      </form>
      <p className="t-sm">
        Lembrou? <Link href="/entrar">Voltar para entrar</Link>
      </p>
    </>
  );
}
