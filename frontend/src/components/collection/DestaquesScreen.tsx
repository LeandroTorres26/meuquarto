"use client";

import { Plus, Search, Star } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { AlertBox } from "@/components/AlertBox";
import { AddWorkDialog } from "@/components/collection/AddWorkDialog";
import { CollectionRow } from "@/components/collection/CollectionRow";
import { HighlightStrip } from "@/components/collection/HighlightStrip";
import {
  addHighlight,
  addToCollection,
  ApiError,
  getCollection,
  getHighlights,
  removeCollectionItem,
  removeHighlight,
  reorderHighlights,
  updateCollectionItem,
} from "@/lib/api-client";
import {
  CATEGORIES,
  collectionMessage,
  collectionQuery,
  counterLabel,
  filterByTitle,
  hasMore,
  isFull,
  listCategory,
  listOf,
  NETWORK_ERROR,
  needsRefresh,
  SORT_OPTIONS,
  tabLabel,
  tabParam,
  TABS,
  withList,
  withOrder,
  type CollectionItem,
  type CollectionPage,
  type Highlight,
  type HighlightOverview,
  type SortKey,
  type Tab,
} from "@/lib/collection";
import { CATEGORY_META, countLabel, type Category } from "@/lib/profile-view";

type ListState = {
  items: CollectionItem[];
  total: number;
  /** Index of the last page loaded. */
  page: number;
  status: "ready" | "loading" | "error";
};

type Toast = { id: number; content: ReactNode; undo?: () => void };

const TOAST_MS = 6000;

/**
 * S7: the highlights strip and the whole collection of the open tab. The server renders the first
 * paint (overview of highlights and the first page of the collection); everything after runs from
 * the browser with CSRF. A mutation disables the controls while it runs, then the highlight lists
 * are fetched again, so the screen shows what the server holds. Only reordering is optimistic
 * (the order is validated by the server and rolled back on any error).
 */
export function DestaquesScreen({
  initialTab,
  initialOverview,
  initialPage,
  initialListCategory,
  favoriteCategories,
}: {
  initialTab: Tab;
  initialOverview: HighlightOverview | null;
  initialPage: CollectionPage | null;
  initialListCategory: Category;
  favoriteCategories: Record<number, Category>;
}) {
  const [tab, setTab] = useState<Tab>(initialTab);
  const [overview, setOverview] = useState<HighlightOverview | null>(initialOverview);
  /** Category of each favorite (by collection item id): the highlight answer does not carry it. */
  const [categories, setCategories] = useState<Record<number, Category>>(favoriteCategories);
  const [listCat, setListCat] = useState<Category>(initialListCategory);
  const [sort, setSort] = useState<SortKey>("recentes");
  const [includeHidden, setIncludeHidden] = useState(false);
  const [query, setQuery] = useState("");
  const [list, setList] = useState<ListState>({
    items: initialPage?.items ?? [],
    total: initialPage?.total ?? 0,
    page: initialPage?.page ?? 0,
    status: initialPage ? "ready" : "error",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState("");
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [addOpen, setAddOpen] = useState(false);
  const seq = useRef(0);
  const toastId = useRef(0);
  /** Guards against a second mutation; a ref because Desfazer buttons keep the closure of their toast. */
  const busyRef = useRef(false);
  /** What the list shows now, for callbacks created earlier (Desfazer) that must not reload an old view. */
  const latest = useRef({ category: listCat, sort, includeHidden });
  useEffect(() => {
    latest.current = { category: listCat, sort, includeHidden };
  });

  const limit = overview?.limit ?? 4;
  const highlightCategory = listCategory(tab);
  const highlights = overview ? listOf(overview, highlightCategory).items : [];
  const full = isFull(highlights.length, limit);
  const highlightByItem = new Map(highlights.map((h) => [h.collectionItemId, h.id]));

  // ---------- helpers ----------

  function toast(content: ReactNode, undo?: () => void) {
    const id = ++toastId.current;
    setToasts((current) => [...current, { id, content, undo }]);
    setTimeout(() => setToasts((current) => current.filter((t) => t.id !== id)), TOAST_MS);
  }
  const dismissToast = (id: number) => setToasts((current) => current.filter((t) => t.id !== id));

  function focusLater(selector: string) {
    requestAnimationFrame(() => {
      const target = document.querySelector<HTMLElement>(selector);
      (target && !target.hasAttribute("disabled")
        ? target
        : document.getElementById("hl-h")
      )?.focus();
    });
  }

  function messageOf(caught: unknown): { message: string; code?: string } {
    if (caught instanceof ApiError) {
      return {
        message: collectionMessage(caught.code, caught.status, caught.retryAfterSeconds),
        code: caught.code,
      };
    }
    return { message: NETWORK_ERROR };
  }

  async function refreshOverview(): Promise<void> {
    try {
      setOverview(await getHighlights());
    } catch {
      setError("Não foi possível atualizar os destaques. Recarregue a página.");
    }
  }

  async function loadList(
    next: { category: Category; sort: SortKey; includeHidden: boolean },
    append = false,
  ): Promise<void> {
    const mine = ++seq.current;
    const page = append ? list.page + 1 : 0;
    setList((current) => ({ ...current, status: "loading" }));
    try {
      const result = await getCollection(collectionQuery({ ...next, page }));
      if (mine !== seq.current) return; // a newer request owns the list now
      setList((current) => ({
        items: append ? [...current.items, ...result.items] : result.items,
        total: result.total,
        page: result.page,
        status: "ready",
      }));
    } catch {
      if (mine === seq.current) setList((current) => ({ ...current, status: "error" }));
    }
  }
  const reload = () => loadList(latest.current);

  /** Runs a mutation with the controls disabled; errors become the page alert, and stale lists reload. */
  async function act(work: () => Promise<void>, focus?: string): Promise<void> {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError(null);
    try {
      await work();
    } catch (caught) {
      const { message, code } = messageOf(caught);
      setError(message);
      if (needsRefresh(code)) {
        await Promise.all([refreshOverview(), reload()]);
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
      if (focus) focusLater(focus);
    }
  }

  // ---------- tabs and list controls ----------

  function changeTab(next: Tab) {
    if (next === tab) return;
    setTab(next);
    setQuery("");
    setError(null);
    const url = new URL(window.location.href);
    url.searchParams.set("aba", tabParam(next));
    window.history.replaceState(null, "", url.pathname + url.search);
    const category = next === "FAVORITES" ? listCat : next;
    setListCat(category);
    void loadList({ category, sort, includeHidden });
  }

  function onTabKey(event: React.KeyboardEvent, index: number) {
    const last = TABS.length - 1;
    const target =
      event.key === "ArrowRight"
        ? (index + 1) % TABS.length
        : event.key === "ArrowLeft"
          ? (index - 1 + TABS.length) % TABS.length
          : event.key === "Home"
            ? 0
            : event.key === "End"
              ? last
              : null;
    if (target === null) return;
    event.preventDefault();
    changeTab(TABS[target]);
    requestAnimationFrame(() => document.getElementById(`tab-${tabParam(TABS[target])}`)?.focus());
  }

  // ---------- highlights ----------

  function highlight(item: CollectionItem) {
    void act(async () => {
      await addHighlight({ category: highlightCategory, collectionItemId: item.id });
      if (tab === "FAVORITES") setCategories((c) => ({ ...c, [item.id]: item.category }));
      await refreshOverview();
      toast(
        <>
          <strong>{item.media.title}</strong> está nos destaques.
        </>,
      );
    }, `[data-hl-action="${item.id}"]`);
  }

  function unhighlight(entry: Highlight, focusItem: boolean) {
    const index = highlights.findIndex((h) => h.id === entry.id);
    const category = highlightCategory;
    void act(
      async () => {
        await removeHighlight(entry.id);
        await refreshOverview();
        toast(
          <>
            <strong>{entry.media.title}</strong> saiu dos destaques. Continua na coleção.
          </>,
          () => restoreHighlight(entry, category, index),
        );
      },
      focusItem ? `[data-hl-action="${entry.collectionItemId}"]` : "#hl-h",
    );
  }

  /** Desfazer: highlights the work again and puts it back where it was. */
  function restoreHighlight(entry: Highlight, category: Category | null, index: number) {
    void act(async () => {
      const restored = await addHighlight({ category, collectionItemId: entry.collectionItemId });
      const current = listOf(await getHighlights(), category).items.map((h) => h.id);
      const ids = current.filter((id) => id !== restored.id);
      ids.splice(Math.min(index, ids.length), 0, restored.id);
      await reorderHighlights({ category, ids });
      await refreshOverview();
    });
  }

  function reorder(ids: number[], message: string) {
    if (!overview || busyRef.current) return;
    const previous = overview;
    const category = highlightCategory;
    setOverview(withOrder(overview, category, ids));
    busyRef.current = true;
    setBusy(true);
    setError(null);
    reorderHighlights({ category, ids })
      .then((saved) => {
        setOverview((current) => (current ? withList(current, saved) : current));
        setAnnouncement(message);
      })
      .catch(async (caught: unknown) => {
        setOverview(previous);
        const { message: text, code } = messageOf(caught);
        setError(text);
        if (needsRefresh(code)) await refreshOverview();
      })
      .finally(() => {
        busyRef.current = false;
        setBusy(false);
      });
  }

  // ---------- collection ----------

  async function saveNote(item: CollectionItem, note: string): Promise<string | null> {
    try {
      const saved = await updateCollectionItem(item.id, { note });
      setList((current) => ({
        ...current,
        items: current.items.map((i) => (i.id === item.id ? { ...i, note: saved.note } : i)),
      }));
      return null;
    } catch (caught) {
      return messageOf(caught).message;
    }
  }

  function setHidden(item: CollectionItem, hidden: boolean) {
    void act(async () => {
      await updateCollectionItem(item.id, { hidden });
      if (!hidden && !latest.current.includeHidden) {
        await reload(); // it was out of the list: bring it back where the sort puts it
      } else {
        setList((current) =>
          latest.current.includeHidden
            ? {
                ...current,
                items: current.items.map((i) => (i.id === item.id ? { ...i, hidden } : i)),
              }
            : {
                ...current,
                items: current.items.filter((i) => i.id !== item.id),
                total: Math.max(0, current.total - 1),
              },
        );
      }
      await refreshOverview();
      toast(
        hidden ? (
          <>
            <strong>{item.media.title}</strong> foi ocultado. Ele não volta na próxima
            sincronização.
          </>
        ) : (
          <>
            <strong>{item.media.title}</strong> voltou para a coleção.
          </>
        ),
        hidden ? () => setHidden(item, false) : undefined,
      );
    }, "#coll-h");
  }

  function remove(item: CollectionItem) {
    void act(async () => {
      await removeCollectionItem(item.id);
      setList((current) => ({
        ...current,
        items: current.items.filter((i) => i.id !== item.id),
        total: Math.max(0, current.total - 1),
      }));
      await refreshOverview();
      toast(
        <>
          <strong>{item.media.title}</strong> saiu da coleção. Desfazer traz a obra de volta, sem a
          nota e sem o destaque.
        </>,
        () =>
          void act(async () => {
            await addToCollection({ category: item.category, externalId: item.media.externalId });
            await reload();
          }),
      );
    }, "#coll-h");
  }

  function onAdded(item: CollectionItem) {
    // The dialog stays open for more additions; the list behind it follows.
    if (item.category === listCat) void reload();
  }

  // ---------- render ----------

  if (!overview) {
    return (
      <AlertBox>
        Não foi possível carregar seus destaques agora. Recarregue a página em instantes.
      </AlertBox>
    );
  }

  const label = tabLabel(tab);
  const visible = filterByTitle(list.items, query);
  const unit = countLabel(listCat, list.total);

  return (
    <>
      <div className="chips" role="tablist" aria-label="Categoria">
        {TABS.map((t, index) => {
          const selected = t === tab;
          const count = listOf(overview, listCategory(t)).items.length;
          return (
            <button
              key={t}
              id={`tab-${tabParam(t)}`}
              type="button"
              role="tab"
              className="chip"
              aria-selected={selected}
              aria-controls="aba-painel"
              tabIndex={selected ? 0 : -1}
              onClick={() => changeTab(t)}
              onKeyDown={(event) => onTabKey(event, index)}
            >
              {tabLabel(t)} <span className="count">{counterLabel(count, limit)}</span>
            </button>
          );
        })}
      </div>

      <div
        id="aba-painel"
        role="tabpanel"
        aria-labelledby={`tab-${tabParam(tab)}`}
        className="next stack-l4"
      >
        {error && <AlertBox>{error}</AlertBox>}

        <section className="stack" aria-labelledby="hl-h">
          <HighlightStrip
            title={tab === "FAVORITES" ? "Destaques" : `Destaques de ${label}`}
            fullTitle={`Destaques cheios em ${label}`}
            items={highlights}
            limit={limit}
            categoryOf={(h) =>
              tab === "FAVORITES" ? (categories[h.collectionItemId] ?? "MOVIE") : tab
            }
            showTags={tab === "FAVORITES"}
            busy={busy}
            onReorder={reorder}
            onRemove={(h) => unhighlight(h, true)}
            announcement={announcement}
          />
        </section>

        <section className="stack" aria-labelledby="coll-h">
          <div className="hl-head">
            <div className="pair-2">
              <h2 id="coll-h" className="t-h2" tabIndex={-1}>
                {tab === "FAVORITES" ? "Coleção" : `Coleção de ${label}`}
              </h2>
              <p className="help">
                {list.status === "error" && list.items.length === 0
                  ? "Não foi possível carregar a coleção."
                  : tab === "FAVORITES"
                    ? `Escolha, em qualquer categoria, o que vai para Favoritos de tudo. ${unit} em ${CATEGORY_META[listCat].heading}.`
                    : `${unit}. O que vem das contas vinculadas e o que você adicionou à mão.`}
              </p>
            </div>
            <button
              type="button"
              className="mq-btn mq-btn--secondary mq-btn--sm"
              onClick={() => setAddOpen(true)}
            >
              <Plus className="icon" aria-hidden="true" />
              Adicionar obra
            </button>
          </div>

          <div className="cat-tools">
            {tab === "FAVORITES" && (
              <div className="field cat-picker">
                <label htmlFor="coll-cat" className="t-label">
                  Categoria da coleção
                </label>
                <select
                  id="coll-cat"
                  className="input"
                  value={listCat}
                  onChange={(event) => {
                    const category = event.target.value as Category;
                    setListCat(category);
                    setQuery("");
                    void loadList({ category, sort, includeHidden });
                  }}
                >
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {CATEGORY_META[c].heading}
                    </option>
                  ))}
                </select>
              </div>
            )}
            <div className="search">
              <label htmlFor="coll-q" className="sr">
                Buscar na coleção
              </label>
              <Search className="icon" aria-hidden="true" />
              <input
                id="coll-q"
                className="input"
                type="search"
                placeholder="Buscar na coleção"
                value={query}
                autoComplete="off"
                onChange={(event) => setQuery(event.target.value)}
              />
            </div>
            <fieldset className="seg">
              <legend className="sr">Ordenar por</legend>
              {SORT_OPTIONS.map((option) => (
                <label key={option.key}>
                  <input
                    type="radio"
                    name="ordem"
                    value={option.key}
                    checked={sort === option.key}
                    onChange={() => {
                      setSort(option.key);
                      void loadList({ category: listCat, sort: option.key, includeHidden });
                    }}
                  />
                  {option.label}
                </label>
              ))}
            </fieldset>
            <label className="check-inline">
              <input
                type="checkbox"
                checked={includeHidden}
                onChange={(event) => {
                  setIncludeHidden(event.target.checked);
                  void loadList({ category: listCat, sort, includeHidden: event.target.checked });
                }}
              />
              Mostrar ocultas
            </label>
          </div>

          {full && (
            <p className="help">
              <Star className="icon icon--inline" aria-hidden="true" /> Destaques cheios: para
              destacar outro, remova um da faixa acima
              {limit < 12 ? " ou espere o pack" : ""}. Adicionar à coleção continua livre.
            </p>
          )}

          <div aria-busy={list.status === "loading"}>
            {list.status === "error" && (
              <div className="stack">
                <AlertBox>Não foi possível carregar a coleção agora.</AlertBox>
                <button
                  type="button"
                  className="mq-btn mq-btn--secondary mq-btn--sm self-start"
                  onClick={() => void reload()}
                >
                  Tentar de novo
                </button>
              </div>
            )}
            {visible.length > 0 ? (
              <ul className="coll-list" aria-label="Coleção">
                {visible.map((item) => (
                  <CollectionRow
                    key={item.id}
                    item={item}
                    showCategory={tab === "FAVORITES"}
                    highlightId={highlightByItem.get(item.id) ?? null}
                    listFull={full}
                    busy={busy}
                    onHighlight={highlight}
                    onUnhighlight={(it, highlightId) => {
                      const entry = highlights.find((h) => h.id === highlightId);
                      if (entry) unhighlight(entry, true);
                    }}
                    onSaveNote={saveNote}
                    onHide={(it) => setHidden(it, true)}
                    onShow={(it) => setHidden(it, false)}
                    onRemove={remove}
                  />
                ))}
              </ul>
            ) : list.status === "loading" ? (
              <p className="t-sm" role="status">
                Carregando…
              </p>
            ) : list.status === "ready" ? (
              <div className="cat-empty">
                <p className="t-sm" role="status">
                  {query
                    ? `Nada com “${query}” nas obras carregadas.`
                    : "A coleção está vazia. Adicione uma obra ou vincule uma conta."}
                </p>
              </div>
            ) : null}
          </div>

          {query && hasMore(list.items.length, list.total) && (
            <p className="help">
              A busca olha as {list.items.length} obras já carregadas. Carregue mais para ampliar.
            </p>
          )}
          {hasMore(list.items.length, list.total) && (
            <button
              type="button"
              className={`mq-btn mq-btn--secondary mq-btn--sm self-start${
                list.status === "loading" ? " is-loading" : ""
              }`}
              disabled={list.status === "loading"}
              onClick={() => void loadList({ category: listCat, sort, includeHidden }, true)}
            >
              Carregar mais
              {list.status === "loading" && <span className="mq-spinner" aria-hidden="true" />}
            </button>
          )}
        </section>
      </div>

      <AddWorkDialog
        open={addOpen}
        initialCategory={listCat}
        onClose={() => setAddOpen(false)}
        onAdded={onAdded}
      />

      <div className="mq-toasts" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className="mq-toast" role="status">
            <p className="mq-toast__text">{t.content}</p>
            {t.undo && (
              <button
                type="button"
                className="mq-btn mq-btn--ghost mq-btn--sm"
                onClick={() => {
                  dismissToast(t.id);
                  t.undo?.();
                }}
              >
                Desfazer
              </button>
            )}
          </div>
        ))}
      </div>
    </>
  );
}
