import { describe, expect, it } from "vitest";
import { boundsOf, faceCount, faceGeometry, type Box } from "../box";
import { ROOM_EXTENTS } from "../camera";
import { buildRoom, type RoomSection } from "./buildRoom";
import { DEFAULT_VIEW, type RoomItem, type SceneActions } from "./RoomObject";

function item(
  ref: number,
  category: RoomItem["category"],
  imageUrl: string | null = null,
): RoomItem {
  return { ref, category, title: `Obra ${ref}`, year: 2020, imageUrl };
}
function section(category: RoomItem["category"], count: number, firstRef = 1): RoomSection {
  return {
    category,
    items: Array.from({ length: count }, (_, i) =>
      item(firstRef + i, category, `https://img.example/${category}-${firstRef + i}.jpg`),
    ),
  };
}
const FULL: RoomSection[] = [
  section("MOVIE", 4, 1),
  section("SERIES", 4, 10),
  section("ANIME", 3, 20),
  section("BOOK", 7, 30),
  section("MANGA", 8, 40),
  section("MUSIC", 4, 50),
  section("GAME", 3, 60),
];

function recorder() {
  const calls: string[] = [];
  const actions: SceneActions = {
    select: (id) => calls.push(`select:${id}`),
    reveal: (id) => calls.push(`reveal:${id}`),
  };
  return { calls, actions };
}

describe("buildRoom", () => {
  it("returns one selectable object per group, in a fixed order, plus the decoration", () => {
    const room = buildRoom(FULL);
    expect(room.filter((o) => o.isSelectable()).map((o) => o.id)).toEqual([
      "parede",
      "mural",
      "estante",
      "vitrola",
      "tv",
    ]);
    expect(room.filter((o) => !o.isSelectable())).toHaveLength(1);
  });

  it("builds the same objects for an empty profile (an empty frame, an empty shelf)", () => {
    const room = buildRoom([]);
    expect(room.filter((o) => o.isSelectable())).toHaveLength(5);
    for (const object of room) expect(object.boxes(DEFAULT_VIEW).length).toBeGreaterThan(0);
  });

  it("sends films and series to the wall, anime to the mural, books and manga to the shelf", () => {
    const room = buildRoom(FULL);
    const byId = new Map(room.map((o) => [o.id, o]));
    expect(byId.get("parede")?.items).toHaveLength(8);
    expect(byId.get("mural")?.items.map((i) => i.category)).toEqual(["ANIME", "ANIME", "ANIME"]);
    expect(byId.get("estante")?.items).toHaveLength(15);
    expect(byId.get("vitrola")?.items).toHaveLength(4);
    expect(byId.get("tv")?.items).toHaveLength(3);
  });
});

describe("RoomObject geometry", () => {
  it("keeps every box of every object inside the room shell", () => {
    for (const object of buildRoom(FULL).filter((o) => o.isSelectable())) {
      const b = boundsOf(object.boxes(DEFAULT_VIEW));
      expect(b.x0).toBeGreaterThanOrEqual(ROOM_EXTENTS.x0);
      expect(b.x1).toBeLessThanOrEqual(ROOM_EXTENTS.x1);
      expect(b.y0).toBeGreaterThanOrEqual(ROOM_EXTENTS.y0);
      expect(b.y1).toBeLessThanOrEqual(ROOM_EXTENTS.y1);
      expect(b.z0).toBeGreaterThanOrEqual(ROOM_EXTENTS.z0);
      expect(b.z1).toBeLessThanOrEqual(ROOM_EXTENTS.z1);
    }
  });

  it("tags only the selectable objects' boxes with their group, so only they can glow", () => {
    for (const object of buildRoom(FULL)) {
      const expected = object.isSelectable() ? object.id : "";
      expect(object.boxes(DEFAULT_VIEW).every((box) => box.group === expected)).toBe(true);
    }
  });

  it("puts the label above the object it names", () => {
    for (const object of buildRoom(FULL).filter((o) => o.isSelectable())) {
      const b = object.bounds();
      expect(object.anchor().y).toBeGreaterThanOrEqual(b.y1 - 1);
      expect(object.anchor().x).toBeGreaterThanOrEqual(ROOM_EXTENTS.x0);
      expect(object.anchor().x).toBeLessThanOrEqual(ROOM_EXTENTS.x1);
    }
  });

  it("draws at most 7 books and 8 manga on the shelf, however many are highlighted", () => {
    const many = buildRoom([section("BOOK", 12, 1), section("MANGA", 12, 100)]);
    const few = buildRoom([section("BOOK", 7, 1), section("MANGA", 8, 100)]);
    const shelf = (room: ReturnType<typeof buildRoom>) =>
      room.find((o) => o.id === "estante")!.boxes(DEFAULT_VIEW).length;
    expect(shelf(many)).toBe(shelf(few));
  });

  it("gives a book the same color every time", () => {
    const a = buildRoom([section("BOOK", 3, 5)])
      .find((o) => o.id === "estante")!
      .boxes(DEFAULT_VIEW);
    const b = buildRoom([section("BOOK", 3, 5)])
      .find((o) => o.id === "estante")!
      .boxes(DEFAULT_VIEW);
    expect(a.map((x) => x.color)).toEqual(b.map((x) => x.color));
  });

  it("shows a cover only for an https image, otherwise paper", () => {
    const sections: RoomSection[] = [
      {
        category: "MOVIE",
        items: [
          item(1, "MOVIE", "https://img.example/a.jpg"),
          item(2, "MOVIE", "http://img.example/b.jpg"),
          item(3, "MOVIE", null),
        ],
      },
    ];
    const wall = buildRoom(sections)
      .find((o) => o.id === "parede")!
      .boxes(DEFAULT_VIEW);
    const faces = wall.map((b) => b.textures?.right);
    expect(faces[0]?.image).toBe("https://img.example/a.jpg");
    expect(faces[1]?.image).toBeUndefined();
    expect(faces[2]?.image).toBeUndefined();
    expect(faces[2]?.background).toBeDefined();
  });
});

describe("RoomObject.onSelect", () => {
  it("selects the object by default (Poster, Shelf, Tv)", () => {
    for (const id of ["parede", "mural", "estante", "tv"]) {
      const { calls, actions } = recorder();
      buildRoom(FULL)
        .find((o) => o.id === id)!
        .onSelect(actions);
      expect(calls).toEqual([`select:${id}`]);
    }
  });

  it("makes the RecordPlayer select itself and ask for the lifted sleeve", () => {
    const { calls, actions } = recorder();
    buildRoom(FULL)
      .find((o) => o.id === "vitrola")!
      .onSelect(actions);
    expect(calls).toEqual(["select:vitrola", "reveal:vitrola"]);
  });

  it("lifts the front sleeve of the RecordPlayer only while it is revealed", () => {
    const player = buildRoom(FULL).find((o) => o.id === "vitrola")!;
    const rest = player.boxes(DEFAULT_VIEW);
    const lifted = player.boxes({ lamp: true, revealed: "vitrola" });
    const other = player.boxes({ lamp: true, revealed: "tv" });
    expect(other).toEqual(rest);
    const movedAt = lifted.flatMap((box, i) => (box.y0 !== rest[i].y0 ? [i] : []));
    expect(movedAt).toHaveLength(1);
    expect(lifted[movedAt[0]].y0 - rest[movedAt[0]].y0).toBe(14);
  });
});

describe("the lamp", () => {
  it("darkens every face when it is off and never beyond 0.85", () => {
    const box: Box = { x0: 0, x1: 10, y0: 0, y1: 10, z0: 0, z1: 10, color: "red", group: "" };
    const on = faceGeometry(box, true);
    const off = faceGeometry(box, false);
    off.forEach((face, i) => expect(face.shade).toBeGreaterThan(on[i].shade));
    expect(Math.max(...off.map((f) => f.shade))).toBeLessThanOrEqual(0.85);
  });

  it("changes the decor: the light pool goes out", () => {
    const decor = buildRoom([]).find((o) => !o.isSelectable())!;
    expect(decor.boxes({ lamp: false, revealed: null }).length).toBe(
      decor.boxes(DEFAULT_VIEW).length - 1,
    );
  });
});

describe("DOM budget", () => {
  it("renders a full room with a few hundred faces", () => {
    const room = buildRoom(FULL);
    const boxes = room.flatMap((o) => o.boxes(DEFAULT_VIEW));
    const faces = faceCount(boxes);
    // Documented in the PR: boxes + faces + cover images. Keep it from growing by accident.
    console.info(`room DOM: ${boxes.length} boxes, ${faces} faces`);
    expect(faces).toBeLessThan(600);
  });
});
