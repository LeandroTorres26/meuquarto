# 0022. Site-wide theme from a cookie read before the first paint

- Status: Accepted
- Date: 2026-11-02

## Context
Until now the profile theme (#37) only coloured the public profile: `ProfileShell` put `data-theme` on the profile page and every other page stayed on `noite`, and a picked theme only applied after "Salvar perfil". The owner wants the signed-in user's theme on the whole site, applied the moment it is picked. The pages are prerendered (Cache Components, Partial Prerender): reading the session or a cookie in the root layout to choose the theme on the server would make every route dynamic, and setting it from a client effect would flash the default theme on every load.

## Decision
1. The theme id is kept in a plain cookie, `mq_theme` (`Path=/`, one year, `SameSite=Lax`, `Secure` on https). It is not a secret: one of four public names. The API's profile stays the source of truth.
2. The root layout renders `<html data-theme="noite">` and a small inline script in `<head>` (`THEME_BOOT_SCRIPT`, `lib/theme.ts`) that reads the cookie and sets `data-theme` before the first paint, the approach of the Next.js guide "Preventing flash before hydration". Only a known id is applied. `<html>` has `suppressHydrationWarning` for that one attribute. The CSP already allows inline scripts (ADR 0015), so nothing changes there.
3. Who writes the cookie: the theme picker (S6) applies and saves on click (`PUT /api/profile` with the other saved fields unchanged); `ThemeSync`, in the panel navigation, reads the saved theme once per page load (a sign-in on a new browser, a change made on another device); sign-out and account deletion delete it.
4. Pages that belong to someone keep the owner's theme: the profile (S4), the category listing (S18) and the room (S5) put the owner's `data-theme` on their own root, which wins inside it. A locked premium theme can be looked at on the whole site, but it is never kept, and leaving S6 puts the saved theme back.

## Consequences
- No route becomes dynamic and there is no flash of the default theme; the build output is unchanged.
- A signed-out browser keeps the last theme until the cookie expires or the next sign-out on it, which only changes colours on that device.
- `color-scheme` follows the theme (`dia` is light), so native controls and scrollbars match.
- One `GET /api/profile` per page load of the panel.

## Alternatives considered
- `cookies()` in the root layout: every route dynamic, against the Cache Components setup.
- Theme in `localStorage`: same script, but the server could never read it if a future server render needs it.
- Theme in the session answer (`/api/auth/me`): the identity module would have to know about profiles, and the public pages still could not use it before paint.
