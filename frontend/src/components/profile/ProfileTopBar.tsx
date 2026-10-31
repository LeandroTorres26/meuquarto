import Link from "next/link";
import { Logo } from "@/components/Logo";

export function ProfileTopBar() {
  return (
    <header className="topbar">
      <div className="wrap topbar__in">
        <Logo href="/" />
        <Link href="/cadastro" prefetch={false} className="mq-btn mq-btn--ghost mq-btn--sm">
          Crie o seu
        </Link>
      </div>
    </header>
  );
}
