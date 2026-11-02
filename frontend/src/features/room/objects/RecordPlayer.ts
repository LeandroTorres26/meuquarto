import type { Box, Point3 } from "../box";
import { PALETTE } from "../palette";
import { RoomObject, type RoomItem, type RoomView, type SceneActions } from "./RoomObject";

/** [top of the sleeve, z] of the three sleeves leaning in the crate, back to front. */
const CRATE = [
  [76, 140],
  [70, 152],
  [62, 164],
] as const;
/** How far the front sleeve rises when the player is selected (room units). */
const LIFT = 14;

const SPEAKER = [
  "radial-gradient(circle at 50% 28%,hsl(240 7% 86%) 0 8px,hsl(235 21% 21%) 8px 12px,transparent 12px)",
  "radial-gradient(circle at 50% 66%,hsl(240 7% 86%) 0 11px,hsl(235 21% 21%) 11px 15px,transparent 15px)",
  PALETTE.speaker,
].join(",");

/**
 * Table with a turntable, two speakers and a crate of sleeves. The first album lies on the table,
 * the next three lean in the crate (sleeves are square and almost flat, depth 0.02). It overrides
 * onSelect: besides being selected, the front sleeve is lifted out of the crate (the other objects
 * only get selected), which is the polymorphism the scene relies on without knowing it.
 */
export class RecordPlayer extends RoomObject {
  private constructor(items: readonly RoomItem[]) {
    super("vitrola", "Vitrola", "Vitrola e discos", "Música", items);
  }

  static of(items: readonly RoomItem[]): RecordPlayer {
    return new RecordPlayer(items);
  }

  override onSelect(actions: SceneActions): void {
    super.onSelect(actions);
    actions.reveal(this.id);
  }

  override boxes(view: RoomView): Box[] {
    const lifted = view.revealed === this.id;
    const boxes: Box[] = [
      // table and legs
      this.solid(-198, -110, 74, 80, -62, 92, "hsl(34 61% 77%)"),
      ...[
        [-196, -58],
        [-116, -58],
        [-196, 86],
        [-116, 86],
      ].map(([x, z]) => this.solid(x, x + 4, 0, 74, z, z + 4, PALETTE.dark)),
      // turntable: plinth, record (a round top), tonearm
      this.solid(-182, -124, 80, 92, -44, 20, PALETTE.woodDark),
      this.solid(-174, -132, 92, 94, -36, 6, PALETTE.vinyl, {
        only: ["top"],
        flat: true,
        textures: {
          top: {
            background:
              "radial-gradient(circle,var(--brand) 0 14%,hsl(240 13% 5%) 15% 18%,transparent 18%),repeating-radial-gradient(circle,hsl(240 9% 10%) 0 2px,hsl(240 13% 5%) 2px 4px)",
            borderRadius: "50%",
          },
        },
      }),
      this.solid(-130, -127, 94, 98, -30, 4, PALETTE.grey),
      // the first album, lying on the table
      this.solid(-190, -150, 80, 84, 40, 84, PALETTE.vinyl, {
        textures: { top: this.cover(this.items[0]) },
      }),
      // two speakers
      this.solid(-194, -160, 0, 112, -112, -82, PALETTE.speaker, {
        textures: { right: { background: SPEAKER } },
      }),
      this.solid(-194, -160, 0, 112, 104, 134, PALETTE.speaker, {
        textures: { right: { background: SPEAKER } },
      }),
      // the crate
      this.solid(-150, -80, 0, 34, 132, 182, PALETTE.wood),
    ];
    // the albums after the first lean in the crate; the one in front is the one that lifts
    const inCrate = Math.min(CRATE.length, Math.max(0, this.items.length - 1));
    CRATE.slice(0, inCrate).forEach(([top, z], slot) => {
      const album = this.items[slot + 1];
      const lift = lifted && slot === inCrate - 1 ? LIFT : 0;
      boxes.push(
        this.solid(-146, -84, 20 + lift, top + lift, z, z + 3, PALETTE.cream, {
          only: ["front", "top"],
          textures: { front: this.cover(album) },
        }),
      );
    });
    return boxes;
  }

  override anchor(): Point3 {
    return { x: -150, y: 128, z: 0 };
  }
}
