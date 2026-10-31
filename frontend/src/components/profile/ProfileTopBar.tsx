import Link from "next/link";

export function ProfileTopBar() {
  return (
    <header className="topbar">
      <div className="wrap topbar__in">
        <Link href="/" className="wordmark">
          meuquarto
        </Link>
        <Link href="/cadastro" className="mq-btn mq-btn--ghost mq-btn--sm">
          Crie o seu
        </Link>
      </div>
    </header>
  );
}
