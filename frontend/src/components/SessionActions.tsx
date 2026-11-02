import Link from "next/link";
import { Suspense } from "react";
import { currentSession } from "@/lib/session";
import { PANEL_HREF, profileHref, viewerState } from "@/lib/viewer-state";

// The session-aware calls to action of the public screens (docs/screens.md, "Session states").
// The pages that use them are prerendered; these components read the session cookie, so each one
// sits inside <Suspense> and is the only dynamic part. The cached HTML holds the placeholder, never
// a button that depends on who is looking.

/** The slug of the profile a page belongs to: known, still to be awaited (route params), or none. */
export type OwnerSlug = string | Promise<string> | undefined;

/**
 * Suspense fallback of every slot: an invisible, inert button of the same size, so the layout does
 * not move when the real buttons arrive and no wrong call to action is shown meanwhile.
 */
export function ActionsPlaceholder({ size = "sm" }: { size?: "sm" | "md" }) {
  return (
    <span
      className={size === "sm" ? "mq-btn mq-btn--ghost mq-btn--sm" : "mq-btn mq-btn--ghost"}
      aria-hidden="true"
      style={{ visibility: "hidden", minWidth: "8ch" }}
    >
      &nbsp;
    </span>
  );
}

async function TopBarButtons({ ownerSlug }: { ownerSlug: OwnerSlug }) {
  const [session, slug] = await Promise.all([currentSession(), ownerSlug]);
  const state = viewerState(slug, session);
  if (state === "visitor") {
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
  if (state === "own") {
    return (
      <>
        <Link href={PANEL_HREF} prefetch={false} className="mq-btn mq-btn--ghost mq-btn--sm">
          Painel
        </Link>
        <Link href={PANEL_HREF} prefetch={false} className="mq-btn mq-btn--primary mq-btn--sm">
          Editar perfil
        </Link>
      </>
    );
  }
  return (
    <>
      {session?.slug ? (
        <Link
          href={profileHref(session.slug)}
          prefetch={false}
          className="mq-btn mq-btn--ghost mq-btn--sm"
        >
          Meu perfil
        </Link>
      ) : null}
      <Link href={PANEL_HREF} prefetch={false} className="mq-btn mq-btn--primary mq-btn--sm">
        Painel
      </Link>
    </>
  );
}

/**
 * Top bar actions of the public screens (S1, S4, S5's neighbours S15, S16, S18). `ownerSlug` is the
 * profile the page shows; leave it out on pages that have no owner.
 */
export function SessionActions({ ownerSlug }: { ownerSlug?: OwnerSlug }) {
  return (
    <nav className="actions" aria-label="Conta">
      <Suspense fallback={<ActionsPlaceholder />}>
        <TopBarButtons ownerSlug={ownerSlug} />
      </Suspense>
    </nav>
  );
}

async function HeroButtons() {
  const session = await currentSession();
  if (!session) {
    return (
      <Link href="/cadastro" prefetch={false} className="mq-btn mq-btn--primary">
        Crie o seu
      </Link>
    );
  }
  return (
    <>
      <Link href={PANEL_HREF} prefetch={false} className="mq-btn mq-btn--primary">
        Ir para o painel
      </Link>
      {session.slug ? (
        <Link
          href={profileHref(session.slug)}
          prefetch={false}
          className="mq-btn mq-btn--secondary"
        >
          Meu perfil
        </Link>
      ) : null}
    </>
  );
}

/** Hero call to action of the landing page (S1). */
export function HeroSessionActions() {
  return (
    <div className="actions">
      <Suspense fallback={<ActionsPlaceholder size="md" />}>
        <HeroButtons />
      </Suspense>
    </div>
  );
}

async function NotFoundButtons({ primary }: { primary: "claim" | "home" }) {
  const session = await currentSession();
  if (session) {
    return (
      <Link href={PANEL_HREF} prefetch={false} className="mq-btn mq-btn--secondary">
        Ir para o painel
      </Link>
    );
  }
  return (
    <Link
      href="/cadastro"
      prefetch={false}
      className={`mq-btn ${primary === "claim" ? "mq-btn--primary" : "mq-btn--secondary"}`}
    >
      {primary === "claim" ? "Usar esse endereço" : "Crie o seu"}
    </Link>
  );
}

/** The sign-up button of S16 ("Usar esse endereço" / "Crie o seu"); "Ir para o painel" when signed in. */
export function NotFoundSessionAction({ primary }: { primary: "claim" | "home" }) {
  return (
    <Suspense fallback={<ActionsPlaceholder size="md" />}>
      <NotFoundButtons primary={primary} />
    </Suspense>
  );
}
