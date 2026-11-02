"use client";

import { Upload } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { AlertBox } from "@/components/AlertBox";
import { FieldError } from "@/components/FieldError";
import { ApiError, removeAvatar, saveProfile, uploadAvatar } from "@/lib/api-client";
import { normalizeSlug } from "@/lib/slug";
import {
  AVATAR_TYPES,
  BIO_MAX,
  NAME_MAX,
  THEME_LABELS,
  avatarFileProblem,
  charCount,
  profileMessage,
  type EditorError,
  type EditorField,
  type OwnProfile,
} from "@/lib/profile-editor";

type Toast = { kind: "ok" | "bad"; text: string };

const NETWORK: EditorError = {
  field: "form",
  message: "Não foi possível falar com o servidor. Confira sua conexão e tente de novo.",
};

const toError = (e: unknown): EditorError =>
  e instanceof ApiError ? profileMessage(e.code, e.status, e.retryAfterSeconds) : NETWORK;

/** S6 (docs/design/telas/painel-perfil.html): photo, name, bio, address and theme. */
export function ProfileEditorScreen({ initial }: { initial: OwnProfile }) {
  const router = useRouter();
  const [saved, setSaved] = useState(initial);
  const [name, setName] = useState(initial.displayName);
  const [bio, setBio] = useState(initial.bio);
  const [slug, setSlug] = useState(initial.slug);
  const [theme, setTheme] = useState(initial.theme);
  // What the polaroid shows: the chosen file as a data URL while it uploads, else the saved avatar.
  const [preview, setPreview] = useState<string | null>(null);
  const [avatarUrl, setAvatarUrl] = useState(initial.avatarUrl);
  const [busyAvatar, setBusyAvatar] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<EditorField, string>>>({});
  const [toast, setToast] = useState<Toast | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const shown = preview ?? avatarUrl;

  function fail(error: EditorError) {
    setErrors({ [error.field]: error.message });
    if (error.field === "form") setToast({ kind: "bad", text: error.message });
  }

  function onPick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = ""; // choosing the same file again must fire onChange
    if (!file) return;
    setToast(null);
    const problem = avatarFileProblem(file);
    if (problem) {
      setErrors({ avatar: problem });
      return;
    }
    setErrors({});
    // A data URL (not a blob URL) so the preview needs nothing new in the page's CSP.
    const reader = new FileReader();
    reader.onload = () => setPreview(typeof reader.result === "string" ? reader.result : null);
    reader.readAsDataURL(file);
    void send(file);
  }

  async function send(file: File) {
    setBusyAvatar(true);
    try {
      const { avatarUrl: next } = await uploadAvatar(file);
      setAvatarUrl(next);
      setPreview(null);
      setToast({ kind: "ok", text: "Foto atualizada." });
      router.refresh();
    } catch (e) {
      setPreview(null);
      fail(toError(e));
    } finally {
      setBusyAvatar(false);
    }
  }

  async function onRemove() {
    setBusyAvatar(true);
    setErrors({});
    try {
      await removeAvatar();
      setAvatarUrl(null);
      setPreview(null);
      setToast({ kind: "ok", text: "Foto removida." });
      router.refresh();
    } catch (e) {
      fail(toError(e));
    } finally {
      setBusyAvatar(false);
    }
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    setErrors({});
    setToast(null);
    try {
      const result = await saveProfile({
        displayName: name,
        bio,
        slug: normalizeSlug(slug),
        theme,
      });
      setSaved(result);
      setName(result.displayName);
      setBio(result.bio);
      setSlug(result.slug);
      setTheme(result.theme);
      setToast({ kind: "ok", text: "Perfil salvo." });
      router.refresh();
    } catch (e) {
      fail(toError(e));
    } finally {
      setSaving(false);
    }
  }

  const bad = (field: EditorField) => Boolean(errors[field]);
  const bioCount = charCount(bio);

  return (
    <div className="cols-aside">
      <form className="form-groups" aria-label="Dados do perfil" onSubmit={onSubmit}>
        {errors.form && <AlertBox>{errors.form}</AlertBox>}

        <fieldset className="fieldset">
          <legend className="t-h3">Foto e nome</legend>
          <div className="row row--nowrap" style={{ gap: "var(--space-6)" }}>
            <div
              className="mq-polaroid"
              style={{ width: 104, flex: "none", padding: "6px 6px 4px" }}
            >
              <span className="mq-tape" aria-hidden="true" />
              {shown ? (
                // eslint-disable-next-line @next/next/no-img-element -- same-origin avatar or a local data URL preview
                <img className="mq-art" src={shown} alt="Sua foto atual" width={512} height={512} />
              ) : (
                <div className="mq-art mq-art--initial" role="img" aria-label="Sem foto ainda">
                  {Array.from(saved.displayName)[0]?.toUpperCase() ?? "?"}
                </div>
              )}
            </div>
            <div className="pair-2">
              <div className="row" style={{ gap: "var(--space-2)" }}>
                <button
                  type="button"
                  className="mq-btn mq-btn--secondary mq-btn--sm self-start"
                  onClick={() => fileRef.current?.click()}
                  disabled={busyAvatar}
                  aria-busy={busyAvatar}
                >
                  <Upload className="icon" aria-hidden="true" />
                  {avatarUrl ? "Trocar foto" : "Enviar foto"}
                </button>
                {avatarUrl && (
                  <button
                    type="button"
                    className="mq-btn mq-btn--ghost mq-btn--sm"
                    onClick={onRemove}
                    disabled={busyAvatar}
                  >
                    Remover foto
                  </button>
                )}
              </div>
              <input
                ref={fileRef}
                id="foto"
                type="file"
                accept={AVATAR_TYPES.join(",")}
                className="sr"
                tabIndex={-1}
                aria-label="Escolher arquivo de foto"
                onChange={onPick}
              />
              <p className="help">JPG ou PNG, até 2 MB. Vira um quadrado de 512 px.</p>
              <FieldError id="foto-erro">{errors.avatar}</FieldError>
            </div>
          </div>
          <div className="field">
            <label className="label" htmlFor="nome">
              Nome
            </label>
            <input
              id="nome"
              className={`input${bad("name") ? " input--error" : ""}`}
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={NAME_MAX * 2}
              autoComplete="name"
              required
              aria-invalid={bad("name") || undefined}
              aria-describedby="nome-erro"
            />
            <FieldError id="nome-erro">{errors.name}</FieldError>
          </div>
        </fieldset>

        <fieldset className="fieldset">
          <legend className="t-h3">Sobre você</legend>
          <div className="field">
            <div className="row row--between">
              <label className="label" htmlFor="bio">
                Bio
              </label>
              <span className="t-cap" aria-live="polite">
                {bioCount} de {BIO_MAX}
              </span>
            </div>
            <textarea
              id="bio"
              className={`input${bad("bio") ? " input--error" : ""}`}
              rows={3}
              style={{ resize: "vertical" }}
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              aria-invalid={bad("bio") || undefined}
              aria-describedby="bio-erro"
            />
            <FieldError id="bio-erro">{errors.bio}</FieldError>
          </div>
        </fieldset>

        <fieldset className="fieldset">
          <legend className="t-h3">Endereço</legend>
          <div className="field">
            <label className="label" htmlFor="slug">
              Link do perfil
            </label>
            <div className={`prefixed${bad("slug") ? " prefixed--error" : ""}`}>
              <span aria-hidden="true">meuquarto.com.br/</span>
              <input
                id="slug"
                className="input"
                value={slug}
                onChange={(e) => setSlug(e.target.value)}
                autoCapitalize="none"
                autoCorrect="off"
                spellCheck={false}
                maxLength={64}
                required
                aria-invalid={bad("slug") || undefined}
                aria-describedby="slug-erro slug-h"
              />
            </div>
            <FieldError id="slug-erro">{errors.slug}</FieldError>
            <p id="slug-h" className="help">
              Se mudar, o link antigo para de funcionar.
            </p>
          </div>
        </fieldset>

        <div className="actions">
          <button
            type="submit"
            className={`mq-btn mq-btn--primary${saving ? " is-loading" : ""}`}
            disabled={saving}
            aria-busy={saving}
          >
            Salvar perfil
            {saving && <span className="mq-spinner" aria-hidden="true" />}
          </button>
          <div aria-live="polite" role="status">
            {toast && (
              <div className="mq-toast">
                <span>
                  <strong>{toast.kind === "ok" ? "Pronto." : "Ops."}</strong> {toast.text}
                </span>
              </div>
            )}
          </div>
        </div>
      </form>

      <section className="stack" aria-labelledby="tema-h">
        <div className="pair-2">
          <h2 id="tema-h" className="t-h3">
            Tema
          </h2>
          <p className="help">
            Muda as cores do seu perfil. Mais temas chegam com a loja.
            {/* The store link returns with S11. */}
          </p>
        </div>
        <div
          className="grid"
          role="radiogroup"
          aria-labelledby="tema-h"
          style={{ gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: "var(--space-3)" }}
        >
          {saved.availableThemes.map((id) => (
            <label className="theme-opt" key={id}>
              <input
                type="radio"
                name="tema"
                value={id}
                checked={theme === id}
                onChange={() => setTheme(id)}
              />
              <div className="swatch" data-theme={id} aria-hidden="true">
                <span className="swatch__poster" />
                <span className="swatch__aa">AA</span>
                <span className="swatch__bar" />
                <span className="swatch__lamp" />
              </div>
              <span className="row row--between">
                <span className="t-label">{THEME_LABELS[id] ?? id}</span>
                <span className="t-cap">grátis</span>
              </span>
            </label>
          ))}
        </div>
        <FieldError id="tema-erro">{errors.theme}</FieldError>
        <p className="help">
          O tema vale depois de <strong>Salvar perfil</strong>.{" "}
          <Link href={`/${saved.slug}`}>Ver meu perfil</Link>
        </p>
      </section>
    </div>
  );
}
