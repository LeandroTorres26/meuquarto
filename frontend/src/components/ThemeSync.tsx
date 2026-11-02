"use client";

import { useEffect } from "react";
import { getOwnProfile } from "@/lib/api-client";
import { applyTheme, setThemeSynced, themeSynced } from "@/lib/theme";

/**
 * Signed-in screens: makes the site theme follow the saved profile theme, for a sign-in on a new
 * browser or a theme changed on another device. Once per page load is enough (the picker in S6 applies
 * a change itself). Renders nothing; a failed read keeps the current theme and tries again next screen.
 */
export function ThemeSync() {
  useEffect(() => {
    if (themeSynced()) return;
    setThemeSynced(true);
    getOwnProfile()
      .then((profile) => applyTheme(profile.theme))
      .catch(() => setThemeSynced(false));
  }, []);
  return null;
}
