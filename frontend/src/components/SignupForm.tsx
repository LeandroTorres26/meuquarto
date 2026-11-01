"use client";

import { Check, X } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AlertBox } from "@/components/AlertBox";
import { FieldError } from "@/components/FieldError";
import { ApiError, checkSlug, signUp } from "@/lib/api-client";
import { messageFor, networkError, slugReasonMessage, type FieldName } from "@/lib/auth-errors";
import { debounce, normalizeSlug } from "@/lib/slug";

const SLUG_CHECK_DELAY_MS = 300;

type SlugResult = { slug: string; state: "available" | "unavailable" | "error"; message?: string };
type Errors = Partial<Record<FieldName, string>>;

/** S2: sign up with live slug availability (docs/design/telas/cadastro.html). */
export function SignupForm() {
  const router = useRouter();
  const [slug, setSlug] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [accepted, setAccepted] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<SlugResult | null>(null);
  const slugRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);

  const normalized = normalizeSlug(slug);

  // Debounced availability check. State is only set from the async callback, and answers for an
  // older slug are ignored, so the status never shows a stale result.
  useEffect(() => {
    if (!normalized) return;
    let cancelled = false;
    const run = debounce(async (value: string) => {
      let next: SlugResult;
      try {
        const answer = await checkSlug(value);
        next = answer.available
          ? { slug: value, state: "available" }
          : { slug: value, state: "unavailable", message: slugReasonMessage(answer.reason) };
      } catch (e) {
        const message =
          e instanceof ApiError && e.status === 429
            ? "Muitas verificações seguidas. Espere um instante."
            : "Não foi possível verificar agora. Você ainda pode tentar criar a conta.";
        next = { slug: value, state: "error", message };
      }
      if (!cancelled) setResult(next);
    }, SLUG_CHECK_DELAY_MS);
    run(normalized);
    return () => {
      cancelled = true;
      run.cancel();
    };
  }, [normalized]);

  const slugStatus = !normalized
    ? null
    : result && result.slug === normalized
      ? result
      : ({ slug: normalized, state: "checking" } as const);
  const slugError = errors.slug;
  const slugBad = Boolean(slugError) || slugStatus?.state === "unavailable";

  function clear(field: FieldName) {
    setErrors((current) => ({ ...current, [field]: undefined, form: undefined }));
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending) return;
    setSending(true);
    setErrors({});
    try {
      await signUp({ email: email.trim(), password, slug: normalized });
      router.replace("/painel/conta");
    } catch (e) {
      const error =
        e instanceof ApiError ? messageFor(e.code, e.status, e.retryAfterSeconds) : networkError();
      setErrors({ [error.field]: error.message });
      const target = { slug: slugRef, email: emailRef, password: passwordRef, form: null }[
        error.field
      ];
      target?.current?.focus();
      setSending(false);
    }
  }

  return (
    <>
      <div className="pair-2">
        <h1 className="t-h1">Crie seu quarto</h1>
        <p className="t-body muted">
          Leva um minuto. Depois você conecta as contas e fixa os favoritos.
        </p>
      </div>

      <form className="form" onSubmit={onSubmit}>
        {errors.form && <AlertBox>{errors.form}</AlertBox>}

        <div className="field">
          <label className="label" htmlFor="slug">
            Seu endereço
          </label>
          <div
            className={`prefixed${slugBad ? " prefixed--error" : slugStatus?.state === "available" ? " prefixed--ok" : ""}`}
          >
            <span aria-hidden="true">meuquarto.com.br/</span>
            <input
              id="slug"
              ref={slugRef}
              className="input"
              name="slug"
              value={slug}
              onChange={(e) => {
                setSlug(e.target.value);
                clear("slug");
              }}
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              maxLength={64}
              required
              aria-invalid={slugBad || undefined}
              aria-describedby="slug-status slug-help"
            />
          </div>
          <p id="slug-status" aria-live="polite">
            {slugError ? (
              <span className="status status--bad">
                <X className="icon" aria-hidden="true" />
                {slugError}
              </span>
            ) : slugStatus?.state === "available" ? (
              <span className="status status--ok">
                <Check className="icon" aria-hidden="true" />
                Disponível
              </span>
            ) : slugStatus?.state === "unavailable" ? (
              <span className="status status--bad">
                <X className="icon" aria-hidden="true" />
                {slugStatus.message}
              </span>
            ) : slugStatus?.state === "error" ? (
              <span className="status help">{slugStatus.message}</span>
            ) : slugStatus?.state === "checking" ? (
              <span className="status help">Verificando…</span>
            ) : null}
          </p>
          <p id="slug-help" className="help">
            Letras minúsculas, números e hífen, de 3 a 30 caracteres.
          </p>
        </div>

        <div className="field">
          <label className="label" htmlFor="email">
            E-mail
          </label>
          <input
            id="email"
            ref={emailRef}
            className={`input${errors.email ? " input--error" : ""}`}
            name="email"
            type="email"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              clear("email");
            }}
            autoComplete="email"
            placeholder="voce@email.com"
            maxLength={254}
            required
            aria-invalid={errors.email ? true : undefined}
            aria-describedby="email-error"
          />
          <FieldError id="email-error">{errors.email}</FieldError>
        </div>

        <div className="field">
          <label className="label" htmlFor="senha">
            Senha
          </label>
          <input
            id="senha"
            ref={passwordRef}
            className={`input${errors.password ? " input--error" : ""}`}
            name="password"
            type="password"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              clear("password");
            }}
            autoComplete="new-password"
            maxLength={128}
            required
            aria-invalid={errors.password ? true : undefined}
            aria-describedby="senha-error senha-help"
          />
          <FieldError id="senha-error">{errors.password}</FieldError>
          <p id="senha-help" className="help">
            Pelo menos 8 caracteres.
          </p>
        </div>

        <label className="check">
          <input
            type="checkbox"
            checked={accepted}
            onChange={(e) => setAccepted(e.target.checked)}
            required
          />
          <span>
            Li e aceito os{" "}
            <Link href="/termos" prefetch={false}>
              Termos de uso
            </Link>{" "}
            e a{" "}
            <Link href="/privacidade" prefetch={false}>
              Política de privacidade
            </Link>
            .
          </span>
        </label>

        <button
          type="submit"
          className={`mq-btn mq-btn--primary btn-block${sending ? " is-loading" : ""}`}
          disabled={sending}
          aria-busy={sending}
        >
          Criar conta
          {sending && <span className="mq-spinner" aria-hidden="true" />}
        </button>
      </form>

      <p className="t-sm">
        Já tem conta? <Link href="/entrar">Entrar</Link>
      </p>
    </>
  );
}
