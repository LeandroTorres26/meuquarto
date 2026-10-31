import type { Metadata } from "next";
import Link from "next/link";
import { ProfileTopBar } from "@/components/profile/ProfileTopBar";

export const metadata: Metadata = { title: "Perfil não encontrado · meuquarto" };

// S16 (docs/design/telas/404.html): a profile address nobody lives at. Shown for unknown, reserved
// and malformed slugs alike, because the API answers all of them with the same 404.
export default function ProfileNotFound() {
  return (
    <div className="page">
      <ProfileTopBar />
      <main className="wrap sections">
        <div className="grid" style={{ paddingTop: "var(--page-top)", alignItems: "center" }}>
          <div className="span-all t-span-4 d-span-6 stack" style={{ gap: "var(--l3)" }}>
            <div className="pair-2">
              <h1 className="t-h1">Ninguém mora aqui ainda</h1>
              <p className="t-lead muted measure-s">
                Não existe perfil nesse endereço. Pode ser que ele tenha mudado, ou que esteja livre
                para você.
              </p>
            </div>
            <div className="actions">
              <Link href="/cadastro" className="mq-btn mq-btn--primary">
                Usar esse endereço
              </Link>
              <Link href="/" className="mq-btn mq-btn--secondary">
                Ir para o início
              </Link>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
