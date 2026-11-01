import { BookCopy, BookOpen, Clapperboard, Disc3, Gamepad2, Sparkles, Tv } from "lucide-react";
import type { Category } from "@/lib/profile-view";

const ICONS = {
  MOVIE: Clapperboard,
  SERIES: Tv,
  ANIME: Sparkles,
  MANGA: BookCopy,
  BOOK: BookOpen,
  GAME: Gamepad2,
  MUSIC: Disc3,
} satisfies Record<Category, typeof Clapperboard>;

/** Lucide icon of a medium (the same ones the design uses in stickers and the empty cover slot). */
export function CategoryIcon({ category, className }: { category: Category; className?: string }) {
  const Icon = ICONS[category];
  return <Icon className={className} aria-hidden="true" />;
}
