import type { Metadata } from "next";
import { NotFoundScreen } from "@/components/NotFoundScreen";

export const metadata: Metadata = { title: "Página não encontrada · meuquarto" };

// S16, route variant: `/{slug}/{categoria}` with a segment that is not one of the seven categories.
// (An unknown profile is rendered by the page itself with the profile variant.)
export default function CategoryNotFound() {
  return <NotFoundScreen kind="route" />;
}
