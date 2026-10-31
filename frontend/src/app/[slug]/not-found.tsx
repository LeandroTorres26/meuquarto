import type { Metadata } from "next";
import { NotFoundScreen } from "@/components/NotFoundScreen";

export const metadata: Metadata = { title: "Perfil não encontrado · meuquarto" };

// S16 (docs/design/telas/404.html): a profile address nobody lives at. Shown for unknown, reserved
// and malformed slugs alike, because the API answers all of them with the same 404.
export default function ProfileNotFound() {
  return <NotFoundScreen kind="profile" />;
}
