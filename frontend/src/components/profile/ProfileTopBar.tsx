import { Logo } from "@/components/Logo";
import { SessionActions, type OwnerSlug } from "@/components/SessionActions";

/**
 * `ownerSlug`: the profile the page shows, so the bar can tell the owner from other signed-in viewers.
 * `premium`: the profile wears a bought theme, so the symbol is the one-color version in `ink`.
 */
export function ProfileTopBar({
  ownerSlug,
  premium = false,
}: {
  ownerSlug?: OwnerSlug;
  premium?: boolean;
}) {
  return (
    <header className="topbar">
      <div className="wrap topbar__in">
        <Logo href="/" variant={premium ? "mono" : "color"} />
        <SessionActions ownerSlug={ownerSlug} />
      </div>
    </header>
  );
}
