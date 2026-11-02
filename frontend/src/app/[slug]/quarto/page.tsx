import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { RoomLoader } from "@/features/room/RoomLoader";
import type { RoomSection } from "@/features/room/objects/buildRoom";
import { getPublicProfile, siteUrl } from "@/lib/public-profile";

// S5 (docs/design/telas/quarto.html): the read-only room built from the profile highlights. Same
// data source as S4 (the public read API, never a provider), so the room costs the profile nothing.
// `quarto` is a static segment: it wins over the dynamic [categoria] of S18, and the backend keeps
// it in the reserved slug list. The scene itself is a client-only chunk (RoomLoader).

type Props = PageProps<"/[slug]/quarto">;

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const result = await getPublicProfile(slug);
  if (result.kind !== "ok") return { title: "Perfil não encontrado · meuquarto" };
  const { profile } = result;
  const title = `O quarto de ${profile.displayName} · meuquarto`;
  const description = `Os destaques de ${profile.displayName} num quarto para explorar.`;
  return {
    metadataBase: new URL(siteUrl()),
    title,
    description,
    openGraph: {
      type: "website",
      siteName: "meuquarto",
      locale: "pt_BR",
      title,
      description,
      url: `/${encodeURIComponent(profile.slug)}/quarto`,
    },
  };
}

async function Room({ params }: { params: Props["params"] }) {
  const { slug } = await params;
  const result = await getPublicProfile(slug);
  if (result.kind === "not-found") notFound();
  if (result.kind === "unavailable") {
    return (
      <main className="wrap sections" style={{ paddingTop: "var(--l4)" }}>
        <div className="pair-2" role="alert">
          <h1 className="t-h1">Não foi possível abrir esse quarto agora</h1>
          <p className="t-lead muted measure-s">Aguarde um instante e recarregue a página.</p>
        </div>
      </main>
    );
  }
  const { profile } = result;
  // Only what the scene draws and lists: no notes, no totals.
  const sections: RoomSection[] = profile.categories.map((section) => ({
    category: section.category,
    items: section.items.map((item) => ({
      ref: item.ref,
      category: item.category,
      title: item.title,
      year: item.year,
      imageUrl: item.imageUrl,
    })),
  }));
  return <RoomLoader slug={profile.slug} ownerName={profile.displayName} sections={sections} />;
}

export default function RoomPage({ params }: Props) {
  return (
    <Suspense
      fallback={
        <div className="page room" aria-busy="true">
          <p className="t-sm" role="status" style={{ padding: "var(--l4)" }}>
            Carregando o quarto…
          </p>
        </div>
      }
    >
      <Room params={params} />
    </Suspense>
  );
}
