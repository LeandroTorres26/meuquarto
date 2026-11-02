// The solid the room is made of: a box in room units, drawn as up to five CSS faces. Pure data and
// pure functions (no React, no DOM), so the geometry is unit tested. Axes as in the prototype
// (docs/design/telas/quarto.html): x to the right, y up, z toward the viewer; 1 unit = 1 CSS px.

export type Face = "front" | "back" | "right" | "left" | "top";

/** What a face shows besides its color: an image (a cover), a gradient, a frame, rounded corners. */
export type FaceTexture = {
  background?: string;
  /** Only an https cover from the API (safeImageUrl), never raw provider markup. */
  image?: string;
  border?: string;
  borderRadius?: string;
};

export type Box = {
  x0: number;
  x1: number;
  y0: number;
  y1: number;
  z0: number;
  z1: number;
  color: string;
  /** Id of the selectable object this box belongs to ("" for decoration). Becomes `data-g`. */
  group: string;
  /** Draw only these faces (a flat frame on a wall needs no back). */
  only?: readonly Face[];
  /** Flat faces ignore the per-face shading (screens, rugs, light pools). */
  flat?: boolean;
  textures?: Partial<Record<Face, FaceTexture>>;
};

export type Bounds3 = { x0: number; x1: number; y0: number; y1: number; z0: number; z1: number };
export type Point3 = { x: number; y: number; z: number };

/** Shading of each face when the lamp is on (the top is the lit one). */
export const FACE_SHADE: Record<Face, number> = {
  front: 0.06,
  back: 0.3,
  right: 0.18,
  left: 0.12,
  top: 0,
};
/** Extra darkness over every face when the lamp ("Abajur") is off. */
export const LAMP_OFF_DIM = 0.3;
const MAX_SHADE = 0.85;

export const FACES: readonly Face[] = ["front", "back", "right", "left", "top"];

export type FaceGeometry = {
  face: Face;
  width: number;
  height: number;
  left: number;
  top: number;
  transform: string;
  /** Alpha of the dark overlay (0 to 0.85). */
  shade: number;
};

/** Where the box sits, relative to the world origin (the prototype's `translate3d`). */
export function boxTransform(box: Box): string {
  const cx = (box.x0 + box.x1) / 2;
  const cy = (box.y0 + box.y1) / 2;
  const cz = (box.z0 + box.z1) / 2;
  return `translate3d(${cx}px,${-cy}px,${cz}px)`;
}

/** The faces to draw for a box, each placed around the box center. */
export function faceGeometry(box: Box, lampOn: boolean): FaceGeometry[] {
  const w = box.x1 - box.x0;
  const h = box.y1 - box.y0;
  const d = box.z1 - box.z0;
  const dim = lampOn ? 0 : LAMP_OFF_DIM;
  const defs: Array<[Face, number, number, string]> = [
    ["front", w, h, `translateZ(${d / 2}px)`],
    ["back", w, h, `rotateY(180deg) translateZ(${d / 2}px)`],
    ["right", d, h, `rotateY(90deg) translateZ(${w / 2}px)`],
    ["left", d, h, `rotateY(-90deg) translateZ(${w / 2}px)`],
    ["top", w, d, `rotateX(90deg) translateZ(${h / 2}px)`],
  ];
  return defs
    .filter(([face]) => !box.only || box.only.includes(face))
    .map(([face, width, height, transform]) => ({
      face,
      width,
      height,
      left: -width / 2,
      top: -height / 2,
      transform,
      shade: Math.min(MAX_SHADE, (box.flat ? 0 : FACE_SHADE[face]) + dim),
    }));
}

export function boundsOf(boxes: readonly Box[]): Bounds3 {
  const b: Bounds3 = {
    x0: Infinity,
    x1: -Infinity,
    y0: Infinity,
    y1: -Infinity,
    z0: Infinity,
    z1: -Infinity,
  };
  for (const box of boxes) {
    b.x0 = Math.min(b.x0, box.x0);
    b.x1 = Math.max(b.x1, box.x1);
    b.y0 = Math.min(b.y0, box.y0);
    b.y1 = Math.max(b.y1, box.y1);
    b.z0 = Math.min(b.z0, box.z0);
    b.z1 = Math.max(b.z1, box.z1);
  }
  return b;
}

/** Number of CSS faces (divs) a set of boxes renders to. */
export function faceCount(boxes: readonly Box[]): number {
  return boxes.reduce((sum, box) => sum + (box.only ? box.only.length : FACES.length), 0);
}
