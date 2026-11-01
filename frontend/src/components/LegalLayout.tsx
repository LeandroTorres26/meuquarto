import Link from "next/link";
import type { ReactNode } from "react";
import { Logo } from "@/components/Logo";
import { LEGAL_UPDATED_ISO, LEGAL_UPDATED_LABEL } from "@/lib/legal";

type Props = {
  current: "termos" | "privacidade";
  title: string;
  children: ReactNode;
};

/** Reading layout of S15 (docs/design/telas/termos.html): the two legal documents share it. */
export function LegalLayout({ current, title, children }: Props) {
  return (
    <div className="page">
      <header className="topbar">
        <div className="wrap topbar__in">
          <Logo href="/" />
          <Link href="/entrar" prefetch={false} className="mq-btn mq-btn--ghost mq-btn--sm">
            Entrar
          </Link>
        </div>
      </header>

      <main className="wrap wrap--read" style={{ paddingBottom: "var(--l5)" }}>
        <div className="page-head">
          <h1 className="t-h1">{title}</h1>
          <p className="t-sm">
            Atualizado em <time dateTime={LEGAL_UPDATED_ISO}>{LEGAL_UPDATED_LABEL}</time>.
          </p>
        </div>
        <nav className="tabs" aria-label="Documentos legais" style={{ marginTop: "var(--l3)" }}>
          <Link
            href="/termos"
            className="tab"
            aria-current={current === "termos" ? "page" : undefined}
          >
            Termos de uso
          </Link>
          <Link
            href="/privacidade"
            className="tab"
            aria-current={current === "privacidade" ? "page" : undefined}
          >
            Privacidade
          </Link>
        </nav>
        <div className="groups" style={{ marginTop: "var(--l3)" }}>
          {children}
        </div>
      </main>
    </div>
  );
}

/** One titled block of a legal document. */
export function LegalSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="pair-2">
      <h2 className="t-h3">{title}</h2>
      {children}
    </section>
  );
}
