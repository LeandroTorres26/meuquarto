"use client";

import { Lock, Upload } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLayoutEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { AlertBox } from "@/components/AlertBox";
import { FieldError } from "@/components/FieldError";
import { ApiError, removeAvatar, saveProfile, uploadAvatar } from "@/lib/api-client";
import { ThemePreview } from "@/components/profile/ThemePreview";
import { normalizeSlug } from "@/lib/slug";
import { applyTheme, showTheme } from "@/lib/theme";
import {
  AVATAR_TYPES,
  BIO_MAX,
  NAME_MAX,
  NETWORK_DOWN,
  THEME_LABELS,
  themeCaption,
  avatarFileProblem,
  charCount,
  profileMessage,
  type EditorError,
  type EditorField,
  type OwnProfile,
  type ThemeOption,
} from "@/lib/profile-editor";

type Toast = { kind: "ok" | "bad"; text: string };

const toError = (e: unknown): EditorError =>
  e instanceof ApiError ? profileMessage(e.code, e.status, e.retryAfterSeconds) : NETWORK_DOWN;

/** S6 (docs/design/telas/painel-perfil.html): photo, name, bio, address and theme. */
export function ProfileEditorScreen({ initial }: { initial: OwnProfile }) {
  const router = useRouter();
  const [saved, setSaved] = useState(initial);
  const [name, setName] = useState(initial.displayName);
  const [bio, setBio] = useState(initial.bio);
  const [slug, setSlug] = useState(initial.slug);
  // `saved.theme` colours the whole site and is saved the moment it is picked; `viewed` is the one
  // shown now, which may also be a locked premium theme the user is only looking at.
  const [viewed, setViewed] = useState(initial.theme);
  const [themeNote, setThemeNote] = useState<string | null>(null);
  // The saved theme for callbacks that outlive a render (a slow save, leaving the page).
  const savedTheme = useRef(initial.theme);
  // Only the answer to the latest pick may change the theme (picks can be quicker than the API).
  const themeRequest = useRef(0);
  // True after a save failed in a way that may pass by itself (server restarting, connection lost).
  const [canRetrySave, setCanRetrySave] = useState(false);
  // What the polaroid shows: the chosen file as a data URL while it uploads, else the saved avatar.
  const [preview, setPreview] = useState<string | null>(null);
  const [avatarUrl, setAvatarUrl] = useState(initial.avatarUrl);
  const [busyAvatar, setBusyAvatar] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<EditorField, string>>>({});
  const [toast, setToast] = useState<Toast | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const shown = preview ?? avatarUrl;
  const viewedOption = saved.themes.find((option) => option.id === viewed);

  // A locked theme is only looked at while this screen is open. Next.js keeps a left page hidden
  // in the document (Activity), so leaving it puts the saved theme back on the site at once.
  useLayoutEffect(
    () => () => {
      showTheme(savedTheme.current);
      setViewed(savedTheme.current);
    },
    [],
  );

  function fail(error: EditorError, fromSave = false) {
    setErrors({ [error.field]: error.message });
    setCanRetrySave(fromSave && Boolean(error.retry));
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

  function pickTheme(option: ThemeOption) {
    setViewed(option.id);
    setErrors((current) => ({ ...current, theme: undefined }));
    if (option.locked) {
      showTheme(option.id); // looked at, never saved
      setThemeNote(null);
      return;
    }
    applyTheme(option.id);
    void saveTheme(option.id);
  }

  // The theme is saved on its own: the other fields keep their saved values, so unsaved edits to the
  // name or the bio are neither sent nor lost.
  async function saveTheme(id: string) {
    const request = ++themeRequest.current;
    setThemeNote(null);
    try {
      const result = await saveProfile({
        displayName: saved.displayName,
        bio: saved.bio,
        slug: saved.slug,
        theme: id,
      });
      if (request !== themeRequest.current) return;
      savedTheme.current = result.theme;
      setSaved((current) => ({
        ...current,
        theme: result.theme,
        themes: result.themes ?? current.themes,
      }));
      setThemeNote(`Tema ${THEME_LABELS[result.theme] ?? result.theme} salvo.`);
      router.refresh();
    } catch (e) {
      if (request !== themeRequest.current) return;
      applyTheme(savedTheme.current);
      setViewed(savedTheme.current);
      const error = toError(e);
      setErrors({ theme: error.message });
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submit();
  }

  async function submit() {
    if (saving) return;
    setSaving(true);
    setErrors({});
    setCanRetrySave(false);
    setToast(null);
    try {
      const result = await saveProfile({
        displayName: name,
        bio,
        slug: normalizeSlug(slug),
        theme: savedTheme.current,
      });
      setSaved(result);
      setName(result.displayName);
      setBio(result.bio);
      setSlug(result.slug);
      savedTheme.current = result.theme;
      applyTheme(result.theme);
      setViewed(result.theme);
      setToast({ kind: "ok", text: "Perfil salvo." });
      router.refresh();
    } catch (e) {
      fail(toError(e), true);
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
          {canRetrySave && !saving ? (
            <button
              type="button"
              className="mq-btn mq-btn--secondary"
              onClick={() => void submit()}
            >
              Tentar de novo
            </button>
          ) : null}
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
            Muda as cores do site todo e já fica salvo. Os marcados com “na loja” são pagos.
          </p>
        </div>
        <div
          className="grid"
          role="radiogroup"
          aria-labelledby="tema-h"
          style={{ gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: "var(--space-3)" }}
        >
          {saved.themes.map((option) => (
            <label className="theme-opt" key={option.id}>
              <input
                type="radio"
                name="tema"
                value={option.id}
                checked={viewed === option.id}
                onChange={() => pickTheme(option)}
              />
              <div className="swatch" data-theme={option.id} aria-hidden="true">
                <span className="swatch__poster" />
                <span className="swatch__aa">AA</span>
                <span className="swatch__bar" />
                <span className="swatch__lamp" />
              </div>
              <span className="row row--between">
                <span className="t-label">{THEME_LABELS[option.id] ?? option.id}</span>
                <span className="t-cap">{themeCaption(option)}</span>
              </span>
            </label>
          ))}
        </div>
        <FieldError id="tema-erro">{errors.theme}</FieldError>
        <ThemePreview
          theme={viewed}
          name={name.trim() || saved.slug}
          slug={normalizeSlug(slug) || saved.slug}
          bio={bio}
          label={`Prévia do tema ${THEME_LABELS[viewed] ?? viewed}`}
        />
        {viewedOption?.locked ? (
          <p className="help" role="status">
            <Lock className="icon" aria-hidden="true" /> O tema {THEME_LABELS[viewed] ?? viewed} é
            pago. Você pode ver como fica, mas só aplica depois de comprar.{" "}
            <Link href="/painel/loja" prefetch={false}>
              Ver na loja
            </Link>
          </p>
        ) : (
          <p className="help" role="status">
            {themeNote ? `${themeNote} ` : null}
            <Link href={`/${saved.slug}`}>Ver meu perfil</Link>
          </p>
        )}
      </section>
    </div>
  );
}
