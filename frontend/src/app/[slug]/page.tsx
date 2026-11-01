import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { CategoryShelf } from "@/components/profile/CategoryShelf";
import { Favorites } from "@/components/profile/Favorites";
import { NowBlock } from "@/components/profile/NowBlock";
import { ItemDialog } from "@/components/profile/ItemDialog";
import { ProfileFooter } from "@/components/profile/ProfileFooter";
import { ProfileHeader } from "@/components/profile/ProfileHeader";
import { ProfileTopBar } from "@/components/profile/ProfileTopBar";
import { CATEGORY_META, isProfileEmpty, visibleSections } from "@/lib/profile-view";
import { nowMs } from "@/lib/linked-accounts";
import { getPublicProfile, siteUrl } from "@/lib/public-profile";

// S4 (docs/design/telas/perfil.html) and, through `?item=`, S17. Server-rendered from the public read
// API; the API is the only data source (no provider is called while rendering). Static routes
// (painel, entrar, cadastro) win over this dynamic segment, and /api/* is rewritten by proxy.ts
// before routing, so nothing here shadows them. An unknown slug ends in notFound() (S16).

type Props = PageProps<"/[slug]">;

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const result = await getPublicProfile(slug);
  if (result.kind !== "ok") return { title: "Perfil não encontrado · meuquarto" };
  const { profile } = result;
  const media = visibleSections(profile.categories).map((s) =>
    CATEGORY_META[s.category].heading.toLowerCase(),
  );
  const description =
    media.length > 0
      ? `Os destaques de ${profile.displayName}: ${media.join(", ")}.`
      : `O quarto de ${profile.displayName} no meuquarto.`;
  const title = `${profile.displayName} · meuquarto`;
  return {
    metadataBase: new URL(siteUrl()),
    title,
    description,
    openGraph: {
      type: "profile",
      siteName: "meuquarto",
      locale: "pt_BR",
      title,
      description,
      url: `/${encodeURIComponent(profile.slug)}`,
    },
  };
}

async function Profile({ params }: { params: Props["params"] }) {
  const { slug } = await params;
  const result = await getPublicProfile(slug);
  if (result.kind === "not-found") notFound();
  if (result.kind === "unavailable") {
    return (
      <main className="wrap sections" style={{ paddingTop: "var(--l4)" }}>
        <div className="pair-2" role="alert">
          <h1 className="t-h1">Não foi possível abrir esse perfil agora</h1>
          <p className="t-lead muted measure-s">Aguarde um instante e recarregue a página.</p>
        </div>
      </main>
    );
  }

  const { profile } = result;
  const sections = visibleSections(profile.categories);
  const serverNow = nowMs();
  return (
    <>
      <section className="profile-top">
        <div className="wrap profile-top__in">
          <ProfileHeader profile={profile} />
          <NowBlock items={profile.now} serverNow={serverNow} />
        </div>
      </section>

      <main className="wrap sections" style={{ paddingTop: "var(--l4)" }}>
        {profile.favorites.length > 0 ? <Favorites items={profile.favorites} /> : null}
        {sections.map((section) => (
          <CategoryShelf key={section.category} slug={profile.slug} section={section} />
        ))}
        {isProfileEmpty(profile) ? (
          <section className="region region--line" aria-labelledby="vazio-h">
            <div className="pair-2 measure-s">
              <h2 id="vazio-h" className="t-h2">
                Esse quarto ainda está vazio
              </h2>
              <p className="t-sm">
                {profile.displayName} ainda não destacou nenhuma obra. Volte daqui a pouco.
              </p>
            </div>
          </section>
        ) : null}
      </main>

      <ItemDialog slug={profile.slug} ownerName={profile.displayName} />
    </>
  );
}

function ProfileSkeleton() {
  return (
    <div className="profile-top" aria-busy="true">
      <div className="wrap profile-top__in">
        <p className="t-sm" role="status">
          Carregando perfil…
        </p>
      </div>
    </div>
  );
}

export default function ProfilePage({ params }: Props) {
  return (
    <div className="page">
      <ProfileTopBar />
      <Suspense fallback={<ProfileSkeleton />}>
        <Profile params={params} />
      </Suspense>
      <ProfileFooter />
    </div>
  );
}
