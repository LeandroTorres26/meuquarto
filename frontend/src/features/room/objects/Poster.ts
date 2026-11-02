import type { Box, Face, Point3 } from "../box";
import { PALETTE } from "../palette";
import { PAPER, RoomObject, type RoomItem } from "./RoomObject";

/** One place on a wall where a cover hangs. */
type Slot = {
  x0: number;
  x1: number;
  y0: number;
  y1: number;
  z0: number;
  z1: number;
  /** Frame color (posters have a colored border, the mural pieces sit on paper). */
  frame: string;
};

/** A board behind the slots (the mural has one). */
type Backing = Omit<Slot, "frame"> & { color: string; border: string };

/** Where a Poster hangs: its slots, the faces drawn, and the face of each slot that shows the cover. */
type Placement = {
  slots: readonly Slot[];
  coverFace: Face;
  faces: readonly Face[];
  /** CSS border of the cover, from the slot's frame color ("" for none). */
  border: (frame: string) => string;
  backing?: Backing;
  label: Point3;
};

const WALL_POSTERS: Placement = {
  slots: [
    { x0: -200, x1: -196, y0: 150, y1: 222, z0: -170, z1: -122, frame: PALETTE.teal },
    { x0: -200, x1: -196, y0: 160, y1: 232, z0: -96, z1: -48, frame: PALETTE.red },
    { x0: -200, x1: -196, y0: 150, y1: 222, z0: 22, z1: 70, frame: PALETTE.teal },
    { x0: -200, x1: -196, y0: 160, y1: 232, z0: 98, z1: 146, frame: PALETTE.navy },
  ],
  coverFace: "right", // the left wall looks toward +x, which is the "right" face of a box
  faces: ["right", "top", "front"],
  border: (frame) => `3px solid ${frame}`,
  label: { x: -168, y: 250, z: -20 },
};

const MURAL: Placement = {
  slots: [
    { x0: -82, x1: -54, y0: 150, y1: 190, z0: -195, z1: -193, frame: PAPER },
    { x0: -48, x1: -20, y0: 144, y1: 184, z0: -195, z1: -193, frame: PAPER },
    { x0: -14, x1: 4, y0: 156, y1: 196, z0: -195, z1: -193, frame: PAPER },
  ],
  coverFace: "front",
  faces: ["front"],
  border: () => "",
  backing: {
    x0: -92,
    x1: 12,
    y0: 130,
    y1: 212,
    z0: -200,
    z1: -195,
    color: PALETTE.board,
    border: `4px solid ${PALETTE.boardFrame}`,
  },
  label: { x: -40, y: 230, z: -190 },
};

/**
 * Covers hanging on a wall. One class, two placements (the poster wall on the left wall and the
 * mural on the back wall): the difference is data, not a subclass. Each slot shows the next item;
 * a slot without an item shows an empty frame. Films, series and anime are flat (no depth).
 */
export class Poster extends RoomObject {
  private constructor(
    id: string,
    label: string,
    title: string,
    subtitle: string,
    items: readonly RoomItem[],
    private readonly placement: Placement,
  ) {
    super(id, label, title, subtitle, items);
  }

  /** Films and series, on the left wall. */
  static wall(items: readonly RoomItem[]): Poster {
    return new Poster(
      "parede",
      "Pôsteres",
      "Pôsteres na parede",
      "Filmes e séries",
      items,
      WALL_POSTERS,
    );
  }

  /** Animes, on a board on the back wall. */
  static mural(items: readonly RoomItem[]): Poster {
    return new Poster("mural", "Mural", "Mural", "Animes", items, MURAL);
  }

  override boxes(): Box[] {
    const { slots, coverFace, faces, border, backing } = this.placement;
    const boxes: Box[] = [];
    if (backing) {
      const { x0, x1, y0, y1, z0, z1, color } = backing;
      boxes.push(
        this.solid(x0, x1, y0, y1, z0, z1, color, {
          textures: { front: { border: backing.border } },
        }),
      );
    }
    slots.forEach((slot, index) => {
      boxes.push(
        this.solid(slot.x0, slot.x1, slot.y0, slot.y1, slot.z0, slot.z1, slot.frame, {
          only: faces,
          textures: { [coverFace]: this.cover(this.items[index], border(slot.frame)) },
        }),
      );
    });
    return boxes;
  }

  override anchor(): Point3 {
    return this.placement.label;
  }
}
