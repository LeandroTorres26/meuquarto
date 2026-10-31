"use client";

import { Ellipsis, Eye, EyeOff, Star, Trash2 } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import {
  checkNote,
  isSynced,
  NOTE_MAX,
  noteChanged,
  noteLength,
  sourceLabels,
  type CollectionItem,
} from "@/lib/collection";
import { CATEGORY_META, safeImageUrl } from "@/lib/profile-view";

type Props = {
  item: CollectionItem;
  /** Favorites mixes media, so the category goes in the caption. */
  showCategory: boolean;
  /** The highlight of this item in the open list, if it is highlighted there. */
  highlightId: number | null;
  /** "Destacar" is disabled while the open list is full. */
  listFull: boolean;
  busy: boolean;
  onHighlight: (item: CollectionItem) => void;
  onUnhighlight: (item: CollectionItem, highlightId: number) => void;
  /** Saves the note; resolves to an error message or null. */
  onSaveNote: (item: CollectionItem, note: string) => Promise<string | null>;
  onHide: (item: CollectionItem) => void;
  onShow: (item: CollectionItem) => void;
  onRemove: (item: CollectionItem) => void;
};

/** One work of the collection (S7): cover, title, origin, note, "Destacar" and the more-actions menu. */
export function CollectionRow({
  item,
  showCategory,
  highlightId,
  listFull,
  busy,
  onHighlight,
  onUnhighlight,
  onSaveNote,
  onHide,
  onShow,
  onRemove,
}: Props) {
  const id = useId();
  const meta = CATEGORY_META[item.category];
  const cover = safeImageUrl(item.media.imageUrl);
  const name = item.media.title;
  const synced = isSynced(item);
  const labels = sourceLabels(item.sources);

  // The note is a draft until the field loses focus (or Enter); then it is saved if it changed.
  const stored = item.note ?? "";
  const [draft, setDraft] = useState(stored);
  const [lastStored, setLastStored] = useState(stored);
  if (lastStored !== stored) {
    setLastStored(stored);
    setDraft(stored);
  }
  const [noteState, setNoteState] = useState<{ kind: "saved" | "error"; message: string } | null>(
    null,
  );
  const [savingNote, setSavingNote] = useState(false);
  const length = noteLength(draft);

  async function saveNote() {
    if (savingNote || !noteChanged(draft, item.note)) return;
    const check = checkNote(draft);
    if (!check.ok) {
      setNoteState({ kind: "error", message: check.message });
      return;
    }
    setSavingNote(true);
    const error = await onSaveNote(item, check.value);
    setSavingNote(false);
    setNoteState(
      error
        ? { kind: "error", message: error }
        : { kind: "saved", message: check.value ? "Nota salva." : "Nota removida." },
    );
  }

  // More-actions menu: opens on the button, Esc and outside clicks close it, arrows move between items.
  const [menuOpen, setMenuOpen] = useState(false);
  const menu = useRef<HTMLDivElement>(null);
  const menuButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    menu.current?.querySelector<HTMLButtonElement>("button")?.focus();
    function onPointer(event: MouseEvent) {
      if (!menu.current?.parentElement?.contains(event.target as Node)) setMenuOpen(false);
    }
    document.addEventListener("mousedown", onPointer);
    return () => document.removeEventListener("mousedown", onPointer);
  }, [menuOpen]);

  function onMenuKey(event: React.KeyboardEvent) {
    if (event.key === "Escape") {
      event.stopPropagation();
      setMenuOpen(false);
      menuButton.current?.focus();
      return;
    }
    if (event.key !== "ArrowDown" && event.key !== "ArrowUp") return;
    event.preventDefault();
    const buttons = Array.from(menu.current?.querySelectorAll<HTMLButtonElement>("button") ?? []);
    const at = buttons.indexOf(document.activeElement as HTMLButtonElement);
    const next = (at + (event.key === "ArrowDown" ? 1 : -1) + buttons.length) % buttons.length;
    buttons[next]?.focus();
  }

  function choose(action: (item: CollectionItem) => void) {
    setMenuOpen(false);
    action(item);
  }

  const noteHint = `${id}-note-hint`;

  return (
    <li className="coll-row">
      {cover ? (
        // eslint-disable-next-line @next/next/no-img-element -- hotlinked provider covers (ADR 0006), https only
        <img
          className={`thumb${item.category === "MUSIC" ? " thumb--sq" : ""}`}
          src={cover}
          alt=""
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
        />
      ) : (
        <span
          className={`thumb${item.category === "MUSIC" ? " thumb--sq" : ""}`}
          aria-hidden="true"
        />
      )}
      <div className="meta">
        <p className="t-poster">{name}</p>
        <p className="line">
          <span className="t-cap">
            {showCategory ? `${meta.sticker}${item.media.year ? ", " : ""}` : ""}
            {item.media.year ?? ""}
          </span>
          {labels.map((label) => (
            <span
              key={label}
              className={`src-badge${label === "Manual" ? " src-badge--manual" : ""}`}
            >
              {label}
            </span>
          ))}
          {item.hidden && <span className="src-badge">Oculto</span>}
          {highlightId !== null && (
            <span className="hl-seal hl-seal--row">
              <Star className="icon" aria-hidden="true" />
              Destaque
            </span>
          )}
        </p>
      </div>
      <div className="note">
        <label className="sr" htmlFor={`${id}-note`}>
          Nota para {name}
        </label>
        <input
          id={`${id}-note`}
          className="input input--sm"
          value={draft}
          placeholder="Adicionar nota (opcional)"
          autoComplete="off"
          aria-describedby={noteHint}
          aria-invalid={noteState?.kind === "error" || length > NOTE_MAX ? true : undefined}
          disabled={savingNote}
          onChange={(event) => {
            setDraft(event.target.value);
            setNoteState(null);
          }}
          onBlur={saveNote}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              void saveNote();
            }
          }}
        />
        <p
          id={noteHint}
          className={`t-cap${noteState?.kind === "error" || length > NOTE_MAX ? " note-bad" : ""}`}
          role={noteState?.kind === "error" ? "alert" : "status"}
        >
          {noteState ? noteState.message : length > 0 ? `${length}/${NOTE_MAX}` : ""}
          {noteState && length > 0 && noteState.kind === "saved" ? ` ${length}/${NOTE_MAX}` : ""}
        </p>
      </div>
      <div className="acts">
        {highlightId !== null ? (
          <button
            type="button"
            className="mq-btn mq-btn--ghost mq-btn--sm"
            data-hl-action={item.id}
            disabled={busy}
            onClick={() => onUnhighlight(item, highlightId)}
          >
            Remover dos destaques
          </button>
        ) : (
          <button
            type="button"
            className="mq-btn mq-btn--secondary mq-btn--sm"
            data-hl-action={item.id}
            disabled={busy || listFull || item.hidden}
            aria-describedby={listFull ? "hl-full" : undefined}
            title={item.hidden ? "Mostre a obra para destacar" : undefined}
            onClick={() => onHighlight(item)}
          >
            <Star className="icon" aria-hidden="true" />
            Destacar
          </button>
        )}
        <div className="menu">
          <button
            ref={menuButton}
            type="button"
            className="ib"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            aria-label={`Mais ações para ${name}`}
            disabled={busy}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <Ellipsis className="icon" aria-hidden="true" />
          </button>
          {menuOpen && (
            <div ref={menu} className="menu__pop" role="menu" onKeyDown={onMenuKey}>
              {item.hidden ? (
                <button type="button" role="menuitem" onClick={() => choose(onShow)}>
                  <Eye className="icon" aria-hidden="true" />
                  Mostrar na coleção
                </button>
              ) : synced ? (
                <>
                  <button type="button" role="menuitem" onClick={() => choose(onHide)}>
                    <EyeOff className="icon" aria-hidden="true" />
                    Ocultar da coleção
                  </button>
                  <p className="t-cap menu__note">
                    Veio de {labels.filter((l) => l !== "Manual").join(" e ")}: voltaria na próxima
                    sincronização, por isso só dá para ocultar.
                  </p>
                </>
              ) : (
                <button
                  type="button"
                  role="menuitem"
                  className="is-danger"
                  onClick={() => choose(onRemove)}
                >
                  <Trash2 className="icon" aria-hidden="true" />
                  Remover da coleção
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </li>
  );
}
