import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { AlertBox } from "@/components/AlertBox";
import { ConnectedCard } from "@/components/linked/ConnectedCard";
import { LinkCard } from "@/components/linked/LinkCard";
import { SoonCard } from "@/components/linked/SoonCard";
import { PanelNav } from "@/components/PanelNav";
import { inProviderOrder, isLinkable, nowMs, unlinkedProviders } from "@/lib/linked-accounts";
import { fetchLinkedAccounts, fetchProviderAvailability } from "@/lib/linked-accounts-server";
import { getSession } from "@/lib/session";
import { Logo } from "@/components/Logo";

export const metadata: Metadata = { title: "Contas vinculadas, painel do meuquarto" };

// S9 (docs/design/telas/painel-contas.html). The list is rendered on the server with the visitor's
// session; link, sync and unlink run from the browser (CSRF) and then refresh this data.
async function LinkedAccounts() {
  const session = await getSession();
  if (!session) redirect("/entrar");
  const [accounts, availability] = await Promise.all([
    fetchLinkedAccounts(),
    fetchProviderAvailability(),
  ]);
  const serverNow = nowMs();
  const unlinked = accounts ? unlinkedProviders(accounts) : [];

  return (
    <>
      <div className="page-head">
        <h1 className="t-h1">Contas vinculadas</h1>
        <p className="t-body muted measure">
          Vincule seus perfis: todos viram links no topo do seu perfil. Os das plataformas que
          deixam ler a lista também atualizam sua coleção sozinhos, a cada 6 horas. O que você
          adicionou à mão e os seus destaques nunca mudam.
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
                Vinculadas
              </h2>
              <div className="cards">
                {inProviderOrder(accounts).map((account) => (
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

          {unlinked.length > 0 && (
            <section className="stack next" aria-labelledby="novas-h">
              <div className="pair-2">
                <h2 id="novas-h" className="t-h2">
                  Vincular mais
                </h2>
                <p className="help">
                  Basta o nome de usuário. Para atualizar sozinho, o perfil precisa estar público.
                </p>
              </div>
              <div className="cards">
                {unlinked.map((provider) =>
                  isLinkable(provider, availability) ? (
                    <LinkCard key={provider.id} provider={provider} />
                  ) : (
                    <SoonCard key={provider.id} provider={provider} />
                  ),
                )}
              </div>
            </section>
          )}
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
          <Logo href="/" />
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
