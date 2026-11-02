import type { ReactNode } from "react";
import type { OwnerSlug } from "@/components/SessionActions";
import { ProfileFooter } from "./ProfileFooter";
import { ProfileTopBar } from "./ProfileTopBar";

/**
 * The whole public profile page: top bar, content and footer inside the owner's theme (`data-theme`), so
 * the bars follow it too. Without a profile (not found, API down) the page keeps the default theme.
 * `ownerSlug` lets the bars tell the owner from other signed-in viewers.
 */
export function ProfileShell({
  theme,
  premium = false,
  ownerSlug,
  children,
}: {
  theme?: string;
  premium?: boolean;
  ownerSlug?: OwnerSlug;
  children: ReactNode;
}) {
  return (
    <div className="page" data-theme={theme}>
      <ProfileTopBar ownerSlug={ownerSlug} premium={premium} />
      {children}
      <ProfileFooter ownerSlug={ownerSlug} />
    </div>
  );
}
