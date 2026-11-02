"use client";

import {
  ArrowLeft,
  ChevronUp,
  Lightbulb,
  PanelRightOpen,
  RotateCcw,
  RotateCw,
  Scan,
  Share2,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import Link from "next/link";
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import { parseItemRef } from "@/lib/profile-view";
import { PITCH_HOME, YAW_HOME, YAW_STEP, ZOOM_STEP, frameTarget, isSideLayout } from "./camera";
import { measureRoomLayout, visibleArea } from "./layout";
import { LabelLayer } from "./LabelLayer";
import { ObjectView } from "./ObjectView";
import { closeItem, dropItem, readItemParam, useItemParam } from "./itemParam";
import { buildRoom, type RoomSection } from "./objects/buildRoom";
import type { SceneActions } from "./objects/RoomObject";
import { RoomPanel } from "./RoomPanel";
import { usePointerGestures, type PanMode } from "./usePointerGestures";
import { CameraController } from "./CameraController";

// S5. The scene is a RoomObject[]: this component iterates it and calls the same methods on every
// object (boxes, anchor, onSelect). It never checks which kind of object it holds.

const PANEL_KEY = "mq-quarto-painel";
const HINT_KEY = "mq-quarto-dica";

function subscribeMedia(query: string) {
  return (onChange: () => void) => {
    const media = window.matchMedia(query);
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  };
}
/** A CSS media query, live. */
function useMedia(query: string): boolean {
  const subscribe = useMemo(() => subscribeMedia(query), [query]);
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(query).matches,
    () => false,
  );
}

function subscribeResize(onChange: () => void) {
  window.addEventListener("resize", onChange);
  return () => window.removeEventListener("resize", onChange);
}
/** The window size as "WxH" (a primitive, so React can compare snapshots). */
function useViewport(): { w: number; h: number; key: string } {
  const key = useSyncExternalStore(
    subscribeResize,
    () => `${window.innerWidth}x${window.innerHeight}`,
    () => "1024x768",
  );
  const [w, h] = key.split("x").map(Number);
  return { w, h, key };
}

function readStored(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}
function store(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // private mode or blocked storage: the choice just is not remembered
  }
}

function ShareRoom({ slug }: { slug: string }) {
  const [state, setState] = useState<"idle" | "copied" | "failed">("idle");
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);
  async function share() {
    const url = `${window.location.origin}/${encodeURIComponent(slug)}/quarto`;
    clearTimeout(timer.current);
    try {
      if (typeof navigator.share === "function") {
        await navigator.share({ url, title: document.title });
        return;
      }
      await navigator.clipboard.writeText(url);
      setState("copied");
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return; // the visitor closed the sheet
      setState("failed");
    }
    timer.current = setTimeout(() => setState("idle"), 2500);
  }
  return (
    <>
      <button
        type="button"
        className="mq-btn mq-btn--secondary mq-btn--sm mq-btn--icon"
        aria-label="Compartilhar o quarto"
        onClick={share}
      >
        <Share2 className="icon" aria-hidden="true" />
      </button>
      <span className="sr" role="status">
        {state === "copied"
          ? "Link copiado"
          : state === "failed"
            ? "Não foi possível copiar o link"
            : ""}
      </span>
    </>
  );
}

type Props = { slug: string; ownerName: string; sections: RoomSection[] };

export default function RoomScene({ slug, ownerName, sections }: Props) {
  const objects = useMemo(() => buildRoom(sections), [sections]);
  const selectable = useMemo(() => objects.filter((o) => o.isSelectable()), [objects]);
  const byId = useMemo(() => new Map(selectable.map((o) => [o.id, o])), [selectable]);
  const pins = useMemo(() => selectable.map((o) => ({ id: o.id, at: o.anchor() })), [selectable]);
  const [camera] = useState(() => new CameraController());
  useEffect(() => {
    camera.setPins(pins);
  }, [camera, pins]);
  useEffect(() => () => camera.dispose(), [camera]);

  const touch = useMedia("(hover: none)");
  const viewport = useViewport();
  const side = isSideLayout(viewport.w, viewport.h, touch);

  // ----- selection -----
  const itemRaw = useItemParam();
  const groupOfItem = useCallback(
    (raw: string | null) => {
      const ref = parseItemRef(raw);
      return ref
        ? (selectable.find((o) => o.items.some((i) => String(i.ref) === ref))?.id ?? null)
        : null;
    },
    [selectable],
  );
  const [sel, setSel] = useState<string | null>(() => groupOfItem(readItemParam()));
  const [hover, setHover] = useState<string | null>(null);
  const [focus, setFocus] = useState<string | null>(null);
  const [lamp, setLamp] = useState(true);
  const [revealed, setRevealed] = useState<string | null>(null);
  const [panMode, setPanMode] = useState<PanMode>(null);
  const [collapsed, setCollapsedState] = useState(() => readStored(PANEL_KEY) === "recolhido");
  const [hint, setHint] = useState<"on" | "gone" | "off">(() =>
    window.matchMedia("(hover: none)").matches && readStored(HINT_KEY) !== "1" ? "on" : "off",
  );
  // An open item selects its object (a shared link, Back, Forward), so the panel and the glow agree.
  const activeId = groupOfItem(itemRaw) ?? sel;

  const roomRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const barRef = useRef<HTMLDivElement>(null);
  const toolsRef = useRef<HTMLDivElement>(null);
  const sheetRef = useRef<HTMLElement>(null);
  const expandRef = useRef<HTMLButtonElement>(null);
  const collapseRef = useRef<HTMLButtonElement>(null);
  const focusAfter = useRef<"expand" | "collapse" | null>(null);
  const [frameRequest, setFrameRequest] = useState<{ id: string; n: number } | null>(null);

  // ----- layout: the room never sits under the panel -----
  const live = useRef({ collapsed, side, touch });
  useLayoutEffect(() => {
    live.current = { collapsed, side, touch };
  });

  const relayout = useCallback(
    (jump: boolean) => {
      const fit = measureRoomLayout(
        {
          room: roomRef.current,
          bar: barRef.current,
          tools: toolsRef.current,
          sheet: sheetRef.current,
          expand: expandRef.current,
        },
        live.current,
      );
      if (fit) camera.setFit(fit, jump);
    },
    [camera],
  );
  const relayoutRef = useRef<(jump: boolean) => void>(() => {});
  useLayoutEffect(() => {
    relayoutRef.current = relayout;
  });

  const lastViewport = useRef<string | null>(null);
  useLayoutEffect(() => {
    // a resize jumps; a panel change (side, folded, touch) eases
    relayoutRef.current(lastViewport.current !== viewport.key);
    lastViewport.current = viewport.key;
  }, [viewport.key, side, collapsed, touch]);

  // The panel changes height with the selection or an item: the room eases into the new free area.
  useEffect(() => {
    const sheet = sheetRef.current;
    if (!sheet) return;
    const observer = new ResizeObserver(() => relayoutRef.current(false));
    observer.observe(sheet);
    return () => observer.disconnect();
  }, []);

  // Touch: pan and zoom so the selected object sits in the visible area above the sheet.
  useEffect(() => {
    if (!frameRequest) return;
    const id = requestAnimationFrame(() => {
      relayoutRef.current(false);
      const object = byId.get(frameRequest.id);
      const sheet = sheetRef.current;
      const tools = toolsRef.current;
      if (!object || !sheet || !tools) return;
      const visible = visibleArea(tools, sheet, live.current.side);
      camera.moveTo(frameTarget(object.bounds(), camera.target(), visible));
    });
    return () => cancelAnimationFrame(id);
  }, [frameRequest, byId, camera]);

  // ----- actions -----
  const dropOpenItem = () => {
    if (readItemParam() !== null) dropItem();
  };

  function setCollapsed(next: boolean, moveFocus = false) {
    setCollapsedState(next);
    store(PANEL_KEY, next ? "recolhido" : "aberto");
    if (next) {
      setHint("off");
      clearSelection();
    }
    if (moveFocus) focusAfter.current = next ? "expand" : "collapse";
  }
  useEffect(() => {
    const target = focusAfter.current === "expand" ? expandRef.current : collapseRef.current;
    if (focusAfter.current) target?.focus();
    focusAfter.current = null;
  }, [collapsed]);

  function select(id: string) {
    dropOpenItem();
    setSel(id);
    setRevealed(null);
    if (collapsed) setCollapsed(false);
    if (touch) setFrameRequest((previous) => ({ id, n: (previous?.n ?? 0) + 1 }));
  }
  function clearSelection() {
    if (sel === null && itemRaw === null) return;
    setSel(null);
    setRevealed(null);
    dropOpenItem();
    camera.moveTo({ zoom: 1, px: 0, py: 0 });
  }

  /** What an object is allowed to do to the scene when clicked (RoomObject.onSelect). */
  const actions: SceneActions = { select, reveal: setRevealed };
  function pick(id: string | null) {
    if (id === null) clearSelection();
    else byId.get(id)?.onSelect(actions);
  }

  usePointerGestures(stageRef, {
    camera,
    touch,
    onHover: setHover,
    onPick: pick,
    onPanMode: setPanMode,
  });

  // Esc closes the open item; on touch it also clears the selection.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== "Escape") return;
      if (itemRaw !== null) {
        closeItem();
      } else if (activeId && touch) {
        clearSelection();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  });

  // First touch visit: every label for about 2s with the hint, then they fade and never return.
  useEffect(() => {
    if (hint !== "on") return;
    const fade = setTimeout(() => {
      setHint((h) => (h === "on" ? "gone" : h));
      store(HINT_KEY, "1");
    }, 2000);
    const hide = setTimeout(() => setHint((h) => (h === "gone" ? "off" : h)), 2450);
    return () => {
      clearTimeout(fade);
      clearTimeout(hide);
    };
  }, [hint === "on"]); // eslint-disable-line react-hooks/exhaustive-deps -- only the on/off edge matters

  // ----- what is lit -----
  const lit = new Set<string>();
  for (const id of [activeId, hover, focus]) if (id) lit.add(id);
  const labelsShown = new Set(hint === "on" ? selectable.map((o) => o.id) : lit);

  const className = [
    "page room",
    touch ? "room--touch" : "",
    side ? "room--side" : "",
    activeId ? "has-sel" : "",
    collapsed ? "is-collapsed" : "",
    panMode === "ready" ? "is-pan-ready" : "",
    panMode === "panning" ? "is-panning" : "",
  ]
    .filter(Boolean)
    .join(" ");

  const sceneLabel = touch
    ? "Quarto em 3D. Arraste para girar, use dois dedos para mover e aproximar. Toque num objeto ou num atalho abaixo."
    : "Quarto em 3D. Arraste para girar e inclinar; para mover, arraste com o botão direito ou com Espaço apertado; a roda aproxima. Clique num objeto para ver o que tem ali.";

  return (
    <div ref={roomRef} className={className}>
      <div ref={stageRef} className="room-stage" role="group" aria-label={sceneLabel}>
        <div className="room-anchor" ref={(el) => camera.registerAnchor(el)}>
          <div className="room-world" ref={(el) => camera.registerWorld(el)}>
            {objects.map((object) => (
              <ObjectView
                key={object.id}
                object={object}
                lamp={lamp}
                revealed={revealed === object.id}
                lit={lit.has(object.id)}
              />
            ))}
          </div>
        </div>
        <div className="room-anchor room-anchor--flat" ref={(el) => camera.registerAnchor(el)}>
          <LabelLayer
            objects={selectable}
            shown={labelsShown}
            selected={activeId}
            registerPin={(id, el) => camera.registerPin(id, el)}
            onPick={pick}
            onFocusChange={setFocus}
          />
        </div>
      </div>
      <p
        className={hint === "gone" ? "room-hint is-gone" : "room-hint"}
        role="status"
        hidden={hint === "off"}
      >
        Toque em um objeto ou use os atalhos abaixo. Arraste para girar, use dois dedos para mover e
        aproximar.
      </p>

      <div ref={barRef} className="room-bar">
        <Link href={`/${encodeURIComponent(slug)}`} className="mq-btn mq-btn--secondary mq-btn--sm">
          <ArrowLeft className="icon" aria-hidden="true" />
          Voltar ao perfil
        </Link>
        <ShareRoom slug={slug} />
      </div>

      <div ref={toolsRef} className="room-tools" role="toolbar" aria-label="Câmera e luz">
        <button
          type="button"
          className="mq-btn mq-btn--icon"
          aria-label="Girar para a esquerda"
          onClick={() => camera.moveTo({ yaw: camera.target().yaw + YAW_STEP })}
        >
          <RotateCcw className="icon" aria-hidden="true" />
        </button>
        <button
          type="button"
          className="mq-btn mq-btn--icon"
          aria-label="Girar para a direita"
          onClick={() => camera.moveTo({ yaw: camera.target().yaw - YAW_STEP })}
        >
          <RotateCw className="icon" aria-hidden="true" />
        </button>
        <span className="sep" aria-hidden="true" />
        <button
          type="button"
          className="mq-btn mq-btn--icon"
          aria-label="Aproximar"
          onClick={() => camera.moveTo({ zoom: camera.target().zoom * ZOOM_STEP })}
        >
          <ZoomIn className="icon" aria-hidden="true" />
        </button>
        <button
          type="button"
          className="mq-btn mq-btn--icon"
          aria-label="Afastar"
          onClick={() => camera.moveTo({ zoom: camera.target().zoom / ZOOM_STEP })}
        >
          <ZoomOut className="icon" aria-hidden="true" />
        </button>
        <button
          type="button"
          className="mq-btn mq-btn--icon"
          aria-label="Centralizar o quarto"
          onClick={() => camera.moveTo({ zoom: 1, px: 0, py: 0, yaw: YAW_HOME, pitch: PITCH_HOME })}
        >
          <Scan className="icon" aria-hidden="true" />
        </button>
        <span className="sep" aria-hidden="true" />
        <button
          type="button"
          className="mq-btn mq-btn--icon"
          aria-label="Abajur"
          aria-pressed={lamp}
          onClick={() => setLamp((on) => !on)}
        >
          <Lightbulb className="icon" aria-hidden="true" />
        </button>
      </div>

      <button
        ref={expandRef}
        type="button"
        className="mq-btn mq-btn--secondary mq-btn--sm room-expand"
        aria-controls="room-sheet"
        aria-expanded="false"
        hidden={!collapsed}
        onClick={() => setCollapsed(false, true)}
      >
        {side ? (
          <PanelRightOpen className="icon" aria-hidden="true" />
        ) : (
          <ChevronUp className="icon" aria-hidden="true" />
        )}
        <span>{touch ? "Atalhos" : "Mostrar painel"}</span>
      </button>
      <RoomPanel
        slug={slug}
        ownerName={ownerName}
        objects={selectable}
        activeId={activeId}
        itemRaw={itemRaw}
        side={side}
        touch={touch}
        sheetRef={sheetRef}
        collapseRef={collapseRef}
        onChip={pick}
        onCollapse={() => setCollapsed(true, true)}
        onDismiss={clearSelection}
      />
    </div>
  );
}
