// Site theme: the signed-in user's profile theme colours every page, not only the public profile.
// The id is kept in a plain cookie (not a secret: one of four public names) so a small script in the
// root layout's <head> sets <html data-theme> before the first paint, without making any page dynamic
// (Next.js guide "Preventing flash before hydration"). The server's profile stays the source of truth.

export const THEME_IDS = ["noite", "dia", "locadora", "estufa"] as const;
export type ThemeId = (typeof THEME_IDS)[number];
export const DEFAULT_THEME: ThemeId = "noite";
export const THEME_COOKIE = "mq_theme";

const ONE_YEAR_SECONDS = 365 * 24 * 60 * 60;

export const isThemeId = (value: unknown): value is ThemeId =>
  typeof value === "string" && (THEME_IDS as readonly string[]).includes(value);

/** The theme kept in a cookie string (`document.cookie`); the default when it is missing or unknown. */
export function themeFromCookies(cookies: string | null | undefined): ThemeId {
  for (const part of (cookies ?? "").split(";")) {
    const [name, value] = part.trim().split("=");
    if (name === THEME_COOKIE) return isThemeId(value) ? value : DEFAULT_THEME;
  }
  return DEFAULT_THEME;
}

/** The Set-Cookie value that keeps `theme` for a year (`null` deletes it). `Secure` on https. */
export function themeCookie(theme: ThemeId | null, secure: boolean): string {
  const value = theme ?? "";
  const maxAge = theme ? ONE_YEAR_SECONDS : 0;
  return `${THEME_COOKIE}=${value}; Path=/; Max-Age=${maxAge}; SameSite=Lax${secure ? "; Secure" : ""}`;
}

/**
 * Runs in <head> before the first paint. The pattern matches only a whole known id (the ids are fixed
 * lowercase words), so a tampered cookie can never put anything else in the attribute.
 */
export const THEME_BOOT_SCRIPT = `(function(){try{var m=document.cookie.match(/(?:^|; )${THEME_COOKIE}=(${THEME_IDS.join("|")})(?:;|$)/);if(m)document.documentElement.setAttribute("data-theme",m[1])}catch(e){}})()`;

/** Colours the whole site with `theme` right away, without keeping it (a locked theme being looked at). */
export function showTheme(theme: string) {
  document.documentElement.setAttribute("data-theme", isThemeId(theme) ? theme : DEFAULT_THEME);
}

/** Colours the whole site with `theme` and keeps it for the next visits on this browser. */
export function applyTheme(theme: string) {
  const id = isThemeId(theme) ? theme : DEFAULT_THEME;
  showTheme(id);
  document.cookie = themeCookie(id, window.location.protocol === "https:");
}

// True once this page load has read the saved theme (ThemeSync). Cleared on sign-out, so the next
// account signing in without a reload gets its own theme.
let synced = false;
export const themeSynced = () => synced;
export const setThemeSynced = (value: boolean) => {
  synced = value;
};

/** Back to the default theme and nothing kept (sign out, account deleted). */
export function forgetTheme() {
  synced = false;
  showTheme(DEFAULT_THEME);
  document.cookie = themeCookie(null, window.location.protocol === "https:");
}
