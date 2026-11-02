import { Logo } from "@/components/Logo";
import { SessionActions, type OwnerSlug } from "@/components/SessionActions";

/** `ownerSlug`: the profile the page shows, so the bar can tell the owner from other signed-in viewers. */
export function ProfileTopBar({ ownerSlug }: { ownerSlug?: OwnerSlug }) {
  return (
    <header className="topbar">
      <div className="wrap topbar__in">
        <Logo href="/" />
        <SessionActions ownerSlug={ownerSlug} />
      </div>
    </header>
  );
}
