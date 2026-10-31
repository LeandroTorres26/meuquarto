"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  acknowledgeCookieNotice,
  browserStorage,
  shouldShowCookieNotice,
} from "@/lib/cookie-notice";

/**
 * Cookie notice of S1. Essential cookies only, so the only choice is "Entendi". Hidden until the
 * browser has been asked (server and first client render match, no flash for returning visitors).
 */
export function CookieNotice({ returnFocusTo }: { returnFocusTo: string }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    // The decision depends on browser-only state, so it can only be made after hydration.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setVisible(shouldShowCookieNotice(browserStorage(), window.location.search));
  }, []);

  if (!visible) return null;

  function dismiss() {
    acknowledgeCookieNotice(browserStorage());
    setVisible(false);
    // The button is about to disappear: send focus back to the top instead of losing it.
    document.getElementById(returnFocusTo)?.focus();
  }

  return (
    <div className="cookie region" role="region" aria-label="Aviso de cookies">
      <p className="t-sm" style={{ color: "var(--ink)" }}>
        Usamos só os cookies necessários para manter você conectado.{" "}
        <Link href="/termos" prefetch={false}>
          Saiba mais
        </Link>
      </p>
      <button
        type="button"
        className="mq-btn mq-btn--secondary mq-btn--sm self-start"
        onClick={dismiss}
      >
        Entendi
      </button>
    </div>
  );
}
