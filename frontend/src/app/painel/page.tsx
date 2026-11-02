import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { Logo } from "@/components/Logo";
import { PanelNav } from "@/components/PanelNav";
import { ProfileEditorScreen } from "@/components/profile/ProfileEditorScreen";
import { fetchOwnProfile } from "@/lib/collection-server";
import { getSession } from "@/lib/session";

export const metadata: Metadata = { title: "Seu perfil, painel do meuquarto" };

// S6 (docs/design/telas/painel-perfil.html). The first paint is rendered on the server with the
// visitor's session; every change runs from the browser (CSRF).
async function Perfil() {
  const session = await getSession();
  if (!session) redirect("/entrar");
  const profile = await fetchOwnProfile();

  return (
    <>
      <div className="page-head">
        <h1 className="t-h1">Seu perfil</h1>
        <p className="t-body muted">
          O que aparece no topo de meuquarto.com.br/{profile?.slug ?? session.slug}.
        </p>
      </div>
      {profile ? (
        <ProfileEditorScreen initial={profile} />
      ) : (
        <p className="t-sm" role="alert">
          Não foi possível carregar seu perfil agora. Recarregue a página.
        </p>
      )}
    </>
  );
}

export default function PerfilPage() {
  return (
    <div className="page">
      <header className="topbar">
        <div className="wrap topbar__in">
          <Logo href="/" />
          <Suspense fallback={null}>
            <ProfileLink />
          </Suspense>
        </div>
      </header>
      <div className="wrap pshell">
        <PanelNav current="Perfil" />
        <main className="pmain">
          <Suspense fallback={<p className="t-sm">Carregando…</p>}>
            <Perfil />
          </Suspense>
        </main>
      </div>
    </div>
  );
}

async function ProfileLink() {
  const session = await getSession();
  if (!session?.slug) return null;
  return (
    <Link href={`/${session.slug}`} className="mq-btn mq-btn--ghost mq-btn--sm">
      Ver meu perfil
    </Link>
  );
}
