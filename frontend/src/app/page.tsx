import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import {
  HeroActions,
  SignedOutHeroActions,
  SignedOutTopBarActions,
  TopBarActions,
} from "@/components/landing/AccountActions";
import { ExampleShelf } from "@/components/landing/ExampleShelf";
import { CookieNotice } from "@/components/CookieNotice";
import { CategoryTag } from "@/components/profile/CategoryTag";
import { siteUrl } from "@/lib/public-profile";
import { Logo } from "@/components/Logo";

const TITLE = "meuquarto: seu gosto num quarto só";
const DESCRIPTION =
  "Um link para a bio com o que você assiste, lê, joga e ouve. Filmes, séries, animes, mangás, livros, games e música num endereço só.";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl()),
  title: { absolute: TITLE },
  description: DESCRIPTION,
  openGraph: {
    type: "website",
    siteName: "meuquarto",
    locale: "pt_BR",
    title: TITLE,
    description: DESCRIPTION,
    url: "/",
  },
};

// S1 (docs/design/telas/landing.html). The page is a static shell (Cache Components): everything
// is prerendered except the two spots that depend on the session (top bar actions and the hero
// call to action), which stream in inside <Suspense> and swap "Crie o seu" for "Ir para o painel"
// when the visitor is signed in. The fallbacks are the signed-out buttons, so the shell is complete
// and the common case (anonymous visitor) has no layout shift. proxy.ts is not used here on purpose:
// a redirect would take signed-in visitors away from the landing page they may want to read.

// Integrations: only what exists is presented as working (docs/backlog.md, M2).
const INTEGRATIONS = [
  { name: "Letterboxd", categories: ["MOVIE"], ready: true },
  { name: "AniList", categories: ["ANIME", "MANGA"], ready: true },
  { name: "Goodreads", categories: ["BOOK"], ready: true },
  { name: "Steam", categories: ["GAME"], ready: false },
  { name: "Last.fm", categories: ["MUSIC"], ready: false },
] as const;

export default function Landing() {
  return (
    <div className="page">
      <header className="topbar">
        <div className="wrap topbar__in">
          <Logo href="/" id="site-home" />
          <nav className="actions" aria-label="Conta">
            <Suspense fallback={<SignedOutTopBarActions />}>
              <TopBarActions />
            </Suspense>
          </nav>
        </div>
      </header>

      <main className="wrap sections">
        <section
          className="grid"
          style={{ paddingTop: "var(--page-top)", alignItems: "center" }}
          aria-labelledby="hero-h"
        >
          <div className="span-all d-span-6 stack" style={{ gap: "var(--l3)" }}>
            <div className="stack">
              <h1 id="hero-h" className="t-display-xl">
                Seu gosto num quarto só
              </h1>
              <p className="t-hand">filmes, animes, livros, games e música</p>
            </div>
            <p className="t-lead measure-s">
              Um link para a bio com o que você assiste, lê, joga e ouve. Conecte o Letterboxd e o
              AniList, e o perfil se atualiza sozinho.
            </p>
            <div
              className="stack-tight"
              style={{ display: "flex", flexDirection: "column", gap: "var(--l1)" }}
            >
              <div className="actions">
                <Suspense fallback={<SignedOutHeroActions />}>
                  <HeroActions />
                </Suspense>
              </div>
              <p className="t-sm">Grátis. Seu endereço fica meuquarto.com.br/seunome.</p>
            </div>
          </div>
          <ExampleShelf />
        </section>

        <section className="stack" style={{ gap: "var(--l3)" }} aria-labelledby="como-h">
          <h2 id="como-h" className="t-h2">
            Como funciona
          </h2>
          <ol className="grid" style={{ listStyle: "none", padding: 0, rowGap: "var(--l3)" }}>
            <li className="span-all t-span-4 d-span-4 step">
              <span className="step__n" aria-hidden="true">
                1
              </span>
              <h3 className="t-h3">Escolha seu endereço</h3>
              <p className="t-sm">meuquarto.com.br/seunome vira o link da sua bio.</p>
            </li>
            <li className="span-all t-span-4 d-span-4 step">
              <span className="step__n" aria-hidden="true">
                2
              </span>
              <h3 className="t-h3">Conecte suas contas</h3>
              <p className="t-sm">
                O que você registra no Letterboxd, no AniList e no Goodreads entra na sua coleção.
                Last.fm e Steam chegam em breve.
              </p>
            </li>
            <li className="span-all t-span-4 d-span-4 step">
              <span className="step__n" aria-hidden="true">
                3
              </span>
              <h3 className="t-h3">Fixe seus favoritos</h3>
              <p className="t-sm">Busque qualquer obra e fixe na parede, com uma nota sua.</p>
            </li>
          </ol>
        </section>

        <section className="grid" aria-labelledby="int-h">
          <div className="span-all d-span-5 pair-2">
            <h2 id="int-h" className="t-h2">
              Usa o que você já usa
            </h2>
            <p className="t-body muted measure-s">
              Seu histórico continua onde está; o meuquarto só mostra. Séries e qualquer outra obra
              você busca e fixa à mão.
            </p>
          </div>
          <ul className="list span-all d-span-6" style={{ gridColumnEnd: -1 }}>
            {INTEGRATIONS.map((integration) => (
              <li key={integration.name}>
                <span className="grow t-h3">{integration.name}</span>
                <span className="row">
                  {integration.ready ? null : <span className="t-cap">em breve</span>}
                  {integration.categories.map((category) => (
                    <CategoryTag key={category} category={category} />
                  ))}
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section
          className="region"
          aria-labelledby="q-h"
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "var(--l2)",
          }}
        >
          <div className="pair-2 measure">
            <h2 id="q-h" className="t-h2">
              Seu perfil também vai ser um quarto
            </h2>
            <p className="t-body muted">
              Em breve, os destaques viram objetos em 3D: pôsteres na parede, livros na estante,
              discos na vitrola, o game na TV.
            </p>
          </div>
        </section>

        <section className="stack" style={{ gap: "var(--l3)" }} aria-labelledby="plano-h">
          <div className="pair-2">
            <h2 id="plano-h" className="t-h2">
              Grátis, com extras se quiser
            </h2>
            <p className="t-body muted">
              Os extras serão compra única, com Pix ou cartão. Nada de assinatura.
            </p>
          </div>
          <div className="grid" style={{ rowGap: "var(--grid-gap, var(--col-gap))" }}>
            <div className="span-all t-span-4 d-span-6 region region--line">
              <h3 className="t-h3">Grátis</h3>
              <p className="t-sm">
                Perfil completo, as integrações disponíveis, 4 destaques por categoria e os temas
                Noite e Dia.
              </p>
            </div>
            <div className="span-all t-span-4 d-span-6 region region--tint">
              <h3 className="t-h3">Pack de destaques e temas</h3>
              <p className="t-sm" style={{ color: "var(--ink)" }}>
                Até 12 destaques por categoria e temas originais para o perfil. Em breve, com preço
                anunciado antes de abrir a loja.
              </p>
            </div>
          </div>
        </section>
      </main>

      <footer className="footer">
        <div className="wrap footer__in">
          <div className="stack">
            <span className="wordmark">meuquarto</span>
            <p className="t-cap">
              <Link href="/termos" prefetch={false}>
                Termos
              </Link>{" "}
              e{" "}
              <Link href="/privacidade" prefetch={false}>
                privacidade
              </Link>
            </p>
          </div>
          <p className="t-cap">
            Filmes e séries: TMDB. Este produto usa a API do TMDB, mas não é endossado nem
            certificado pelo TMDB.
          </p>
        </div>
      </footer>

      <CookieNotice returnFocusTo="site-home" />
    </div>
  );
}
