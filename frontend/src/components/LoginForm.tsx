"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AlertBox } from "@/components/AlertBox";
import { ApiError, logIn } from "@/lib/api-client";
import { messageFor, networkError } from "@/lib/auth-errors";

/** S3: sign in, with the error state of docs/design/telas/entrar.html. */
export function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<{ message: string; credentials: boolean } | null>(null);
  const [sending, setSending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending) return;
    setSending(true);
    setError(null);
    try {
      await logIn({ email: email.trim(), password });
      router.replace("/painel/conta");
    } catch (e) {
      const mapped =
        e instanceof ApiError ? messageFor(e.code, e.status, e.retryAfterSeconds) : networkError();
      setError({
        message: mapped.message,
        credentials: e instanceof ApiError && e.code === "INVALID_CREDENTIALS",
      });
      setSending(false);
    }
  }

  return (
    <>
      <h1 className="t-h1">Entrar</h1>
      {error && <AlertBox>{error.message}</AlertBox>}
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
        <div className="field">
          <label className="label" htmlFor="senha">
            Senha
          </label>
          <input
            id="senha"
            className={`input${error?.credentials ? " input--error" : ""}`}
            name="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            maxLength={128}
            required
            aria-invalid={error?.credentials ? true : undefined}
          />
        </div>
        <button
          type="submit"
          className={`mq-btn mq-btn--primary btn-block${sending ? " is-loading" : ""}`}
          disabled={sending}
          aria-busy={sending}
        >
          Entrar
          {sending && <span className="mq-spinner" aria-hidden="true" />}
        </button>
      </form>
      <p className="t-sm">
        Ainda não tem um quarto? <Link href="/cadastro">Crie o seu</Link>
      </p>
    </>
  );
}
