"use client";

import { ChevronLeft, ChevronRight, Plus, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { HighlightCard } from "@/components/profile/HighlightCard";
import {
  focusDirectionAfterMove,
  isFull,
  meterLabel,
  moveId,
  moveTo,
  PACK_LIMIT,
  showUpsell,
  type Highlight,
} from "@/lib/collection";
import type { Category } from "@/lib/profile-view";

type Props = {
  /** "Destaques de Animes" / "Destaques". */
  title: string;
  /** "Animes está cheio". */
  fullTitle: string;
  items: Highlight[];
  limit: number;
  /** Category of every entry; favorites mix media, so each card needs its own. */
  categoryOf: (item: Highlight) => Category;
  showTags: boolean;
  /** True while a request of this screen is running: every control is disabled. */
  busy: boolean;
  onReorder: (ids: number[], announcement: string) => void;
  onRemove: (item: Highlight) => void;
  /** Polite text for screen readers after a move. */
  announcement: string;
};

function Meter({ count, limit }: { count: number; limit: number }) {
  return (
    <p className="mq-slots">
      <span className="mq-slots__bar" aria-hidden="true">
        {Array.from({ length: limit }, (_, i) => (
          <span key={i} className={i < count ? undefined : "is-free"} />
        ))}
      </span>
      {meterLabel(count, limit)}
    </p>
  );
}

/**
 * The highlights of the open tab, in the order of the wall. Reordering has two ways in: drag and
 * drop (mouse) and the "Mover para antes / depois" buttons (keyboard, touch, screen readers); both
 * end in one onReorder call with the new id order, which the screen saves with PUT /api/highlights/order.
 */
export function HighlightStrip({
  title,
  fullTitle,
  items,
  limit,
  categoryOf,
  showTags,
  busy,
  onReorder,
  onRemove,
  announcement,
}: Props) {
  const count = items.length;
  const full = isFull(count, limit);
  const ids = items.map((h) => h.id);
  const list = useRef<HTMLOListElement>(null);
  /** The button to focus after a move: the list re-renders, but the element must keep the user's place. */
  const refocus = useRef<{ id: number; direction: -1 | 1 } | null>(null);
  const [dragId, setDragId] = useState<number | null>(null);
  const [overId, setOverId] = useState<number | null>(null);

  useEffect(() => {
    const target = refocus.current;
    if (!target || busy) return;
    refocus.current = null;
    const button = list.current?.querySelector<HTMLButtonElement>(
      `[data-move="${target.id}"][data-dir="${target.direction}"]`,
    );
    button?.focus();
  }, [items, busy]);

  function move(item: Highlight, direction: -1 | 1) {
    const next = moveId(ids, item.id, direction);
    if (next === ids) return;
    const index = next.indexOf(item.id);
    refocus.current = { id: item.id, direction: focusDirectionAfterMove(index, count, direction) };
    onReorder(next, `${item.media.title}: posição ${index + 1} de ${count}.`);
  }

  function drop(target: Highlight) {
    const from = ids.indexOf(dragId ?? -1);
    const to = ids.indexOf(target.id);
    setDragId(null);
    setOverId(null);
    const next = moveTo(ids, from, to);
    if (next === ids) return;
    const moved = items[from];
    onReorder(next, `${moved.media.title}: posição ${to + 1} de ${count}.`);
  }

  return (
    <>
      <div className="hl-head">
        <div className="pair-2">
          <h2 id="hl-h" className="t-h2" tabIndex={-1}>
            {title}
          </h2>
          <p className="help">
            Na ordem da parede. Arraste ou use as setas dos botões para mudar a ordem.
          </p>
        </div>
        <Meter count={count} limit={limit} />
      </div>

      {showUpsell(count, limit) && (
        <div className="mq-upsell">
          <p className="mq-upsell__text" id="hl-full">
            <strong>{fullTitle}</strong>
            No plano grátis cabem {limit} destaques. Com o pack de destaques, cabem {PACK_LIMIT}. O
            pack ainda não está à venda: chega em breve. A coleção continua sem limite.
          </p>
        </div>
      )}
      {full && limit >= PACK_LIMIT && (
        <p className="help" id="hl-full">
          Os {limit} destaques estão ocupados. Remova um para destacar outro.
        </p>
      )}

      {count > 0 ? (
        <ol
          ref={list}
          className={`hl-strip${limit > 4 ? " hl-strip--pack" : ""}`}
          aria-label="Destaques, na ordem da parede"
        >
          {items.map((item, index) => {
            const name = item.media.title;
            return (
              <li
                key={item.id}
                className={`hl-slot hl-slot--drag${dragId === item.id ? " is-dragging" : ""}${
                  overId === item.id && dragId !== item.id ? " is-over" : ""
                }`}
                draggable={!busy}
                onDragStart={(event) => {
                  event.dataTransfer.effectAllowed = "move";
                  event.dataTransfer.setData("text/plain", String(item.id));
                  setDragId(item.id);
                }}
                onDragOver={(event) => {
                  if (dragId === null) return;
                  event.preventDefault();
                  event.dataTransfer.dropEffect = "move";
                  setOverId(item.id);
                }}
                onDragLeave={() => setOverId((current) => (current === item.id ? null : current))}
                onDrop={(event) => {
                  event.preventDefault();
                  drop(item);
                }}
                onDragEnd={() => {
                  setDragId(null);
                  setOverId(null);
                }}
              >
                <HighlightCard
                  interactive={false}
                  showTag={showTags}
                  item={{
                    ref: item.collectionItemId,
                    category: categoryOf(item),
                    title: name,
                    year: item.media.year,
                    imageUrl: item.media.imageUrl,
                    note: null,
                  }}
                />
                <div className="hl-ctrl">
                  <button
                    type="button"
                    className="ib"
                    data-move={item.id}
                    data-dir="-1"
                    aria-label={`Mover ${name} para antes`}
                    disabled={busy || index === 0}
                    onClick={() => move(item, -1)}
                  >
                    <ChevronLeft className="icon" aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    className="ib"
                    data-move={item.id}
                    data-dir="1"
                    aria-label={`Mover ${name} para depois`}
                    disabled={busy || index === count - 1}
                    onClick={() => move(item, 1)}
                  >
                    <ChevronRight className="icon" aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    className="ib ib--danger"
                    aria-label={`Remover ${name} dos destaques`}
                    disabled={busy}
                    onClick={() => onRemove(item)}
                  >
                    <X className="icon" aria-hidden="true" />
                  </button>
                </div>
              </li>
            );
          })}
          {!full && (
            <li className="hl-slot">
              <a className="mq-slot hl-empty" href="#coll-h">
                <Plus className="icon" aria-hidden="true" />
                <span>Destaque um item da coleção</span>
              </a>
            </li>
          )}
        </ol>
      ) : (
        <div className="cat-empty">
          <p className="t-sm">
            Nenhum destaque ainda. O perfil mostra os destaques; escolha na coleção abaixo.
          </p>
        </div>
      )}
      <p className="sr" role="status" aria-live="polite">
        {announcement}
      </p>
    </>
  );
}
