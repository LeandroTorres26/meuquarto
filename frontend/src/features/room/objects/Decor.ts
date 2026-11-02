import type { Box } from "../box";
import { PALETTE } from "../palette";
import { RoomObject, type RoomView } from "./RoomObject";

/**
 * The room itself: floor, two walls, the cut top, the window, the rug, the pouf, the floor lamp
 * and the plants. Nobody can point at it (no label, no glow): isSelectable says no. It follows the
 * lamp ("Abajur"): the window turns to night and the lamp shade and the light pool go out.
 */
export class Decor extends RoomObject {
  private constructor() {
    super("decor", "", "", "", []);
  }

  static room(): Decor {
    return new Decor();
  }

  override isSelectable(): boolean {
    return false;
  }

  override boxes(view: RoomView): Box[] {
    const lamp = view.lamp;
    const { navy, dark } = PALETTE;
    const boxes: Box[] = [
      // floor with boards, walls, the navy cut along the top, baseboards
      this.solid(-212, 200, -16, 0, -212, 200, PALETTE.floor, {
        textures: {
          top: {
            background:
              "repeating-linear-gradient(90deg,hsl(18 83% 65%) 0 38px,hsl(17 64% 57%) 38px 40px)",
          },
        },
      }),
      this.solid(-200, 200, 0, 260, -212, -200, PALETTE.wall),
      this.solid(-212, -200, 0, 260, -212, 200, PALETTE.wall),
      this.solid(-224, 206, 260, 286, -226, -194, navy),
      this.solid(-226, -194, 260, 286, -226, 206, navy),
      this.solid(-200, 200, 0, 10, -200, -197, PALETTE.baseboard, { only: ["front", "top"] }),
      this.solid(-200, -197, 0, 10, -197, 200, PALETTE.baseboard, { only: ["right", "top"] }),
      // window: frame, glass (day or night) and sill
      this.solid(40, 150, 100, 210, -200, -195, PALETTE.red),
      this.solid(50, 140, 110, 200, -195, -194, PALETTE.yellow, {
        only: ["front"],
        flat: true,
        textures: {
          front: {
            background: lamp
              ? "repeating-linear-gradient(180deg,hsl(42 100% 70%) 0 8px,hsl(36 87% 62%) 8px 10px)"
              : "radial-gradient(circle at 70% 30%,hsl(40 91% 91%) 0 6px,transparent 7px),linear-gradient(hsl(225 40% 18%),hsl(213 30% 32%))",
          },
        },
      }),
      this.solid(34, 156, 96, 102, -200, -186, PALETTE.red),
      // rug and pouf
      this.solid(-10, 130, 0, 1, -40, 100, PALETTE.sky, {
        only: ["top"],
        flat: true,
        textures: {
          top: {
            background:
              "repeating-radial-gradient(circle,hsl(198 78% 64%) 0 14px,hsl(42 32% 92%) 14px 22px)",
            borderRadius: "50%",
          },
        },
      }),
      this.solid(40, 80, 44, 52, 10, 50, PALETTE.green, {
        textures: { top: { borderRadius: "50%" } },
      }),
      ...[
        [44, 14],
        [72, 14],
        [44, 42],
        [72, 42],
      ].map(([x, z]) => this.solid(x, x + 4, 0, 44, z, z + 4, dark)),
    ];
    if (lamp) {
      boxes.push(
        this.solid(-150, 110, 1, 2, -200, 80, "transparent", {
          only: ["top"],
          flat: true,
          textures: {
            top: {
              background:
                "radial-gradient(ellipse at 50% 40%,hsl(40 100% 68% / .5),hsl(40 100% 68% / 0) 65%)",
            },
          },
        }),
      );
    }
    boxes.push(
      // floor lamp: base, pole, shade
      this.solid(-44, -12, 0, 4, -134, -102, dark),
      this.solid(-30, -26, 4, 196, -120, -116, dark),
      this.solid(-52, -4, 188, 216, -142, -94, lamp ? "hsl(43 100% 83%)" : "hsl(41 22% 68%)", {
        flat: lamp,
      }),
      // plants in the corner
      this.solid(150, 180, 0, 26, 120, 150, "hsl(17 56% 51%)"),
      this.solid(144, 186, 26, 76, 114, 156, "hsl(153 39% 41%)"),
      this.solid(152, 178, 76, 96, 122, 148, "hsl(152 41% 52%)"),
    );
    return boxes;
  }
}
