// Cookie notice of the landing page (docs/design/telas/README.md, S1). We only set the cookies the
// session needs, so there is nothing to choose: "Entendi" closes the notice and the browser
// remembers it. The choice lives in localStorage, which can be missing or throw (private window,
// blocked site data), so every access is guarded and the notice then simply shows again.

export const COOKIE_NOTICE_KEY = "mq-cookies";
const ACKNOWLEDGED = "ok";

type ReadableStorage = Pick<Storage, "getItem">;
type WritableStorage = Pick<Storage, "setItem">;

/** `?cookies=1` shows the notice again (design review aid, as in the prototype). */
export function forcesCookieNotice(search: string): boolean {
  return new URLSearchParams(search).get("cookies") === "1";
}

export function shouldShowCookieNotice(storage: ReadableStorage | null, search = ""): boolean {
  if (forcesCookieNotice(search)) return true;
  try {
    return storage?.getItem(COOKIE_NOTICE_KEY) !== ACKNOWLEDGED;
  } catch {
    return true;
  }
}

export function acknowledgeCookieNotice(storage: WritableStorage | null): void {
  try {
    storage?.setItem(COOKIE_NOTICE_KEY, ACKNOWLEDGED);
  } catch {
    // Storage unavailable: the notice comes back on the next visit, which is harmless.
  }
}

/** `window.localStorage`, or null when even touching it throws. */
export function browserStorage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}
