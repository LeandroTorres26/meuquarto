import type { ReactNode } from "react";
import { ProfileFooter } from "./ProfileFooter";
import { ProfileTopBar } from "./ProfileTopBar";

/**
 * The whole public profile page: top bar, content and footer inside the owner's theme (`data-theme`), so
 * the bars follow it too. Without a profile (not found, API down) the page keeps the default theme.
 */
export function ProfileShell({
  theme,
  premium = false,
  children,
}: {
  theme?: string;
  premium?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="page" data-theme={theme}>
      <ProfileTopBar premium={premium} />
      {children}
      <ProfileFooter />
    </div>
  );
}
