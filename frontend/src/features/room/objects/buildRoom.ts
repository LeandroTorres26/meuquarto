import type { Category } from "../../../lib/profile-view";
import { Decor } from "./Decor";
import { Poster } from "./Poster";
import { RecordPlayer } from "./RecordPlayer";
import type { RoomItem, RoomObject } from "./RoomObject";
import { Shelf } from "./Shelf";
import { Tv } from "./Tv";

/** The highlights of one category, as the public profile API sends them. */
export type RoomSection = { category: Category; items: readonly RoomItem[] };

function itemsOf(sections: readonly RoomSection[], ...categories: Category[]): RoomItem[] {
  return categories.flatMap((category) =>
    sections.filter((s) => s.category === category).flatMap((s) => s.items),
  );
}

/**
 * Highlights by category to the objects of the room, in drawing order. Films and series hang on
 * the wall, anime on the mural, books and manga on the shelf, music on the record player, games on
 * the TV. An object always exists, even without items (an empty frame, an empty shelf), so the
 * room has the same shape for every profile.
 */
export function buildRoom(sections: readonly RoomSection[]): RoomObject[] {
  return [
    Decor.room(),
    Poster.wall(itemsOf(sections, "MOVIE", "SERIES")),
    Poster.mural(itemsOf(sections, "ANIME")),
    Shelf.of(itemsOf(sections, "BOOK", "MANGA")),
    RecordPlayer.of(itemsOf(sections, "MUSIC")),
    Tv.of(itemsOf(sections, "GAME")),
  ];
}
