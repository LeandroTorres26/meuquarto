import type { Metadata } from "next";
import { NotFoundScreen } from "@/components/NotFoundScreen";

export const metadata: Metadata = { title: "Página não encontrada · meuquarto" };

// S16 for every path no route matches (the profile route has its own not-found for unknown slugs).
export default function NotFound() {
  return <NotFoundScreen kind="route" />;
}
