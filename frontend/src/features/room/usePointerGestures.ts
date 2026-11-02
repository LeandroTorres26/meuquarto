"use client";

import { useEffect, useRef, type RefObject } from "react";
import {
  LIMITS,
  PITCH_PER_PX,
  YAW_PER_PX,
  clamp,
  clampPan,
  rawPitchFor,
  softPitch,
  wheelZoom,
  type Camera,
} from "./camera";
import type { CameraController } from "./CameraController";

export type PanMode = "ready" | "panning" | null;

type Options = {
  camera: CameraController;
  touch: boolean;
  /** The mouse moved over an object (id) or over nothing (null). */
  onHover: (id: string | null) => void;
  /** A tap or click ended on an object (id) or on empty space (null). */
  onPick: (id: string | null) => void;
  onPanMode: (mode: PanMode) => void;
};

type Pointer = { x: number; y: number };

/** The id of the selectable object under a screen point: a lit box (`data-g`) or a visible label (`data-k`). */
function objectAt(x: number, y: number): string | null {
  const el = document.elementFromPoint(x, y);
  const hit = el?.closest<HTMLElement>('.bx[data-g]:not([data-g=""]), .room-pin');
  return hit ? (hit.dataset.g ?? hit.dataset.k ?? null) : null;
}

/**
 * Mouse and touch input of the scene, ported from quarto.html:
 * - one pointer drags: horizontal rotates (0.35 deg/px), vertical tilts with a mouse only (soft limits);
 * - right or middle button, Space or Shift + drag pans (mouse); two fingers pinch to zoom and pan;
 * - the wheel zooms (Shift + wheel pans sideways) and the page never scrolls;
 * - a press that did not move picks the object under it (or clears, on touch).
 * Native listeners on purpose: the wheel listener must be non-passive.
 */
export function usePointerGestures(stageRef: RefObject<HTMLElement | null>, options: Options) {
  // The latest options, read by the listeners installed once.
  const latest = useRef(options);
  useEffect(() => {
    latest.current = options;
  });

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    const pointers = new Map<number, Pointer>();
    let drag: {
      x: number;
      y: number;
      yaw: number;
      raw: number;
      px: number;
      py: number;
      pan: boolean;
      moved: boolean;
    } | null = null;
    let pinch: { d: number; zoom: number; x: number; y: number; px: number; py: number } | null =
      null;
    let spaceDown = false;

    const two = () => {
      const [a, b] = [...pointers.values()];
      return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, d: Math.hypot(a.x - b.x, a.y - b.y) };
    };
    const camera = () => latest.current.camera;

    function onKeyDown(e: KeyboardEvent) {
      const target = e.target instanceof Element ? e.target : null;
      if (
        e.code !== "Space" ||
        latest.current.touch ||
        target?.closest("button, a, input, textarea")
      )
        return;
      spaceDown = true;
      latest.current.onPanMode("ready");
      e.preventDefault();
    }
    function onKeyUp(e: KeyboardEvent) {
      if (e.code !== "Space") return;
      spaceDown = false;
      latest.current.onPanMode(null);
    }

    function onDown(e: PointerEvent) {
      if (e.target instanceof Element && e.target.closest("button")) return;
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      const view = camera().camera();
      const goal = camera().target();
      const pan =
        e.pointerType === "mouse" && (e.button === 1 || e.button === 2 || spaceDown || e.shiftKey);
      if (pointers.size === 1) {
        drag = {
          x: e.clientX,
          y: e.clientY,
          yaw: view.yaw,
          raw: camera().rawPitch(),
          px: view.px,
          py: view.py,
          pan,
          moved: false,
        };
        if (pan) {
          e.preventDefault();
          latest.current.onPanMode("panning");
        }
      }
      if (pointers.size === 2) {
        const c = two();
        pinch = {
          d: c.d,
          zoom: goal.zoom,
          x: c.x,
          y: c.y,
          px: goal.px,
          py: goal.py,
        };
        drag = null;
      }
      stage!.setPointerCapture(e.pointerId);
    }

    function onMove(e: PointerEvent) {
      if (!pointers.has(e.pointerId)) {
        if (e.pointerType === "mouse" && !latest.current.touch) {
          latest.current.onHover(objectAt(e.clientX, e.clientY));
        }
        return;
      }
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pinch && pointers.size === 2) {
        const c = two();
        camera().moveTo({
          zoom: (pinch.zoom * c.d) / pinch.d,
          px: pinch.px + c.x - pinch.x,
          py: pinch.py + c.y - pinch.y,
        });
        return;
      }
      if (!drag) return;
      const dx = e.clientX - drag.x;
      const dy = e.clientY - drag.y;
      if (Math.abs(dx) + Math.abs(dy) > 5) drag.moved = true;
      const ctl = camera();
      const view = ctl.camera();
      if (drag.pan) {
        const pan = clampPan(
          { px: drag.px + dx, py: drag.py + dy },
          view.fs,
          view.zoom,
          ctl.free(),
        );
        ctl.setNow(pan);
        return;
      }
      const next: Partial<Camera> = { yaw: clamp(drag.yaw + dx * YAW_PER_PX, LIMITS.yaw) };
      // no tilt on touch: the vertical drag belongs to the sheet
      if (!latest.current.touch) {
        const raw = drag.raw + dy * PITCH_PER_PX;
        ctl.setRawPitch(raw);
        next.pitch = softPitch(raw);
      }
      ctl.setNow(next);
    }

    function onUp(e: PointerEvent) {
      if (!pointers.delete(e.pointerId)) return;
      latest.current.onPanMode(spaceDown ? "ready" : null);
      if (drag && !drag.moved && !drag.pan && e.type === "pointerup") {
        const id = objectAt(e.clientX, e.clientY);
        if (id) latest.current.onPick(id);
        else if (latest.current.touch) latest.current.onPick(null);
      }
      if (drag && !drag.pan) camera().setRawPitch(rawPitchFor(camera().camera().pitch));
      if (pointers.size === 0) {
        drag = null;
        pinch = null;
      }
    }

    function onLeave(e: PointerEvent) {
      if (e.pointerType === "mouse" && !pointers.has(e.pointerId)) latest.current.onHover(null);
    }

    function onWheel(e: WheelEvent) {
      e.preventDefault();
      const ctl = camera();
      const goal = ctl.target();
      if (e.shiftKey) {
        ctl.moveTo({ px: goal.px - (e.deltaX || e.deltaY) });
        return;
      }
      ctl.moveTo({ zoom: wheelZoom(goal.zoom, e.deltaY, e.ctrlKey) });
    }

    const prevent = (e: Event) => e.preventDefault(); // no context menu, no native image drag
    stage.addEventListener("pointerdown", onDown);
    stage.addEventListener("pointermove", onMove);
    stage.addEventListener("pointerup", onUp);
    stage.addEventListener("pointercancel", onUp);
    stage.addEventListener("pointerleave", onLeave);
    stage.addEventListener("wheel", onWheel, { passive: false });
    stage.addEventListener("contextmenu", prevent);
    stage.addEventListener("dragstart", prevent);
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    return () => {
      stage.removeEventListener("pointerdown", onDown);
      stage.removeEventListener("pointermove", onMove);
      stage.removeEventListener("pointerup", onUp);
      stage.removeEventListener("pointercancel", onUp);
      stage.removeEventListener("pointerleave", onLeave);
      stage.removeEventListener("wheel", onWheel);
      stage.removeEventListener("contextmenu", prevent);
      stage.removeEventListener("dragstart", prevent);
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
    };
  }, [stageRef]);
}
