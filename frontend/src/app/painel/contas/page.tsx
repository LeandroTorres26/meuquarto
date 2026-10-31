import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { AlertBox } from "@/components/AlertBox";
import { ConnectedCard } from "@/components/linked/ConnectedCard";
import { LinkCard } from "@/components/linked/LinkCard";
import { SoonCard } from "@/components/linked/SoonCard";
import { PanelNav } from "@/components/PanelNav";
import { comingSoonProviders, linkableProviders, nowMs } from "@/lib/linked-accounts";
import { fetchLinkedAccounts } from "@/lib/linked-accounts-server";
import { getSession } from "@/lib/session";

export const metadata: Metadata = { title: "Contas vinculadas, painel do meuquarto" };

// S9 (docs/design/telas/painel-contas.html). The list is rendered on the server with the visitor's
// session; link, sync and unlink run from the browser (CSRF) and then refresh this data.
async function LinkedAccounts() {
  const session = await getSession();
  if (!session) redirect("/entrar");
  const accounts = await fetchLinkedAccounts();
  const serverNow = nowMs();

  return (
    <>
      <div className="page-head">
        <h1 className="t-h1">Contas vinculadas</h1>
        <p className="t-body muted measure">
          Lemos só o que é público e atualizamos sozinhos a cada 6 horas. O que vem daqui entra na
          sua coleção; o que você adicionou à mão e os seus destaques nunca mudam.
        </p>
      </div>

      {accounts === null ? (
        <AlertBox>
          Não foi possível carregar suas contas agora. Recarregue a página em instantes.
        </AlertBox>
      ) : (
        <>
          {accounts.length > 0 && (
            <section className="stack" aria-labelledby="ativas-h">
              <h2 id="ativas-h" className="t-h2">
                Conectadas
              </h2>
              <div className="cards">
                {accounts.map((account) => (
                  <ConnectedCard
                    key={account.id}
                    account={account}
                    slug={session.slug}
                    serverNow={serverNow}
                  />
                ))}
              </div>
            </section>
          )}

          <section className="stack next" aria-labelledby="novas-h">
            <div className="pair-2">
              <h2 id="novas-h" className="t-h2">
                Conectar mais
              </h2>
              <p className="help">Basta o nome de usuário. O perfil precisa estar público.</p>
            </div>
            <div className="cards">
              {linkableProviders(accounts).map((provider) => (
                <LinkCard key={provider.id} provider={provider} />
              ))}
              {comingSoonProviders().map((provider) => (
                <SoonCard key={provider.id} provider={provider} />
              ))}
              <div className="region region--line" role="group" aria-labelledby="csv-h">
                <h3 id="csv-h" className="t-h3">
                  Outra plataforma
                </h3>
                <p className="t-sm">
                  Usa Skoob, StoryGraph ou Backloggd? Importação por arquivo CSV chega em uma
                  próxima versão. Séries você adiciona à mão em Destaques.
                </p>
                <button
                  type="button"
                  className="mq-btn mq-btn--secondary mq-btn--sm self-start"
                  disabled
                >
                  Em breve
                </button>
              </div>
            </div>
          </section>
        </>
      )}
    </>
  );
}

export default function LinkedAccountsPage() {
  return (
    <div className="page">
      <header className="topbar">
        <div className="wrap topbar__in">
          <Link href="/" className="wordmark">
            meuquarto
          </Link>
        </div>
      </header>
      <div className="wrap pshell">
        <PanelNav current="Contas vinculadas" />
        <main className="pmain">
          <Suspense fallback={<p className="t-sm">Carregando…</p>}>
            <LinkedAccounts />
          </Suspense>
        </main>
      </div>
    </div>
  );
}
