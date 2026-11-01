import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense, type ReactNode } from "react";
import { Logo } from "@/components/Logo";
import { AgoraScreen } from "@/components/now/AgoraScreen";
import { PanelNav } from "@/components/PanelNav";
import { fetchNow } from "@/lib/collection-server";
import { getSession } from "@/lib/session";

export const metadata: Metadata = { title: "Agora, painel do meuquarto" };

// S10 (docs/design/telas/painel-agora.html). The first paint is rendered on the server with the
// visitor's session; every change runs from the browser (CSRF) and answers with the new state.
async function Agora() {
  const session = await getSession();
  if (!session) redirect("/entrar");
  const entries = await fetchNow();

  return (
    <>
      <div className="page-head">
        <h1 className="t-h1">Agora</h1>
        <p className="t-body muted measure">
          Um item por categoria no topo do seu perfil. Deixe no automático, escolha você mesmo ou
          esconda.
        </p>
      </div>
      <AgoraScreen initial={entries} />
    </>
  );
}

export default function AgoraPage() {
  return (
    <AgoraShell>
      <Suspense fallback={<p className="t-sm">Carregando…</p>}>
        <Agora />
      </Suspense>
    </AgoraShell>
  );
}

// The shell is static; "Ver meu perfil" needs the slug, so it is a small dynamic island.
function AgoraShell({ children }: { children: ReactNode }) {
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
        <PanelNav current="Agora" />
        <main className="pmain">{children}</main>
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
