import type { Box, Point3 } from "../box";
import { BOOK_COLORS, MANGA_COLORS, PALETTE } from "../palette";
import { RoomObject, pickBy, type RoomItem } from "./RoomObject";

/** [width, height] of the spines on the first board; the 7 slots fill the board from the left. */
const BOOK_SIZES = [
  [9, 40],
  [8, 36],
  [10, 42],
  [7, 34],
  [9, 38],
  [8, 40],
  [9, 36],
] as const;
/** Manga volumes are thinner and shorter; 8 fit on the second board. */
const MANGA_SLOTS = 8;
const LEFT = -182;

/**
 * The bookshelf: books on the first board, manga on the second, and a
 * few fixed things (books and a plant) on the top board. The scene draws up to 7 books and 8
 * manga; the spine colors come from the work, so the same book keeps its color.
 */
export class Shelf extends RoomObject {
  private readonly books: readonly RoomItem[];
  private readonly mangas: readonly RoomItem[];

  private constructor(items: readonly RoomItem[]) {
    super("estante", "Estante", "Estante", "Livros e mangás", items);
    this.books = items.filter((item) => item.category === "BOOK");
    this.mangas = items.filter((item) => item.category === "MANGA");
  }

  static of(items: readonly RoomItem[]): Shelf {
    return new Shelf(items);
  }

  override boxes(): Box[] {
    const wood = PALETTE.wood;
    const boxes: Box[] = [
      // the frame: back panel, two sides, three boards and the top
      this.solid(-190, -110, 0, 170, -198, -194, PALETTE.woodDark),
      this.solid(-190, -184, 0, 170, -194, -160, wood),
      this.solid(-116, -110, 0, 170, -194, -160, wood),
      ...[0, 56, 112].map((y) => this.solid(-184, -116, y, y + 6, -194, -160, wood)),
      this.solid(-192, -108, 164, 170, -198, -158, wood),
    ];

    let x = LEFT;
    this.books.slice(0, BOOK_SIZES.length).forEach((book, index) => {
      const [width, height] = BOOK_SIZES[index];
      boxes.push(
        this.solid(x, x + width, 6, 6 + height, -190, -166, pickBy(book.ref, BOOK_COLORS)),
      );
      x += width + 1;
    });

    x = LEFT;
    this.mangas.slice(0, MANGA_SLOTS).forEach((volume) => {
      boxes.push(this.solid(x, x + 7, 62, 92, -188, -168, pickBy(volume.ref, MANGA_COLORS)));
      x += 8;
    });

    // top board: fixed books and a plant, always there
    x = LEFT;
    for (const [width, height, color] of [
      [10, 40, "hsl(215 50% 23%)"],
      [9, 44, "hsl(355 78% 56%)"],
      [8, 38, "hsl(40 91% 91%)"],
    ] as const) {
      boxes.push(this.solid(x, x + width, 118, 118 + height, -190, -166, color));
      x += width + 1;
    }
    boxes.push(this.solid(-150, -120, 118, 124, -190, -166, PALETTE.green));
    boxes.push(this.solid(-148, -122, 124, 130, -188, -168, PALETTE.orange));
    return boxes;
  }

  override anchor(): Point3 {
    return { x: -150, y: 186, z: -170 };
  }
}
