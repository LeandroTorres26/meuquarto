import Link from "next/link";
import { cache } from "react";
import { getSession } from "@/lib/session";

// One session lookup per request, shared by the top bar and the hero.
const currentSession = cache(getSession);

const PANEL = "/painel/conta";

/** Top bar actions: "Entrar" and "Crie o seu", or "Ir para o painel" when signed in. Dynamic (cookies). */
export async function TopBarActions() {
  const session = await currentSession();
  return session ? (
    <Link href={PANEL} prefetch={false} className="mq-btn mq-btn--primary mq-btn--sm">
      Ir para o painel
    </Link>
  ) : (
    <SignedOutTopBarActions />
  );
}

/** Also the Suspense fallback: what the static shell shows until the session is known. */
export function SignedOutTopBarActions() {
  return (
    <>
      <Link href="/entrar" prefetch={false} className="mq-btn mq-btn--ghost mq-btn--sm">
        Entrar
      </Link>
      <Link href="/cadastro" prefetch={false} className="mq-btn mq-btn--primary mq-btn--sm">
        Crie o seu
      </Link>
    </>
  );
}

/** Hero call to action. Dynamic (cookies). */
export async function HeroActions() {
  const session = await currentSession();
  return session ? (
    <Link href={PANEL} prefetch={false} className="mq-btn mq-btn--primary">
      Ir para o painel
    </Link>
  ) : (
    <SignedOutHeroActions />
  );
}

export function SignedOutHeroActions() {
  return (
    <Link href="/cadastro" prefetch={false} className="mq-btn mq-btn--primary">
      Crie o seu
    </Link>
  );
}
