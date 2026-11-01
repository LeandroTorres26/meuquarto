"use client";

import { Check, Plus, RefreshCw, Search, X } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { AlertBox } from "@/components/AlertBox";
import { addToCollection, ApiError, searchCatalog } from "@/lib/api-client";
import {
  CATEGORIES,
  collectionMessage,
  debounce,
  NETWORK_ERROR,
  resultCaption,
  SEARCH_DEBOUNCE_MS,
  SEARCH_MIN_CHARS,
  searchTerm,
  unavailableMessage,
  workKey,
  type CatalogResult,
  type CatalogSearch,
  type CollectionItem,
} from "@/lib/collection";
import { CATEGORY_META, safeImageUrl, type Category } from "@/lib/profile-view";

const CATALOG_NAME: Record<Category, string> = {
  MOVIE: "TMDB",
  SERIES: "TMDB",
  ANIME: "AniList",
  MANGA: "AniList",
  BOOK: "Open Library",
  GAME: "IGDB",
  MUSIC: "Deezer",
};

type Outcome = { key: string; kind: "ok"; data: CatalogSearch } | { key: string; kind: "error" };
type Row = { state: "sending" | "added" | "error"; message?: string };

/**
 * S8: search the catalog of a category and add a work to the collection (no limit). Native
 * <dialog>: Esc, focus trap and an inert page come from showModal(), and focus goes back to the
 * button that opened it. A bottom sheet below 600px, a centered dialog from there (`.dialog`).
 */
export function AddWorkDialog({
  open,
  initialCategory,
  onClose,
  onAdded,
}: {
  open: boolean;
  initialCategory: Category;
  onClose: () => void;
  /** Called after a work is added, so the screen can reload its list. */
  onAdded: (item: CollectionItem) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const opener = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const element = dialog.current;
    if (!element) return;
    if (open && !element.open) {
      opener.current = document.activeElement as HTMLElement | null;
      element.showModal();
      element.querySelector<HTMLElement>("[data-autofocus]")?.focus(); // the search field, not the close button
    } else if (!open && element.open) {
      element.close();
    }
  }, [open]);

  function handleClose() {
    onClose();
    if (opener.current?.isConnected) opener.current.focus();
    opener.current = null;
  }

  return (
    <dialog
      ref={dialog}
      className="dialog"
      aria-labelledby="add-h"
      onClose={handleClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose(); // the dimmed backdrop
      }}
    >
      {/* Mounted only while open: closing forgets the search, so the next opening starts clean. */}
      {open && (
        <AddWorkPanel initialCategory={initialCategory} onClose={onClose} onAdded={onAdded} />
      )}
    </dialog>
  );
}

function AddWorkPanel({
  initialCategory,
  onClose,
  onAdded,
}: {
  initialCategory: Category;
  onClose: () => void;
  onAdded: (item: CollectionItem) => void;
}) {
  const id = useId();
  const [category, setCategory] = useState<Category>(initialCategory);
  const [query, setQuery] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [rows, setRows] = useState<Record<string, Row>>({});
  const [announcement, setAnnouncement] = useState("");

  const term = searchTerm(query);
  const key = term ? `${category}|${term}|${attempt}` : null;

  // Debounced search. The answer is stored with the key it was asked for, and only an answer whose
  // key is still the current one is shown, so a slow older request can never overwrite a newer one.
  useEffect(() => {
    if (!term || !key) return;
    const run = debounce(() => {
      searchCatalog(category, term)
        .then((data) => setOutcome({ key, kind: "ok", data }))
        .catch(() => setOutcome({ key, kind: "error" }));
    }, SEARCH_DEBOUNCE_MS);
    run();
    return () => run.cancel();
  }, [category, term, key]);

  const view: "idle" | "loading" | "ok" | "error" =
    key === null ? "idle" : outcome?.key === key ? outcome.kind : "loading";
  const data = outcome?.kind === "ok" && outcome.key === key ? outcome.data : null;

  async function add(result: CatalogResult) {
    const rowKey = workKey(result.category, result.provider, result.externalId);
    setRows((current) => ({ ...current, [rowKey]: { state: "sending" } }));
    try {
      const { created, item } = await addToCollection({
        category: result.category,
        externalId: result.externalId,
      });
      setRows((current) => ({ ...current, [rowKey]: { state: "added" } }));
      setAnnouncement(
        created
          ? `${result.title} foi adicionado à coleção.`
          : `${result.title} já estava na coleção.`,
      );
      onAdded(item);
    } catch (error) {
      const message =
        error instanceof ApiError
          ? collectionMessage(error.code, error.status, error.retryAfterSeconds)
          : NETWORK_ERROR;
      setRows((current) => ({ ...current, [rowKey]: { state: "error", message } }));
    }
  }

  return (
    <>
      <div className="row row--between row--nowrap">
        <h2 id="add-h" className="t-h2">
          Adicionar obra
        </h2>
        <button type="button" className="ib" aria-label="Fechar" onClick={onClose}>
          <X className="icon" aria-hidden="true" />
        </button>
      </div>

      <div className="field">
        <label htmlFor={`${id}-cat`} className="t-label">
          Categoria
        </label>
        <select
          id={`${id}-cat`}
          className="input"
          value={category}
          onChange={(event) => setCategory(event.target.value as Category)}
        >
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {CATEGORY_META[c].sticker}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor={`${id}-q`} className="sr">
          Buscar {CATEGORY_META[category].noun}
        </label>
        <div className="search-box">
          <Search className="icon" aria-hidden="true" />
          <input
            id={`${id}-q`}
            className="input"
            type="search"
            value={query}
            placeholder={`Buscar ${CATEGORY_META[category].noun}`}
            autoComplete="off"
            maxLength={100}
            data-autofocus
            aria-describedby={`${id}-help`}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <p id={`${id}-help`} className="help">
          A coleção não tem limite. Busca no catálogo do {CATALOG_NAME[category]}; digite pelo menos{" "}
          {SEARCH_MIN_CHARS} letras.
        </p>
      </div>

      <div aria-live="polite" aria-busy={view === "loading"} className="stack">
        {view === "loading" && (
          <ul className="list" aria-label="Carregando resultados">
            {[0, 1, 2].map((n) => (
              <li key={n} aria-hidden="true">
                <span className="thumb thumb--lg" />
                <div className="grow pair-2">
                  <span className="mq-sk" style={{ width: "70%" }} />
                  <span className="mq-sk" />
                </div>
              </li>
            ))}
          </ul>
        )}

        {view === "error" && (
          <div className="stack">
            <AlertBox>Não deu para buscar agora. Tente de novo.</AlertBox>
            <button
              type="button"
              className="mq-btn mq-btn--secondary mq-btn--sm self-start"
              onClick={() => setAttempt((n) => n + 1)}
            >
              <RefreshCw className="icon" aria-hidden="true" />
              Tentar de novo
            </button>
          </div>
        )}

        {data && !data.available && (
          <div className="pair-2 add-empty" role="status">
            <p className="t-h3">{unavailableMessage(category)}</p>
            <p className="t-sm">
              Enquanto isso, vincule uma conta em Contas vinculadas para trazer suas obras.
            </p>
          </div>
        )}

        {data && data.available && data.results.length === 0 && (
          <div className="pair-2 add-empty" role="status">
            <p className="t-h3">Nada encontrado para “{term}”.</p>
            <p className="t-sm">Tente outro título, ou o nome original.</p>
          </div>
        )}

        {data && data.available && data.results.length > 0 && (
          <ul className="list" aria-label="Resultados">
            {data.results.map((result) => {
              const rowKey = workKey(result.category, result.provider, result.externalId);
              const row = rows[rowKey];
              const cover = safeImageUrl(result.imageUrl);
              const square = result.category === "MUSIC";
              return (
                <li key={rowKey} className="add-result">
                  {cover ? (
                    // eslint-disable-next-line @next/next/no-img-element -- hotlinked provider covers (ADR 0006), https only
                    <img
                      className={`thumb thumb--lg${square ? " thumb--sq" : ""}`}
                      src={cover}
                      alt=""
                      loading="lazy"
                      decoding="async"
                      referrerPolicy="no-referrer"
                    />
                  ) : (
                    <span
                      className={`thumb thumb--lg${square ? " thumb--sq" : ""}`}
                      aria-hidden="true"
                    />
                  )}
                  <div className="grow pair">
                    <p className="t-poster">{result.title}</p>
                    <p className="t-cap">{resultCaption(result.category, result.year)}</p>
                    {row?.state === "error" && (
                      <p className="t-cap note-bad" role="alert">
                        {row.message}
                      </p>
                    )}
                  </div>
                  {row?.state === "added" ? (
                    <span className="status status--ok">
                      <Check className="icon" aria-hidden="true" />
                      Na coleção
                    </span>
                  ) : (
                    <button
                      type="button"
                      className={`mq-btn mq-btn--secondary mq-btn--sm${
                        row?.state === "sending" ? " is-loading" : ""
                      }`}
                      disabled={row?.state === "sending"}
                      aria-busy={row?.state === "sending"}
                      aria-label={`Adicionar ${result.title} à coleção`}
                      onClick={() => void add(result)}
                    >
                      <Plus className="icon" aria-hidden="true" />
                      Adicionar à coleção
                      {row?.state === "sending" && (
                        <span className="mq-spinner" aria-hidden="true" />
                      )}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <p className="t-cap">Obras marcadas como adultas não podem ser adicionadas.</p>
      <p className="sr" role="status" aria-live="polite">
        {announcement}
      </p>
    </>
  );
}
