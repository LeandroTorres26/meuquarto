// ADR 0018 (optional hierarchy) and ADR 0013: the base of everything in the room.
// Plain TypeScript: no React, no Next, no DOM. The scene holds a RoomObject[] and only talks to this
// base type (boxes, anchor, bounds, onSelect); it never asks which subclass an object is.

import { boundsOf, type Bounds3, type Box, type Face, type FaceTexture, type Point3 } from "../box";
import { safeImageUrl, type Category } from "../../../lib/profile-view";

/** A highlight as the room needs it (a subset of the public profile item). */
export type RoomItem = {
  ref: number;
  category: Category;
  title: string;
  year: number | null;
  imageUrl: string | null;
};

/** What the scene lets an object do when it is clicked. */
export type SceneActions = {
  select(id: string): void;
  /** Ask for the "pulled out" pose of an object (it lasts while the object stays selected). */
  reveal(id: string): void;
};

/** The state of the scene that changes how objects are drawn. */
export type RoomView = {
  lamp: boolean;
  /** Id of the object showing its "pulled out" pose, or null. */
  revealed: string | null;
};
export const DEFAULT_VIEW: RoomView = { lamp: true, revealed: null };

type SolidOptions = {
  only?: readonly Face[];
  flat?: boolean;
  textures?: Partial<Record<Face, FaceTexture>>;
};

export abstract class RoomObject {
  protected constructor(
    /** 'parede', 'mural', 'estante', 'vitrola', 'tv' (and 'decor' for the room itself). */
    readonly id: string,
    /** Short name on the label and the chip: 'Pôsteres', 'Mural', ... */
    readonly label: string,
    /** Heading of the panel when selected, and the line under it. */
    readonly title: string,
    readonly subtitle: string,
    /** All the highlights that belong here (the panel lists them all, the scene draws some). */
    readonly items: readonly RoomItem[],
  ) {}

  /** The solids that make this object, in room units. Each subclass places itself. */
  abstract boxes(view: RoomView): Box[];

  /** Where the label floats. Default: the middle of the top of the object. */
  anchor(): Point3 {
    const b = this.bounds();
    return { x: (b.x0 + b.x1) / 2, y: b.y1, z: (b.z0 + b.z1) / 2 };
  }

  /** Whether the visitor can point at it. Decoration says no and gets no label. */
  isSelectable(): boolean {
    return true;
  }

  /** What a click or Tab + Enter does. Default selects the object; subclasses may add to it. */
  onSelect(actions: SceneActions): void {
    actions.select(this.id);
  }

  /** The box around everything this object draws (the camera frames it on touch). */
  bounds(): Bounds3 {
    return boundsOf(this.boxes(DEFAULT_VIEW));
  }

  has(ref: number): boolean {
    return this.items.some((item) => item.ref === ref);
  }

  /** A box that belongs to this object: it carries the id so hover and selection can light it. */
  protected solid(
    x0: number,
    x1: number,
    y0: number,
    y1: number,
    z0: number,
    z1: number,
    color: string,
    options: SolidOptions = {},
  ): Box {
    return {
      x0,
      x1,
      y0,
      y1,
      z0,
      z1,
      color,
      group: this.isSelectable() ? this.id : "",
      ...options,
    };
  }

  /** Face texture of a cover: the https image when there is one, a paper color when not. */
  protected cover(item: RoomItem | undefined, frame?: string): FaceTexture {
    const image = item ? safeImageUrl(item.imageUrl) : null;
    const border = frame;
    return image ? { image, border } : { background: PAPER, border };
  }
}

/** Paper color of an empty frame or cover. */
export const PAPER = "hsl(40 59% 86%)";

/** Deterministic pick from a list: the same work always gets the same color. */
export function pickBy<T>(ref: number, options: readonly T[]): T {
  const mixed = Math.imul(ref | 0, 2654435761) >>> 0;
  return options[mixed % options.length];
}
