"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AlertBox } from "@/components/AlertBox";
import { FieldError } from "@/components/FieldError";
import { ProviderTags } from "@/components/linked/ProviderTags";
import { ApiError, linkAccount } from "@/lib/api-client";
import {
  NETWORK_MESSAGE,
  broughtCaption,
  linkErrorMessage,
  type LinkErrorField,
  type ProviderInfo,
} from "@/lib/linked-accounts";

/** S9, a platform not linked yet: username input, hint, "Vincular" and the inline errors. */
export function LinkCard({ provider }: { provider: ProviderInfo }) {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [error, setError] = useState<{ field: LinkErrorField; message: string } | null>(null);
  const [sending, setSending] = useState(false);

  const id = provider.id.toLowerCase();

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = username.trim();
    if (sending || !value) return;
    setError(null);
    setSending(true);
    try {
      await linkAccount({ provider: provider.id, username: value });
      setUsername("");
      router.refresh(); // the card moves to "Conectadas" with the server's data
    } catch (e) {
      setError(
        e instanceof ApiError
          ? linkErrorMessage(e.code, e.status, provider.name, e.retryAfterSeconds)
          : { field: "form", message: NETWORK_MESSAGE },
      );
    } finally {
      setSending(false);
    }
  }

  const invalid = error?.field === "username";

  return (
    <form className="region region--line" aria-labelledby={`${id}-h`} onSubmit={onSubmit}>
      <div className="row row--between">
        <h3 id={`${id}-h`} className="t-h3">
          {provider.name}
        </h3>
        <ProviderTags provider={provider} />
      </div>
      {error?.field === "form" && <AlertBox>{error.message}</AlertBox>}
      <div className="field">
        <label className="label" htmlFor={`${id}-user`}>
          {provider.label}
        </label>
        <input
          id={`${id}-user`}
          className={`input${invalid ? " input--error" : ""}`}
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder={provider.placeholder}
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          maxLength={100}
          required
          aria-invalid={invalid ? true : undefined}
          aria-describedby={`${id}-help ${id}-erro`}
        />
        <p id={`${id}-help`} className="help">
          {provider.hint}
        </p>
        <FieldError id={`${id}-erro`}>{invalid ? error?.message : undefined}</FieldError>
      </div>
      <p className="t-cap">{broughtCaption(provider)}</p>
      <button
        type="submit"
        className={`mq-btn mq-btn--primary mq-btn--sm self-start${sending ? " is-loading" : ""}`}
        disabled={sending}
        aria-busy={sending}
      >
        Vincular {provider.name}
        {sending && <span className="mq-spinner" aria-hidden="true" />}
      </button>
    </form>
  );
}
