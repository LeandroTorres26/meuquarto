// Camera math of the room (S5): limits, soft tilt, projection, fit, pan limits, easing, framing.
// Pure functions over plain numbers (no React, no DOM), unit tested. The numbers come from the
// S5 section of docs/design/telas/README.md and the prototype quarto.html.

import type { Bounds3, Point3 } from "./box";

export type Range = readonly [number, number];

export const LIMITS = {
  /** Degrees. Rotation: horizontal drag, buttons turn 15. */
  yaw: [-88, -2] as Range,
  /** Degrees. Only a mouse tilts; the limits are soft (see softPitch). */
  pitch: [22, 34] as Range,
  /** Times the fitted size. */
  zoom: [0.6, 3.5] as Range,
};
export const YAW_HOME = -45;
export const PITCH_HOME = 28;
const PITCH_RANGE = 6;
export const YAW_STEP = 15;
export const ZOOM_STEP = 1.25;
/** Degrees of rotation per pixel of horizontal drag, and of tilt per pixel of vertical drag. */
export const YAW_PER_PX = 0.35;
export const PITCH_PER_PX = 0.12;
const EASE = 0.18;
const PAN_SLACK = 24;
/** Where the prototype's world origin sits vertically (the floor level), in px. */
const WORLD_LIFT = 130;

export type Camera = {
  yaw: number;
  pitch: number;
  zoom: number;
  /** Pan in px. */
  px: number;
  py: number;
  /** Fitted scale and center of the free area; they ease like the rest when the panel changes size. */
  fs: number;
  cx: number;
  cy: number;
};

export const HOME_CAMERA: Camera = {
  yaw: YAW_HOME,
  pitch: PITCH_HOME,
  zoom: 1,
  px: 0,
  py: 0,
  fs: 0.5,
  cx: 0,
  cy: 0,
};

export const clamp = (value: number, range: Range): number =>
  Math.max(range[0], Math.min(range[1], value));

/** The tilt eases into 22 to 34 degrees instead of stopping dead: 28 + 6 * tanh((raw - 28) / 6). */
export function softPitch(raw: number): number {
  return PITCH_HOME + PITCH_RANGE * Math.tanh((raw - PITCH_HOME) / PITCH_RANGE);
}

/** Inverse of softPitch: the raw value that yields this pitch, so the next drag starts where the tilt eased to. */
export function rawPitchFor(pitch: number): number {
  const t = Math.max(-0.999, Math.min(0.999, (pitch - PITCH_HOME) / PITCH_RANGE));
  return PITCH_HOME + PITCH_RANGE * Math.atanh(t);
}

type View = Pick<Camera, "yaw" | "pitch">;
export type Point2 = { x: number; y: number };

/** Projects a room point to the screen plane at scale `s` (the labels follow the 3D objects with this). */
export function project(at: Point3, s: number, view: View): Point2 {
  const ya = (view.yaw * Math.PI) / 180;
  const xa = (-view.pitch * Math.PI) / 180;
  const y = -at.y + WORLD_LIFT;
  const x1 = at.x * Math.cos(ya) + at.z * Math.sin(ya);
  const z1 = -at.x * Math.sin(ya) + at.z * Math.cos(ya);
  return { x: x1 * s, y: (y * Math.cos(xa) - z1 * Math.sin(xa)) * s };
}

export type Bounds2 = { x0: number; x1: number; y0: number; y1: number };

/** Screen bounds (at scale 1) of a 3D box seen from `view`. */
export function projectBounds(b: Bounds3, view: View): Bounds2 {
  const out: Bounds2 = { x0: Infinity, x1: -Infinity, y0: Infinity, y1: -Infinity };
  for (const x of [b.x0, b.x1])
    for (const y of [b.y0, b.y1])
      for (const z of [b.z0, b.z1]) {
        const p = project({ x, y, z }, 1, view);
        out.x0 = Math.min(out.x0, p.x);
        out.x1 = Math.max(out.x1, p.x);
        out.y0 = Math.min(out.y0, p.y);
        out.y1 = Math.max(out.y1, p.y);
      }
  return out;
}

/** The outer corners of the room shell (walls and floor), in room units. */
export const ROOM_EXTENTS: Bounds3 = { x0: -226, x1: 206, y0: -16, y1: 286, z0: -226, z1: 206 };

export type RoomBox = Bounds2 & { cx: number; cy: number; w: number; h: number };

/**
 * The room's projected bounds at scale 1 over every yaw and pitch the camera allows, so the fit never
 * "breathes" while rotating (fitToArea depends on this and not on the current yaw).
 */
export function roomBox(): RoomBox {
  const b: Bounds2 = { x0: Infinity, x1: -Infinity, y0: Infinity, y1: -Infinity };
  for (let yaw = LIMITS.yaw[0]; yaw <= LIMITS.yaw[1]; yaw += 2)
    for (const pitch of [LIMITS.pitch[0], PITCH_HOME, LIMITS.pitch[1]]) {
      const p = projectBounds(ROOM_EXTENTS, { yaw, pitch });
      b.x0 = Math.min(b.x0, p.x0);
      b.x1 = Math.max(b.x1, p.x1);
      b.y0 = Math.min(b.y0, p.y0);
      b.y1 = Math.max(b.y1, p.y1);
    }
  return { ...b, cx: (b.x0 + b.x1) / 2, cy: (b.y0 + b.y1) / 2, w: b.x1 - b.x0, h: b.y1 - b.y0 };
}
export const ROOM_BOX: RoomBox = roomBox();

export type Area = { x0: number; x1: number; y0: number; y1: number };
export type Fit = { fs: number; cx: number; cy: number; w: number; h: number };

/** Fits the room's projected bounds into a free area of the screen (at least 120px each way). */
export function fitToArea(area: Area): Fit {
  const w = Math.max(120, area.x1 - area.x0);
  const h = Math.max(120, area.y1 - area.y0);
  return {
    fs: Math.min(w / ROOM_BOX.w, h / ROOM_BOX.h),
    cx: (area.x0 + area.x1) / 2,
    cy: area.y0 + h / 2,
    w,
    h,
  };
}

/** Pan limit: a room smaller than the free area stays inside it, a larger one always covers it (24px of slack). */
export function clampPan(
  pan: Pick<Camera, "px" | "py">,
  fs: number,
  zoom: number,
  free: { w: number; h: number },
): Pick<Camera, "px" | "py"> {
  const s = fs * zoom;
  const mx = Math.abs(free.w - s * ROOM_BOX.w) / 2 + PAN_SLACK;
  const my = Math.abs(free.h - s * ROOM_BOX.h) / 2 + PAN_SLACK;
  return { px: clamp(pan.px, [-mx, mx]), py: clamp(pan.py, [-my, my]) };
}

/** A requested camera target made legal: zoom and yaw inside their limits, pan inside the free area. */
export function legalTarget(target: Camera, free: { w: number; h: number }): Camera {
  const zoom = clamp(target.zoom, LIMITS.zoom);
  const yaw = clamp(target.yaw, LIMITS.yaw);
  return { ...target, zoom, yaw, ...clampPan(target, target.fs, zoom, free) };
}

/** Wheel and trackpad pinch (ctrlKey) zoom: exp(-deltaY * 0.0015), or 0.01 for a pinch. */
export function wheelZoom(zoom: number, deltaY: number, ctrlKey: boolean): number {
  return zoom * Math.exp(-deltaY * (ctrlKey ? 0.01 : 0.0015));
}

/**
 * One animation frame toward the target (0.18 of the gap), snapping when close. With reduced motion
 * the camera jumps to the target at once.
 */
export function stepCamera(
  cam: Camera,
  target: Camera,
  reducedMotion: boolean,
): { camera: Camera; moving: boolean } {
  if (reducedMotion) return { camera: { ...target }, moving: false };
  const next = { ...cam };
  let moving = false;
  for (const key of Object.keys(cam) as Array<keyof Camera>) {
    const gap = target[key] - cam[key];
    const epsilon = key === "zoom" || key === "fs" ? 0.0005 : 0.05;
    if (Math.abs(gap) > epsilon) {
      next[key] = cam[key] + gap * EASE;
      moving = true;
    } else {
      next[key] = target[key];
    }
  }
  return { camera: next, moving };
}

/** CSS transform of the world for a camera (scale, tilt, rotation, floor lift). */
export function worldTransform(cam: Camera): string {
  const s = cam.fs * cam.zoom;
  return `scale(${s.toFixed(4)}) rotateX(${(-cam.pitch).toFixed(2)}deg) rotateY(${cam.yaw.toFixed(2)}deg) translateY(${WORLD_LIFT}px)`;
}

/** Left and top of the anchor so that zoom keeps the room's center on the center of the free area. */
export function anchorOffset(cam: Camera): { left: number; top: number } {
  const s = cam.fs * cam.zoom;
  return { left: cam.cx - s * ROOM_BOX.cx, top: cam.cy - s * ROOM_BOX.cy };
}

/** Screen position of a label (relative to the anchor), to be shifted by (-50%, -100%) so it sits above the point. */
export function labelPosition(at: Point3, cam: Camera): Point2 {
  return project(at, cam.fs * cam.zoom, cam);
}

/** Panel layout: beside the room on wide mouse screens and on landscape windows, otherwise a bottom sheet. */
export function isSideLayout(width: number, height: number, touch: boolean): boolean {
  const landscape = width >= 600 && width > height * 1.2;
  return touch ? landscape : width >= 1024 || landscape;
}

/**
 * Touch selection: zoom (1 to 2.4) and pan so the whole object is centered in the visible area
 * between the tools and the top of the sheet (or left of the side panel).
 */
export function frameTarget(
  objectBounds: Bounds3,
  cam: Pick<Camera, "yaw" | "pitch" | "fs" | "cx" | "cy">,
  visible: Area,
): Pick<Camera, "zoom" | "px" | "py"> {
  const b = projectBounds(objectBounds, cam);
  const fitW = ((visible.x1 - visible.x0) * 0.7) / ((b.x1 - b.x0) * cam.fs);
  const fitH = ((visible.y1 - visible.y0) * 0.7) / ((b.y1 - b.y0) * cam.fs);
  const zoom = clamp(Math.min(fitW, fitH), [1, 2.4]);
  const s = cam.fs * zoom;
  const originX = cam.cx - s * ROOM_BOX.cx;
  const originY = cam.cy - s * ROOM_BOX.cy;
  return {
    zoom,
    px: (visible.x0 + visible.x1) / 2 - (originX + (s * (b.x0 + b.x1)) / 2),
    py: (visible.y0 + visible.y1) / 2 - (originY + (s * (b.y0 + b.y1)) / 2),
  };
}
