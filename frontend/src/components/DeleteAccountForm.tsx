"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AlertBox } from "@/components/AlertBox";
import { FieldError } from "@/components/FieldError";
import { accountMessageFor, accountNetworkError, type AccountError } from "@/lib/account-errors";
import { ApiError, deleteAccount } from "@/lib/api-client";

/** S14, danger zone: typing the password is the confirmation step (docs/design/telas/conta.html). */
export function DeleteAccountForm({ address }: { address: string }) {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<AccountError | null>(null);
  const [sending, setSending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending) return;
    setError(null);
    setSending(true);
    try {
      await deleteAccount({ password });
      // The API ended the session and cleared the cookie; the refresh drops cached signed-in pages.
      router.replace("/");
      router.refresh();
    } catch (e) {
      setError(
        e instanceof ApiError
          ? accountMessageFor(e.code, e.status, e.retryAfterSeconds)
          : accountNetworkError(),
      );
      setSending(false);
    }
  }

  const wrong = error?.field === "current";

  return (
    <form className="region region--danger next" aria-labelledby="del-h" onSubmit={onSubmit}>
      <div className="pair-2">
        <h2 id="del-h" className="t-h2">
          Excluir conta
        </h2>
        <p className="t-body">
          Seu perfil sai do ar e {address} fica livre para outra pessoa. Isso não pode ser desfeito.
        </p>
      </div>
      <ul
        className="t-sm"
        style={{
          paddingLeft: "var(--space-5)",
          display: "flex",
          flexDirection: "column",
          gap: "var(--space-1)",
        }}
      >
        <li>Apagamos perfil, foto, destaques e contas vinculadas.</li>
        <li>Temas comprados são perdidos, sem reembolso.</li>
        <li>Registros de pagamento ficam guardados sem seus dados, como a lei pede.</li>
      </ul>
      {error && !wrong && <AlertBox>{error.message}</AlertBox>}
      <div className="field">
        <label className="label" htmlFor="del-pass">
          Digite sua senha para confirmar
        </label>
        <input
          id="del-pass"
          className={`input${wrong ? " input--error" : ""}`}
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          maxLength={128}
          required
          aria-invalid={wrong ? true : undefined}
          aria-describedby="del-pass-erro"
        />
        <FieldError id="del-pass-erro">{wrong ? error?.message : undefined}</FieldError>
      </div>
      <button
        type="submit"
        className={`mq-btn mq-btn--danger self-start${sending ? " is-loading" : ""}`}
        disabled={sending}
        aria-busy={sending}
      >
        Excluir minha conta
        {sending && <span className="mq-spinner" aria-hidden="true" />}
      </button>
    </form>
  );
}
