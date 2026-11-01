"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AlertBox } from "@/components/AlertBox";
import { CategoryIcon } from "@/components/profile/CategoryIcon";
import { ApiError, changeNow, getCollection, getNow } from "@/lib/api-client";
import {
  collectionQuery,
  filterByTitle,
  NETWORK_ERROR,
  type CollectionItem,
} from "@/lib/collection";
import {
  entryCaption,
  nowMessage,
  pickerLabel,
  withEntry,
  type NowEntry,
  type NowMode,
} from "@/lib/now";
import { CATEGORY_META, safeImageUrl, sourceName, type Category } from "@/lib/profile-view";

type Picker =
  | { category: Category; status: "loading" }
  | { category: Category; status: "error" }
  | { category: Category; status: "ready"; items: CollectionItem[] };

const MODES: { mode: NowMode; label: string }[] = [
  { mode: "AUTOMATIC", label: "Automático" },
  { mode: "PINNED", label: "Escolher" },
  { mode: "HIDDEN", label: "Esconder" },
];

/**
 * S10: per category, what the profile shows in "Agora" and how it is chosen (automatic, pinned by
 * hand, hidden). The server renders the first paint; each change is one PUT from the browser (CSRF)
 * and the answer replaces that category's entry. "Escolher" opens the owner's own collection of the
 * category to pick from: the API refuses anything that is not in it.
 */
export function AgoraScreen({ initial }: { initial: NowEntry[] | null }) {
  const [entries, setEntries] = useState<NowEntry[] | null>(initial);
  const [busy, setBusy] = useState<Category | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [picker, setPicker] = useState<Picker | null>(null);
  const [query, setQuery] = useState("");
  const [announcement, setAnnouncement] = useState("");
  const busyRef = useRef(false);

  function failed(caught: unknown) {
    setError(
      caught instanceof ApiError
        ? nowMessage(caught.code, caught.status, caught.retryAfterSeconds)
        : NETWORK_ERROR,
    );
    if (caught instanceof ApiError && caught.code === "NOT_IN_COLLECTION") {
      void getNow()
        .then(setEntries)
        .catch(() => undefined);
    }
  }

  async function apply(category: Category, mode: NowMode, collectionItemId?: number) {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(category);
    setError(null);
    try {
      const saved = await changeNow(category, mode, collectionItemId);
      setEntries((current) => (current ? withEntry(current, saved) : current));
      setPicker(null);
      setAnnouncement(`${CATEGORY_META[category].heading} atualizado.`);
    } catch (caught) {
      failed(caught);
    } finally {
      busyRef.current = false;
      setBusy(null);
    }
  }

  async function openPicker(category: Category) {
    setQuery("");
    setPicker({ category, status: "loading" });
    try {
      const page = await getCollection(
        collectionQuery({ category, sort: "recentes", page: 0, includeHidden: false }),
      );
      setPicker({ category, status: "ready", items: page.items });
    } catch {
      setPicker({ category, status: "error" });
    }
  }

  function choose(entry: NowEntry, mode: NowMode) {
    if (mode === "PINNED") {
      if (picker?.category === entry.category) setPicker(null);
      else void openPicker(entry.category);
      return;
    }
    setPicker(null);
    if (mode !== entry.mode) void apply(entry.category, mode);
  }

  if (!entries) {
    return (
      <AlertBox>
        Não foi possível carregar o Agora agora. Recarregue a página em instantes.
      </AlertBox>
    );
  }

  return (
    <>
      {error && <AlertBox>{error}</AlertBox>}
      <p className="sr" role="status">
        {announcement}
      </p>
      <div className="list" role="list" aria-label="Categorias do Agora" style={{ gap: 0 }}>
        {entries.map((entry) => {
          const meta = CATEGORY_META[entry.category];
          const caption = entryCaption(entry);
          const src = safeImageUrl(entry.shown?.imageUrl);
          const square = meta.shape === "vinyl";
          const open = picker?.category === entry.category;
          const rowBusy = busy === entry.category;
          return (
            <div className="now-set" role="listitem" key={entry.category}>
              <span className={`mq-tag mq-tag--${meta.tag} self-start`}>
                <CategoryIcon category={entry.category} className="mq-icon icon" />
                {meta.sticker}
              </span>

              {entry.shown ? (
                <div className="row row--nowrap" style={{ gap: "var(--space-3)" }}>
                  {src ? (
                    // eslint-disable-next-line @next/next/no-img-element -- hotlinked provider covers (ADR 0006), https only
                    <img
                      className={square ? "thumb thumb--sq" : "thumb"}
                      src={src}
                      alt=""
                      loading="lazy"
                      referrerPolicy="no-referrer"
                    />
                  ) : (
                    <span className={square ? "thumb thumb--sq" : "thumb"} aria-hidden="true">
                      <CategoryIcon category={entry.category} className="mq-icon icon" />
                    </span>
                  )}
                  <div className="pair grow">
                    <p className="t-poster">{entry.shown.title}</p>
                    <Caption text={caption.text} connect={caption.connect} />
                  </div>
                </div>
              ) : (
                <div className="pair-2">
                  <p className="t-sm empty">
                    {entry.mode === "HIDDEN" ? "Escondido no perfil." : "Nada por aqui agora."}
                  </p>
                  {entry.mode !== "HIDDEN" && (
                    <Caption text={caption.text} connect={caption.connect} />
                  )}
                </div>
              )}

              <div className="stack">
                <fieldset className="seg" disabled={rowBusy}>
                  <legend className="sr">{meta.sticker}</legend>
                  {MODES.map(({ mode, label }) => (
                    <label key={mode}>
                      <input
                        type="radio"
                        name={`agora-${entry.category}`}
                        checked={mode === "PINNED" && open ? true : !open && entry.mode === mode}
                        disabled={mode === "AUTOMATIC" && !entry.automaticAvailable}
                        // "Escolher" opens the picker on click (also when it is already the checked
                        // mode); the other two change the mode right away.
                        onChange={() => {
                          if (mode !== "PINNED") choose(entry, mode);
                        }}
                        onClick={() => {
                          if (mode === "PINNED") choose(entry, mode);
                        }}
                      />
                      {label}
                    </label>
                  ))}
                </fieldset>
                {entry.mode === "PINNED" && !open && (
                  <button
                    type="button"
                    className="mq-btn mq-btn--ghost mq-btn--sm self-start"
                    onClick={() => void openPicker(entry.category)}
                  >
                    {pickerLabel(entry)}
                  </button>
                )}
              </div>

              {open && picker && (
                <PickerPanel
                  picker={picker}
                  query={query}
                  setQuery={setQuery}
                  busy={rowBusy}
                  currentId={
                    entry.mode === "PINNED" ? (entry.shown?.collectionItemId ?? null) : null
                  }
                  onPick={(item) => void apply(entry.category, "PINNED", item.id)}
                  onCancel={() => setPicker(null)}
                  heading={meta.heading}
                />
              )}
            </div>
          );
        })}
      </div>
    </>
  );
}

function Caption({ text, connect }: { text: string; connect?: string }) {
  return (
    <p className="t-cap">
      {text}
      {connect ? (
        <>
          {" "}
          <Link href="/painel/contas">
            Conecte {connect === "STEAM" ? "a" : "o"} {sourceName(connect)}
          </Link>{" "}
          para o automático.
        </>
      ) : null}
    </p>
  );
}

function PickerPanel({
  picker,
  query,
  setQuery,
  busy,
  currentId,
  onPick,
  onCancel,
  heading,
}: {
  picker: Picker;
  query: string;
  setQuery: (value: string) => void;
  busy: boolean;
  currentId: number | null;
  onPick: (item: CollectionItem) => void;
  onCancel: () => void;
  heading: string;
}) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    panel.current?.querySelector<HTMLElement>("input, button")?.focus();
  }, []);
  const inputId = `agora-q-${picker.category}`;
  const items = picker.status === "ready" ? filterByTitle(picker.items, query) : [];
  return (
    <div className="stack" ref={panel} style={{ gridColumn: "1 / -1" }}>
      <div className="field">
        <label htmlFor={inputId} className="t-label">
          Escolher na coleção de {heading}
        </label>
        <input
          id={inputId}
          className="input"
          type="search"
          autoComplete="off"
          placeholder="Buscar na coleção"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>
      {picker.status === "loading" && (
        <p className="t-sm" role="status">
          Carregando…
        </p>
      )}
      {picker.status === "error" && <AlertBox>Não foi possível carregar a coleção agora.</AlertBox>}
      {picker.status === "ready" && items.length === 0 && (
        <p className="t-sm" role="status">
          {query
            ? `Nada com “${query}” nas obras carregadas.`
            : "Sua coleção dessa categoria está vazia."}
        </p>
      )}
      {items.length > 0 && (
        <ul className="list" aria-label={`Coleção de ${heading}`}>
          {items.map((item) => (
            <li key={item.id} className="row row--nowrap" style={{ gap: "var(--space-3)" }}>
              <p className="t-poster grow">{item.media.title}</p>
              <button
                type="button"
                className="mq-btn mq-btn--secondary mq-btn--sm"
                disabled={busy || item.id === currentId}
                aria-label={`Usar ${item.media.title}`}
                onClick={() => onPick(item)}
              >
                {item.id === currentId ? "Em uso" : "Usar"}
              </button>
            </li>
          ))}
        </ul>
      )}
      <button
        type="button"
        className="mq-btn mq-btn--ghost mq-btn--sm self-start"
        onClick={onCancel}
      >
        Cancelar
      </button>
    </div>
  );
}
