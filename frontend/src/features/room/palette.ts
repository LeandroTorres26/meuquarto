// Colors of the room illustration, ported from docs/design/telas/quarto.html. They paint the objects
// of the scene (like a drawing), not the interface: the interface colors all come from the design
// tokens. Kept in one place so a theme for the room can replace them later.

export const PALETTE = {
  wall: "hsl(28 77% 78%)",
  navy: "hsl(228 48% 28%)",
  wood: "hsl(28 52% 56%)",
  woodDark: "hsl(24 40% 39%)",
  dark: "hsl(235 21% 21%)",
  cream: "hsl(40 59% 86%)",
  teal: "hsl(187 64% 29%)",
  red: "hsl(7 66% 53%)",
  yellow: "hsl(42 100% 70%)",
  board: "hsl(27 38% 53%)",
  boardFrame: "hsl(26 30% 32%)",
  floor: "hsl(31 62% 68%)",
  baseboard: "hsl(25 69% 69%)",
  vinyl: "hsl(240 13% 5%)",
  steel: "hsl(220 10% 64%)",
  grey: "hsl(240 7% 86%)",
  speaker: "hsl(226 8% 32%)",
  green: "hsl(144 47% 66%)",
  orange: "hsl(17 100% 68%)",
  sky: "hsl(198 78% 64%)",
} as const;

/** Spine colors of books (picked by the work, so a book keeps its color). */
export const BOOK_COLORS = [
  "hsl(124 46% 70%)",
  "hsl(27 87% 67%)",
  "hsl(214 35% 37%)",
  "hsl(42 34% 89%)",
  "hsl(12 76% 61%)",
  "hsl(153 40% 30%)",
  "hsl(44 89% 61%)",
] as const;

/** Spine colors of manga volumes. */
export const MANGA_COLORS = ["hsl(266 100% 81%)", "hsl(334 100% 75%)", "hsl(198 78% 64%)"] as const;
