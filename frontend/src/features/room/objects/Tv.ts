import { safeImageUrl } from "../../../lib/profile-view";
import type { Box, FaceTexture, Point3 } from "../box";
import { PALETTE } from "../palette";
import { RoomObject, type RoomItem } from "./RoomObject";

const SCREEN_OFF = "linear-gradient(hsl(225 40% 18%),hsl(213 30% 32%))";

/**
 * The TV on its bench, with a console and two controllers. The screen shows the cover of the first
 * game (games have a black band on top in the card; here the cover is the screen). With no game the
 * screen is dark.
 */
export class Tv extends RoomObject {
  private constructor(items: readonly RoomItem[]) {
    super("tv", "TV", "TV e console", "Games", items);
  }

  static of(items: readonly RoomItem[]): Tv {
    return new Tv(items);
  }

  override boxes(): Box[] {
    return [
      // bench: top, cabinet with a door line, two legs
      this.solid(30, 190, 64, 72, -198, -138, PALETTE.navy),
      this.solid(130, 186, 0, 64, -194, -144, PALETTE.yellow, {
        textures: {
          front: {
            background: `linear-gradient(${PALETTE.yellow} 0 46%,hsl(40 62% 49%) 46% 50%,${PALETTE.yellow} 50%)`,
          },
        },
      }),
      this.solid(34, 40, 0, 64, -150, -144, PALETTE.dark),
      this.solid(34, 40, 0, 64, -196, -190, PALETTE.dark),
      // the set: the cover of the first game is the screen
      this.solid(118, 180, 72, 120, -188, -148, PALETTE.cream, {
        textures: { front: this.screen() },
      }),
      // antenna
      this.solid(136, 138, 120, 146, -170, -168, PALETTE.steel),
      this.solid(158, 160, 120, 140, -170, -168, PALETTE.steel),
      // console and its button
      this.solid(50, 98, 72, 84, -186, -160, PALETTE.grey, {
        textures: {
          top: {
            background: `linear-gradient(90deg,${PALETTE.grey} 0 60%,${PALETTE.red} 60% 70%,${PALETTE.grey} 70%)`,
          },
        },
      }),
      this.solid(58, 84, 72, 76, -150, -140, "hsl(226 10% 75%)"),
    ];
  }

  private screen(): FaceTexture {
    const border = `7px solid ${PALETTE.cream}`;
    const image = this.items[0] ? safeImageUrl(this.items[0].imageUrl) : null;
    return image
      ? { image, border, borderRadius: "10px" }
      : { background: SCREEN_OFF, border, borderRadius: "10px" };
  }

  override anchor(): Point3 {
    return { x: 150, y: 168, z: -165 };
  }
}
