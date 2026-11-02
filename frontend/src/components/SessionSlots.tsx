import Link from "next/link";
import { Suspense, type ReactNode } from "react";
import { ActionsPlaceholder, type OwnerSlug } from "@/components/SessionActions";
import { currentSession } from "@/lib/session";
import { HIGHLIGHTS_HREF, viewerState, type ViewerState } from "@/lib/viewer-state";

// Small session-aware pieces of the public screens, each one a dynamic hole inside <Suspense>
// (see SessionActions.tsx). Rules: docs/screens.md, "Session states".

async function WhenViewer({
  ownerSlug,
  states,
  children,
}: {
  ownerSlug: OwnerSlug;
  states: readonly ViewerState[];
  children: ReactNode;
}) {
  const [session, slug] = await Promise.all([currentSession(), ownerSlug]);
  return states.includes(viewerState(slug, session)) ? <>{children}</> : null;
}

/** Renders `children` only for visitors with no session (the "Crie o seu" invitations). */
export function ForVisitors({
  ownerSlug,
  fallback,
  children,
}: {
  ownerSlug?: OwnerSlug;
  /** What the static shell holds until the session is known. Nothing interactive. */
  fallback: ReactNode;
  children: ReactNode;
}) {
  return (
    <Suspense fallback={fallback}>
      <WhenViewer ownerSlug={ownerSlug} states={["visitor"]}>
        {children}
      </WhenViewer>
    </Suspense>
  );
}

/** Renders `children` only for the signed-in owner of the profile on screen. */
export function ForOwner({ ownerSlug, children }: { ownerSlug: OwnerSlug; children: ReactNode }) {
  return (
    <Suspense fallback={null}>
      <WhenViewer ownerSlug={ownerSlug} states={["own"]}>
        {children}
      </WhenViewer>
    </Suspense>
  );
}

/** Footer invitation of the profile screens: "Crie o seu" for visitors only. */
export function FooterInvite({ ownerSlug }: { ownerSlug?: OwnerSlug }) {
  return (
    <ForVisitors
      ownerSlug={ownerSlug}
      fallback={
        <span aria-hidden="true" style={{ display: "inline-block", minWidth: "7ch" }}>
          &nbsp;
        </span>
      }
    >
      <Link href="/cadastro" prefetch={false}>
        Crie o seu
      </Link>
    </ForVisitors>
  );
}

/**
 * The room panel's "Crie o seu quarto", for visitors only. The room has its own share button for
 * the owner, so signed-in viewers see nothing here. A server element passed to the client scene.
 */
export function RoomInvite({ ownerSlug }: { ownerSlug: OwnerSlug }) {
  return (
    <ForVisitors ownerSlug={ownerSlug} fallback={<ActionsPlaceholder size="md" />}>
      <Link
        href="/cadastro"
        prefetch={false}
        className="mq-btn mq-btn--primary self-start room-cta"
      >
        Crie o seu quarto
      </Link>
    </ForVisitors>
  );
}

/** Link to the highlights editor, shown in the empty states of the owner's own profile only. */
export function OwnerHighlightsLink({ ownerSlug }: { ownerSlug: OwnerSlug }) {
  return (
    <ForOwner ownerSlug={ownerSlug}>
      <p className="t-sm">
        <Link href={HIGHLIGHTS_HREF}>Escolher destaques no painel</Link>
      </p>
    </ForOwner>
  );
}
