import Link from "next/link";
import type { ReactNode } from "react";

type Props = {
  /** Top bar action: the other auth screen. */
  action: { href: string; label: string };
  /** Handwritten line above the cover art (desktop only, decorative). */
  note: string;
  art: ReactNode;
  children: ReactNode;
};

/** Split layout of S2 and S3 (docs/design/telas/cadastro.html, entrar.html). */
export function AuthLayout({ action, note, art, children }: Props) {
  return (
    <div className="page">
      <header className="topbar">
        <div className="wrap topbar__in" style={{ maxWidth: "none" }}>
          <Link href="/" className="wordmark">
            meuquarto
          </Link>
          <Link href={action.href} className="mq-btn mq-btn--ghost mq-btn--sm">
            {action.label}
          </Link>
        </div>
      </header>

      <div className="auth">
        <aside className="auth__art" aria-hidden="true">
          <p className="t-hand">{note}</p>
          <ul
            className="shelf"
            style={{ gridTemplateColumns: "repeat(3, minmax(0, 1fr))", maxWidth: 520 }}
          >
            {art}
          </ul>
        </aside>

        <main className="auth__form wrap wrap--form">
          <div className="groups">{children}</div>
        </main>
      </div>
    </div>
  );
}
