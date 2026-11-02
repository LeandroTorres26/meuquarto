import { describe, expect, it } from "vitest";
import {
  HOME_CAMERA,
  LIMITS,
  PITCH_HOME,
  ROOM_BOX,
  clampPan,
  fitToArea,
  frameTarget,
  isSideLayout,
  legalTarget,
  project,
  projectBounds,
  rawPitchFor,
  softPitch,
  stepCamera,
  wheelZoom,
  type Camera,
} from "./camera";

const free = { w: 600, h: 400 };
const fitted: Camera = { ...HOME_CAMERA, fs: 0.5, cx: 300, cy: 200 };

describe("softPitch", () => {
  it("stays within 22 to 34 degrees however far the drag goes", () => {
    for (const raw of [-500, -40, 0, 22, 28, 34, 60, 5000]) {
      const pitch = softPitch(raw);
      expect(pitch).toBeGreaterThanOrEqual(LIMITS.pitch[0] - 1e-9);
      expect(pitch).toBeLessThanOrEqual(LIMITS.pitch[1] + 1e-9);
    }
  });

  it("starts at 28 and eases into the limits instead of stopping dead", () => {
    expect(softPitch(PITCH_HOME)).toBe(PITCH_HOME);
    expect(softPitch(PITCH_HOME + 6) - PITCH_HOME).toBeLessThan(6); // slower than the finger
    expect(softPitch(PITCH_HOME + 100)).toBeGreaterThan(softPitch(PITCH_HOME + 12)); // still moving, just slower
  });

  it("is undone by rawPitchFor, so the next drag starts where the tilt eased to", () => {
    for (const pitch of [22.5, 25, 28, 31, 33.5])
      expect(softPitch(rawPitchFor(pitch))).toBeCloseTo(pitch, 6);
  });
});

describe("fitToArea", () => {
  it("does not depend on the yaw: the room box covers every rotation", () => {
    const area = { x0: 8, x1: 808, y0: 60, y1: 560 };
    const a = fitToArea(area);
    // The fit is a function of the area alone; the camera yaw is not even an input.
    expect(fitToArea(area)).toEqual(a);
    for (const yaw of [-88, -46, -2]) {
      for (const pitch of [22, 28, 34]) {
        const b = projectBounds(
          { x0: -226, x1: 206, y0: -16, y1: 286, z0: -226, z1: 206 },
          { yaw, pitch },
        );
        expect(b.x1 - b.x0).toBeLessThanOrEqual(ROOM_BOX.w + 1e-6);
        expect(b.y1 - b.y0).toBeLessThanOrEqual(ROOM_BOX.h + 1e-6);
      }
    }
  });

  it("fits inside the free area and centers on it", () => {
    const area = { x0: 8, x1: 808, y0: 60, y1: 560 };
    const fit = fitToArea(area);
    expect(fit.w * 1).toBe(800);
    expect(fit.fs * ROOM_BOX.w).toBeLessThanOrEqual(800 + 1e-6);
    expect(fit.fs * ROOM_BOX.h).toBeLessThanOrEqual(500 + 1e-6);
    expect(fit.cx).toBe(408);
    expect(fit.cy).toBe(310);
  });

  it("never gives a free area under 120px", () => {
    const fit = fitToArea({ x0: 0, x1: 10, y0: 0, y1: 10 });
    expect(fit.w).toBe(120);
    expect(fit.h).toBe(120);
  });
});

describe("zoom and rotation limits", () => {
  it("clamps zoom to 0.6x to 3.5x of the fitted size", () => {
    expect(legalTarget({ ...fitted, zoom: 0.01 }, free).zoom).toBe(0.6);
    expect(legalTarget({ ...fitted, zoom: 99 }, free).zoom).toBe(3.5);
    expect(legalTarget({ ...fitted, zoom: 1.7 }, free).zoom).toBe(1.7);
  });

  it("clamps yaw to -88 to -2 degrees", () => {
    expect(legalTarget({ ...fitted, yaw: -200 }, free).yaw).toBe(-88);
    expect(legalTarget({ ...fitted, yaw: 40 }, free).yaw).toBe(-2);
  });

  it("zooms by the wheel with exp(-deltaY * 0.0015), and faster for a trackpad pinch", () => {
    expect(wheelZoom(1, 0, false)).toBe(1);
    expect(wheelZoom(1, -100, false)).toBeCloseTo(Math.exp(0.15), 10);
    expect(wheelZoom(1, -10, true)).toBeCloseTo(Math.exp(0.1), 10);
    expect(wheelZoom(2, 100, false)).toBeLessThan(2);
  });
});

describe("clampPan", () => {
  it("keeps a room smaller than the free area inside it, with 24px of slack", () => {
    const s = 0.4;
    const pan = clampPan({ px: 9999, py: -9999 }, s, 1, free);
    expect(pan.px).toBeCloseTo(Math.abs(free.w - s * ROOM_BOX.w) / 2 + 24, 6);
    expect(pan.py).toBeCloseTo(-(Math.abs(free.h - s * ROOM_BOX.h) / 2 + 24), 6);
  });

  it("lets a room larger than the free area move only until it stops covering it", () => {
    const big = clampPan({ px: 9999, py: 9999 }, 2, 3, free);
    expect(big.px).toBeLessThan(9999);
    expect(clampPan({ px: 0, py: 0 }, 2, 3, free)).toEqual({ px: 0, py: 0 });
  });
});

describe("reduced motion", () => {
  it("jumps to the target in one step", () => {
    const target = { ...fitted, yaw: -80, zoom: 2.5, px: 40 };
    const { camera, moving } = stepCamera(fitted, target, true);
    expect(camera).toEqual(target);
    expect(moving).toBe(false);
  });

  it("otherwise eases 0.18 of the gap per frame and then snaps", () => {
    const target = { ...fitted, yaw: -80 };
    const first = stepCamera(fitted, target, false);
    expect(first.moving).toBe(true);
    expect(first.camera.yaw).toBeCloseTo(fitted.yaw + (-80 - fitted.yaw) * 0.18, 10);
    let step = first;
    for (let i = 0; i < 200 && step.moving; i++) step = stepCamera(step.camera, target, false);
    expect(step.moving).toBe(false);
    expect(step.camera.yaw).toBe(-80);
  });
});

describe("project and frameTarget", () => {
  it("projects the world origin straight below the lift, whatever the yaw", () => {
    for (const yaw of [-88, -46, -2]) {
      const p = project({ x: 0, y: 0, z: 0 }, 1, { yaw, pitch: 28 });
      expect(p.x).toBeCloseTo(0, 10);
      expect(p.y).toBeCloseTo(130 * Math.cos((28 * Math.PI) / 180), 10);
    }
  });

  it("frames an object with a zoom between 1 and 2.4 and centers it in the visible area", () => {
    const bounds = { x0: -190, x1: -110, y0: 0, y1: 170, z0: -198, z1: -158 };
    const visible = { x0: 8, x1: 400, y0: 100, y1: 400 };
    const frame = frameTarget(bounds, fitted, visible);
    expect(frame.zoom).toBeGreaterThanOrEqual(1);
    expect(frame.zoom).toBeLessThanOrEqual(2.4);
    // after the pan, the object's projected center sits at the center of the visible area
    const s = fitted.fs * frame.zoom;
    const b = projectBounds(bounds, fitted);
    const centerX = fitted.cx - s * ROOM_BOX.cx + frame.px + (s * (b.x0 + b.x1)) / 2;
    const centerY = fitted.cy - s * ROOM_BOX.cy + frame.py + (s * (b.y0 + b.y1)) / 2;
    expect(centerX).toBeCloseTo(204, 6);
    expect(centerY).toBeCloseTo(250, 6);
  });
});

describe("isSideLayout", () => {
  it("puts the panel beside the room with a mouse from 1024px or in a landscape window", () => {
    expect(isSideLayout(1280, 800, false)).toBe(true);
    expect(isSideLayout(800, 900, false)).toBe(false);
    expect(isSideLayout(800, 500, false)).toBe(true);
  });

  it("on touch only in landscape from 600px (width over 1.2 times the height)", () => {
    expect(isSideLayout(1280, 800, true)).toBe(true); // 1.6 > 1.2
    expect(isSideLayout(390, 844, true)).toBe(false);
    expect(isSideLayout(844, 390, true)).toBe(true);
    expect(isSideLayout(1024, 900, true)).toBe(false);
  });
});
