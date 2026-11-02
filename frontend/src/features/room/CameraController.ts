import type { Point3 } from "./box";
import {
  HOME_CAMERA,
  PITCH_HOME,
  anchorOffset,
  labelPosition,
  legalTarget,
  stepCamera,
  worldTransform,
  type Camera,
  type Fit,
} from "./camera";

/** A label to keep over its object: the id of the object and the 3D point it floats above. */
export type PinSpec = { id: string; at: Point3 };

const reducedMotion = () =>
  typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/**
 * Holds the camera outside React state: a drag or a wheel tick changes a few CSS values directly,
 * so a frame never re-renders the scene. React only decides where the camera should go (moveTo,
 * setFit); the easing and the math live in camera.ts. The only DOM it touches are the elements
 * registered by the scene (the world, the two anchors and the label wrappers).
 */
export class CameraController {
  private shown: Camera = { ...HOME_CAMERA };
  private goal: Camera = { ...HOME_CAMERA };
  private freeArea = { w: 600, h: 400 };
  private raf = 0;
  /** The un-eased tilt of a mouse drag; softPitch maps it to the real pitch. */
  private raw = PITCH_HOME;
  private world: HTMLElement | null = null;
  private readonly anchors = new Set<HTMLElement>();
  private readonly pinEls = new Map<string, HTMLElement>();
  private pins: readonly PinSpec[] = [];

  camera(): Camera {
    return this.shown;
  }
  target(): Camera {
    return this.goal;
  }
  free(): { w: number; h: number } {
    return this.freeArea;
  }
  rawPitch(): number {
    return this.raw;
  }
  setRawPitch(value: number) {
    this.raw = value;
  }

  setPins(pins: readonly PinSpec[]) {
    this.pins = pins;
    this.apply();
  }

  // ----- the elements it moves -----
  registerWorld = (el: HTMLElement | null) => {
    this.world = el;
    this.apply();
  };
  /** Ref callback for an anchor element; returns its cleanup. */
  registerAnchor = (el: HTMLElement | null) => {
    if (!el) return;
    this.anchors.add(el);
    this.apply();
    return () => {
      this.anchors.delete(el);
    };
  };
  registerPin = (id: string, el: HTMLElement | null) => {
    if (el) {
      this.pinEls.set(id, el);
      this.apply();
    } else {
      this.pinEls.delete(id);
    }
  };

  // ----- moving -----
  /** Sets a new target (made legal) and eases there; with reduced motion the camera jumps. */
  moveTo = (to: Partial<Camera>) => {
    this.goal = legalTarget({ ...this.goal, ...to }, this.freeArea);
    if (to.pitch !== undefined) this.raw = to.pitch;
    if (reducedMotion()) {
      this.shown = { ...this.goal };
      this.apply();
    } else if (!this.raf) {
      this.raf = requestAnimationFrame(this.frame);
    }
  };

  /** The free area changed (a window resize, a panel opening): refit, easing unless `jump`. */
  setFit(fit: Fit, jump: boolean) {
    this.freeArea = { w: fit.w, h: fit.h };
    const next = { fs: fit.fs, cx: fit.cx, cy: fit.cy };
    if (jump) {
      this.goal = legalTarget({ ...this.goal, ...next }, this.freeArea);
      this.shown = { ...this.shown, ...next, px: this.goal.px, py: this.goal.py };
      this.apply();
    } else {
      this.moveTo(next);
    }
  }

  /** Direct set, no easing (a finger or the mouse is dragging). */
  setNow(to: Partial<Camera>) {
    this.shown = { ...this.shown, ...to };
    this.goal = { ...this.goal, ...to };
    this.apply();
  }

  dispose() {
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
  }

  private frame = () => {
    this.raf = 0;
    const { camera, moving } = stepCamera(this.shown, this.goal, reducedMotion());
    this.shown = camera;
    this.apply();
    if (moving) this.raf = requestAnimationFrame(this.frame);
  };

  private apply() {
    const c = this.shown;
    const { left, top } = anchorOffset(c);
    for (const el of this.anchors) {
      el.style.left = `${left.toFixed(1)}px`;
      el.style.top = `${top.toFixed(1)}px`;
      el.style.translate = `${c.px.toFixed(1)}px ${c.py.toFixed(1)}px`;
    }
    if (this.world) this.world.style.transform = worldTransform(c);
    for (const pin of this.pins) {
      const el = this.pinEls.get(pin.id);
      if (!el) continue;
      const p = labelPosition(pin.at, c);
      el.style.transform = `translate(${p.x.toFixed(1)}px,${p.y.toFixed(1)}px) translate(-50%,-100%)`;
    }
  }
}
