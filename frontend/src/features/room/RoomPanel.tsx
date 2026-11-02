"use client";

import { ArrowLeft, ChevronDown, PanelRightClose } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState, type RefObject } from "react";
import {
  ItemDetailBody,
  ItemDetailError,
  ItemDetailGone,
  ItemDetailLoading,
} from "@/components/profile/ItemDetail";
import { ApiError, getJson } from "@/lib/api-client";
import {
  CATEGORY_META,
  parseItemRef,
  safeImageUrl,
  type Category,
  type PublicItemDetail,
} from "@/lib/profile-view";
import { closeItem, pushItem } from "./itemParam";
import type { RoomObject } from "./objects/RoomObject";

type Loaded = { status: "ready"; detail: PublicItemDetail } | { status: "gone" | "error" };

/** S17 inside the panel: the same body as the dialog on the profile, fetched from the same endpoint. */
function ItemPanel({
  slug,
  ownerName,
  itemRef,
  hint,
  onRetry,
}: {
  slug: string;
  ownerName: string;
  itemRef: string;
  hint: Category | null;
  onRetry: () => void;
}) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  useEffect(() => {
    let cancelled = false;
    getJson<PublicItemDetail>(
      `/api/public/profiles/${encodeURIComponent(slug)}/items/${encodeURIComponent(itemRef)}`,
    )
      .then((detail) => {
        if (!cancelled) setLoaded({ status: "ready", detail });
      })
      .catch((error: unknown) => {
        const status = error instanceof ApiError && error.status === 404 ? "gone" : "error";
        if (!cancelled) setLoaded({ status });
      });
    return () => {
      cancelled = true;
    };
  }, [slug, itemRef]);

  // Focus follows the content: the heading of the loading state, then the heading of the item.
  const status = loaded?.status ?? "loading";
  useEffect(() => {
    const heading = document.getElementById("item-h");
    if (!heading) return;
    heading.tabIndex = -1;
    heading.focus({ preventScroll: true });
  }, [status]);

  if (loaded?.status === "ready")
    return <ItemDetailBody detail={loaded.detail} ownerName={ownerName} />;
  if (loaded?.status === "gone")
    return <ItemDetailGone ownerName={ownerName} onClose={closeItem} />;
  if (loaded?.status === "error") return <ItemDetailError onRetry={onRetry} />;
  return <ItemDetailLoading category={hint} />;
}

type Props = {
  slug: string;
  ownerName: string;
  /** The selectable objects, in chip order. */
  objects: readonly RoomObject[];
  activeId: string | null;
  /** The raw `?item=` value, or null. */
  itemRaw: string | null;
  side: boolean;
  touch: boolean;
  sheetRef: RefObject<HTMLElement | null>;
  collapseRef: RefObject<HTMLButtonElement | null>;
  onChip: (id: string) => void;
  onCollapse: () => void;
  /** The sheet was dragged down far enough: clear the selection. */
  onDismiss: () => void;
};

/**
 * The info panel of S5: side panel or bottom sheet. Top to bottom: the title, the selected object's
 * items (or S17 when an item is open), the object chips and "Crie o seu quarto". No how-to text here:
 * it is the scene's accessible description.
 */
export function RoomPanel({
  slug,
  ownerName,
  objects,
  activeId,
  itemRaw,
  side,
  touch,
  sheetRef,
  collapseRef,
  onChip,
  onCollapse,
  onDismiss,
}: Props) {
  const active = objects.find((o) => o.id === activeId) ?? null;
  const itemRef = parseItemRef(itemRaw);
  const [attempt, setAttempt] = useState(0);
  const lastOpened = useRef<number | null>(null);

  // After closing an item, focus goes back to the button that opened it.
  useEffect(() => {
    if (itemRaw !== null || lastOpened.current === null) return;
    document.querySelector<HTMLElement>(`[data-item="${lastOpened.current}"]`)?.focus();
    lastOpened.current = null;
  }, [itemRaw]);

  // Touch: dragging the handle or the title down more than 72px clears the selection.
  const [dragging, setDragging] = useState(false);
  const drag = useRef<{ y: number; dy: number } | null>(null);
  const canDrag = touch && active !== null;
  function down(e: React.PointerEvent<HTMLElement>) {
    if (!canDrag || !(e.target instanceof Element) || !e.target.closest(".room-grab, .room-head"))
      return;
    drag.current = { y: e.clientY, dy: 0 };
    setDragging(true);
    e.currentTarget.setPointerCapture(e.pointerId);
  }
  function move(e: React.PointerEvent<HTMLElement>) {
    if (!drag.current) return;
    drag.current.dy = Math.max(0, e.clientY - drag.current.y);
    e.currentTarget.style.translate = `0 ${drag.current.dy}px`;
  }
  function up(e: React.PointerEvent<HTMLElement>) {
    if (!drag.current) return;
    const { dy } = drag.current;
    drag.current = null;
    setDragging(false);
    e.currentTarget.style.translate = "";
    if (dy > 72) onDismiss();
  }

  // The chips row fades at its right edge until it is scrolled to the end.
  const chips = useRef<HTMLDivElement>(null);
  const [atEnd, setAtEnd] = useState(false);
  useEffect(() => {
    const el = chips.current;
    if (!el) return;
    const check = () => setAtEnd(el.scrollLeft + el.clientWidth >= el.scrollWidth - 2);
    check();
    el.addEventListener("scroll", check, { passive: true });
    const observer = new ResizeObserver(check);
    observer.observe(el);
    return () => {
      el.removeEventListener("scroll", check);
      observer.disconnect();
    };
  }, []);

  const CollapseIcon = side ? PanelRightClose : ChevronDown;
  const hint: Category | null =
    (itemRef && active?.items.find((i) => String(i.ref) === itemRef)?.category) || null;

  return (
    <section
      ref={sheetRef}
      id="room-sheet"
      className={dragging ? "room-info is-dragging" : "room-info"}
      aria-labelledby="room-h"
      onPointerDown={down}
      onPointerMove={move}
      onPointerUp={up}
      onPointerCancel={up}
    >
      <button
        ref={collapseRef}
        type="button"
        className="ib room-collapse"
        aria-controls="room-sheet"
        aria-expanded="true"
        aria-label={touch ? "Recolher atalhos" : "Recolher painel"}
        onClick={onCollapse}
      >
        <CollapseIcon className="icon" aria-hidden="true" />
      </button>
      <div className="room-grab" aria-hidden="true" />
      <div className="room-head">
        <h1 id="room-h" className="t-h2">
          O quarto de {ownerName}
        </h1>
      </div>
      <div aria-live="polite" className="stack">
        {itemRaw !== null ? (
          <>
            <button
              type="button"
              className="mq-btn mq-btn--ghost mq-btn--sm self-start"
              aria-label={active ? `Voltar para ${active.label}` : "Voltar ao quarto"}
              onClick={closeItem}
            >
              <ArrowLeft className="icon" aria-hidden="true" />
              {active ? active.label : "Quarto"}
            </button>
            <div className="detail-wrap">
              {itemRef ? (
                <ItemPanel
                  key={`${itemRef}-${attempt}`}
                  slug={slug}
                  ownerName={ownerName}
                  itemRef={itemRef}
                  hint={hint}
                  onRetry={() => setAttempt((n) => n + 1)}
                />
              ) : (
                <ItemDetailGone ownerName={ownerName} onClose={closeItem} />
              )}
            </div>
          </>
        ) : active ? (
          <>
            <div className="pair-2">
              <h2 className="t-h3">{active.title}</h2>
              <p className="t-sm">{active.subtitle}</p>
            </div>
            {active.items.length > 0 ? (
              <ul className="list">
                {active.items.map((item) => {
                  const cover = safeImageUrl(item.imageUrl);
                  return (
                    <li key={item.ref}>
                      <button
                        type="button"
                        className="room-item"
                        data-item={item.ref}
                        onClick={() => {
                          lastOpened.current = item.ref;
                          pushItem(String(item.ref));
                        }}
                      >
                        {cover ? (
                          // eslint-disable-next-line @next/next/no-img-element -- hotlinked provider cover (ADR 0006), https only
                          <img
                            className={`thumb${CATEGORY_META[item.category].shape === "vinyl" ? " thumb--sq" : ""}`}
                            src={cover}
                            alt=""
                            referrerPolicy="no-referrer"
                          />
                        ) : (
                          <span
                            className={`thumb${CATEGORY_META[item.category].shape === "vinyl" ? " thumb--sq" : ""}`}
                            aria-hidden="true"
                          />
                        )}
                        <span className="grow pair">
                          <span className="t-poster">{item.title}</span>
                          {item.year ? <span className="t-cap">{item.year}</span> : null}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p className="t-sm">{ownerName} ainda não destacou nada aqui.</p>
            )}
          </>
        ) : null}
      </div>
      <div
        ref={chips}
        className={atEnd ? "chips room-chips is-end" : "chips room-chips"}
        role="group"
        aria-label="Objetos do quarto"
      >
        {objects.map((object) => (
          <button
            key={object.id}
            type="button"
            className="chip"
            aria-pressed={activeId === object.id}
            onClick={() => onChip(object.id)}
          >
            {object.label}
          </button>
        ))}
      </div>
      <Link
        href="/cadastro"
        prefetch={false}
        className="mq-btn mq-btn--primary self-start room-cta"
      >
        Crie o seu quarto
      </Link>
    </section>
  );
}
