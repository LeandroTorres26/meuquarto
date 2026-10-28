import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { ChangePasswordForm } from "@/components/ChangePasswordForm";
import { DeleteAccountForm } from "@/components/DeleteAccountForm";
import { LogoutButton } from "@/components/LogoutButton";
import { PanelNav } from "@/components/PanelNav";
import { getSession } from "@/lib/session";

export const metadata: Metadata = { title: "Conta, painel do meuquarto" };

// S14 (docs/design/telas/conta.html). proxy.ts already redirects anonymous visitors with a real 307;
// the check here is the page's own guard. The owner comes from the session, never from the request.
async function Account() {
  const session = await getSession();
  if (!session) redirect("/entrar");
  const address = session.slug ? `meuquarto.com.br/${session.slug}` : "o endereço do seu perfil";
  return (
    <>
      <div className="page-head">
        <h1 className="t-h1">Conta</h1>
        <p className="t-body muted">
          Entrou como <strong>{session.email}</strong>.
        </p>
      </div>

      <ChangePasswordForm />

      <section className="stack next" aria-labelledby="sessao-h">
        <div className="row row--between">
          <div className="pair-2">
            <h2 id="sessao-h" className="t-h2">
              Sair
            </h2>
            <p className="help">Encerra a sessão neste aparelho.</p>
          </div>
          <LogoutButton />
        </div>
      </section>

      <DeleteAccountForm address={address} />
    </>
  );
}

export default function AccountPage() {
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
        <PanelNav current="Conta" />
        <main className="pmain" style={{ maxWidth: 640 }}>
          <Suspense fallback={<p className="t-sm">Carregando…</p>}>
            <Account />
          </Suspense>
        </main>
      </div>
    </div>
  );
}
