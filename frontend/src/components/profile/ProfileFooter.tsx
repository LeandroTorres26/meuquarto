import Link from "next/link";

/** Attribution required by the data providers (ADR 0010) and the invitation to make one's own. */
export function ProfileFooter() {
  return (
    <footer className="footer">
      <div className="wrap footer__in">
        <div className="stack">
          <Link href="/" className="wordmark">
            meuquarto
          </Link>
          <p className="t-sm">
            Seu gosto num endereço só.{" "}
            <Link href="/cadastro" prefetch={false}>
              Crie o seu
            </Link>
          </p>
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
        <div className="pair-2">
          <p className="t-cap">
            Filmes e séries: TMDB. Este produto usa a API do TMDB, mas não é endossado nem
            certificado pelo TMDB. Anime e mangá: AniList. Livros: Open Library e Goodreads. Games:
            IGDB e Steam. Música: Deezer e Last.fm.
          </p>
        </div>
      </div>
    </footer>
  );
}
