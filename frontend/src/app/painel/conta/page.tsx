import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { LogoutButton } from "@/components/LogoutButton";
import { getSession } from "@/lib/session";

export const metadata: Metadata = { title: "Conta no meuquarto" };

// TEMPORARY placeholder (issue #10): the landing page after sign up and sign in until the real
// account screen (S14, docs/design/telas/conta.html) and the profile editor are built.
// proxy.ts already redirects anonymous visitors with a real 307; the check here is the page's own guard.
async function Account() {
  const session = await getSession();
  if (!session) redirect("/entrar");
  return (
    <div className="groups">
      <div className="pair-2">
        <h1 className="t-h1">Conta</h1>
        <p className="t-body">
          Você entrou como <strong>{session.email}</strong>.
        </p>
      </div>
      <div className="stack">
        <LogoutButton />
      </div>
    </div>
  );
}

export default function AccountPage() {
  return (
    <div className="page">
      <main className="wrap wrap--form" style={{ paddingBlock: "var(--page-top) var(--l5)" }}>
        <Suspense fallback={<p className="t-sm">Carregando…</p>}>
          <Account />
        </Suspense>
      </main>
    </div>
  );
}
