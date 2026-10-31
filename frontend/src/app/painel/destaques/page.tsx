import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense, type ReactNode } from "react";
import { DestaquesScreen } from "@/components/collection/DestaquesScreen";
import { PanelNav } from "@/components/PanelNav";
import { tabFromParam } from "@/lib/collection";
import {
  fetchCollectionPage,
  fetchFavoriteCategories,
  fetchHighlights,
} from "@/lib/collection-server";
import { getSession } from "@/lib/session";
import { Logo } from "@/components/Logo";

export const metadata: Metadata = { title: "Destaques e coleção, painel do meuquarto" };

// S7 (docs/design/telas/painel-destaques.html). The first paint is rendered on the server with the
// visitor's session (highlights, first page of the open category); every change runs from the browser.
async function Destaques({ params }: { params: Promise<{ aba?: string | string[] }> }) {
  const session = await getSession();
  if (!session) redirect("/entrar");
  const tab = tabFromParam((await params).aba);
  const listCategory = tab === "FAVORITES" ? "MOVIE" : tab;

  const [overview, page, favoriteCategories] = await Promise.all([
    fetchHighlights(),
    fetchCollectionPage(listCategory),
    session.slug ? fetchFavoriteCategories(session.slug) : Promise.resolve({}),
  ]);

  return (
    <>
      <div className="page-head">
        <h1 className="t-h1">Destaques</h1>
        <p className="t-body muted measure">
          Sua coleção guarda tudo, sem limite. Os destaques são o que você escolhe dela para a
          parede do perfil.
        </p>
      </div>
      <DestaquesScreen
        initialTab={tab}
        initialOverview={overview}
        initialPage={page}
        initialListCategory={listCategory}
        favoriteCategories={favoriteCategories}
      />
    </>
  );
}

export default function DestaquesPage({
  searchParams,
}: {
  searchParams: Promise<{ aba?: string | string[] }>;
}) {
  return (
    <DestaquesShell>
      <Suspense fallback={<p className="t-sm">Carregando…</p>}>
        <Destaques params={searchParams} />
      </Suspense>
    </DestaquesShell>
  );
}

// The shell is static; "Ver meu perfil" needs the slug, so it is a small dynamic island.
function DestaquesShell({ children }: { children: ReactNode }) {
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
        <PanelNav current="Destaques" />
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
