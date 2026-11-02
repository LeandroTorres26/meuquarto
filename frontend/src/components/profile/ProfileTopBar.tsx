import Link from "next/link";
import { Logo } from "@/components/Logo";

/** `premium`: the profile wears a bought theme, so the symbol is the one-color version in `ink`. */
export function ProfileTopBar({ premium = false }: { premium?: boolean }) {
  return (
    <header className="topbar">
      <div className="wrap topbar__in">
        <Logo href="/" variant={premium ? "mono" : "color"} />
        <Link href="/cadastro" prefetch={false} className="mq-btn mq-btn--ghost mq-btn--sm">
          Crie o seu
        </Link>
      </div>
    </header>
  );
}
