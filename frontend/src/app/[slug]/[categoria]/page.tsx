import { ArrowLeft } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Suspense } from "react";
import { NotFoundScreen } from "@/components/NotFoundScreen";
import { CategoryTools } from "@/components/profile/CategoryTools";
import { HighlightCard } from "@/components/profile/HighlightCard";
import { ItemDialog } from "@/components/profile/ItemDialog";
import { ListingPager } from "@/components/profile/ListingPager";
import { ProfileFooter } from "@/components/profile/ProfileFooter";
import { ProfileTopBar } from "@/components/profile/ProfileTopBar";
import {
  categoryFromRoute,
  listingHref,
  pageCount,
  parseListingState,
  statusText,
  type ListingState,
} from "@/lib/category-listing";
import { CATEGORY_META, countLabel, type Category } from "@/lib/profile-view";
import { getPublicListing, getPublicProfile, siteUrl } from "@/lib/public-profile";

// S18 (docs/design/telas/categoria.html): the whole visible collection of one category. Server-rendered
// from the public read API (one page of 24 per request); search, order and page live in the URL.
// An unknown category segment ends in notFound(); an unknown profile renders the S16 profile variant.

type Props = PageProps<"/[slug]/[categoria]">;

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const { slug, categoria } = await params;
  const category = categoryFromRoute(categoria);
  const result = await getPublicProfile(slug);
  if (!category || result.kind !== "ok") return { title: "Página não encontrada · meuquarto" };
  const { profile } = result;
  const meta = CATEGORY_META[category];
  const state = parseListingState(await searchParams);
  const title = `${meta.heading} de ${profile.displayName} · meuquarto`;
  const description = `Todos os itens de ${meta.heading.toLowerCase()} da coleção de ${profile.displayName}.`;
  return {
    metadataBase: new URL(siteUrl()),
    title,
    description,
    // Searches and deeper pages are views of the same collection, not new pages to index.
    robots: state.q ? { index: false, follow: true } : undefined,
    openGraph: {
      type: "website",
      siteName: "meuquarto",
      locale: "pt_BR",
      title,
      description,
      url: listingHref(profile.slug, category, {}),
    },
  };
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="page">
      <ProfileTopBar />
      {children}
      <ProfileFooter />
    </div>
  );
}

async function Listing({ props }: { props: Props }) {
  const { slug, categoria } = await props.params;
  const category = categoryFromRoute(categoria);
  if (!category) notFound();
  const state = parseListingState(await props.searchParams);

  const [profileResult, listingResult] = await Promise.all([
    getPublicProfile(slug),
    getPublicListing(slug, category, state),
  ]);
  if (profileResult.kind === "not-found" || listingResult.kind === "not-found") {
    return <NotFoundScreen kind="profile" />;
  }
  if (profileResult.kind !== "ok" || listingResult.kind !== "ok") {
    return (
      <Shell>
        <main className="wrap sections" style={{ paddingTop: "var(--l4)" }}>
          <div className="pair-2" role="alert">
            <h1 className="t-h1">Não foi possível abrir essa coleção agora</h1>
            <p className="t-lead muted measure-s">Aguarde um instante e recarregue a página.</p>
          </div>
        </main>
      </Shell>
    );
  }

  const { profile } = profileResult;
  const { listing } = listingResult;
  const pages = pageCount(listing.total);
  if (state.page > pages) redirect(listingHref(profile.slug, category, { ...state, page: pages }));

  const meta = CATEGORY_META[category];
  const section = profile.categories.find((s) => s.category === category);
  const collectionTotal = section?.total ?? 0;
  const pinned = section && !section.mostRecent ? section.items.length : 0;
  const empty = collectionTotal === 0 && listing.total === 0 && !state.q;
  const noResult = listing.total === 0 && !empty;
  const profileHref = `/${encodeURIComponent(profile.slug)}`;

  return (
    <Shell>
      <main
        className="wrap sections"
        style={{ paddingTop: "var(--page-top)", paddingBottom: "var(--l5)" }}
      >
        <div className="page-head">
          <Link className="owner" href={profileHref}>
            <ArrowLeft className="icon" aria-hidden="true" />
            <span>{profile.displayName}</span>
          </Link>
          <div className="pair-2">
            <h1 className="t-h1" id="cat-h">
              {meta.heading}
            </h1>
            <p className="t-sm">
              {empty
                ? "Coleção vazia."
                : `${countLabel(category, collectionTotal)} na coleção de ${profile.displayName}.${
                    pinned > 0 ? ` ${pinned} em destaque no perfil.` : ""
                  }`}
            </p>
          </div>
        </div>

        <section className="groups" aria-labelledby="cat-h">
          {empty ? null : (
            <CategoryTools
              slug={profile.slug}
              category={category}
              q={state.q}
              ordem={state.ordem}
            />
          )}
          <div className="stack">
            {listing.items.length > 0 ? (
              <ul className="shelf shelf--grid" aria-label="Coleção">
                {listing.items.map((item, index) => (
                  <li key={item.ref}>
                    <HighlightCard
                      item={item}
                      highlighted={item.highlighted}
                      eager={index < 6}
                      href={itemHref(profile.slug, category, state, item.ref)}
                    />
                  </li>
                ))}
              </ul>
            ) : null}
            {empty ? (
              <div className="cat-empty">
                <div className="pair-2">
                  <h2 className="t-h3">
                    {profile.displayName} ainda não tem {meta.unit[1]} na coleção.
                  </h2>
                  <p className="t-sm">Quando houver, aparecem aqui.</p>
                </div>
                <Link className="mq-btn mq-btn--secondary" href={profileHref}>
                  Voltar ao perfil
                </Link>
              </div>
            ) : null}
            {noResult ? (
              <div className="cat-empty">
                <div className="pair-2">
                  <h2 className="t-h3">
                    Nada com “{state.q}” em {meta.heading}.
                  </h2>
                  <p className="t-sm">Confira a grafia ou busque pelo título original.</p>
                </div>
                <Link
                  className="mq-btn mq-btn--secondary"
                  href={listingHref(profile.slug, category, { ordem: state.ordem })}
                >
                  Limpar busca
                </Link>
              </div>
            ) : null}
            <p className="t-cap" role="status" aria-live="polite">
              {statusText(category, state, listing.items.length, listing.total)}
            </p>
            <ListingPager slug={profile.slug} category={category} state={state} pages={pages} />
          </div>
        </section>
      </main>

      <ItemDialog slug={profile.slug} ownerName={profile.displayName} />
    </Shell>
  );
}

/** The no-JavaScript address of a card: this same page (search, order and page kept) with the dialog open. */
function itemHref(slug: string, category: Category, state: ListingState, ref: number) {
  const base = listingHref(slug, category, state);
  return `${base}${base.includes("?") ? "&" : "?"}item=${ref}`;
}

export default function CategoryListingPage(props: Props) {
  return (
    <Suspense
      fallback={
        <Shell>
          <div className="wrap" aria-busy="true" style={{ paddingTop: "var(--page-top)" }}>
            <p className="t-sm" role="status">
              Carregando coleção…
            </p>
          </div>
        </Shell>
      }
    >
      <Listing props={props} />
    </Suspense>
  );
}
