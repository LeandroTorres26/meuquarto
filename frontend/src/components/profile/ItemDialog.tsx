"use client";

import { X } from "lucide-react";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { ApiError, getJson } from "@/lib/api-client";
import {
  CATEGORY_META,
  parseItemRef,
  type Category,
  type PublicItemDetail,
} from "@/lib/profile-view";
import { ItemDetailBody, ItemDetailError, ItemDetailGone, ItemDetailLoading } from "./ItemDetail";

// S17. The open item lives in the URL (`?item={ref}`), so a shared link opens the dialog on first
// load. The URL is read with useSyncExternalStore (popstate plus our own event after pushState) and
// written with history.pushState, which Next.js 16 keeps in sync without a server round trip: the
// profile is not fetched again when a dialog opens or closes. Clicks on the server-rendered cards are
// caught by one delegated listener (`a[data-item-ref]`).

const URL_EVENT = "mq:urlchange";

function subscribe(onChange: () => void) {
  window.addEventListener("popstate", onChange);
  window.addEventListener(URL_EVENT, onChange);
  return () => {
    window.removeEventListener("popstate", onChange);
    window.removeEventListener(URL_EVENT, onChange);
  };
}
const currentItem = () => new URLSearchParams(window.location.search).get("item");
const noItemOnServer = () => null;

type Loaded =
  | { ref: string; status: "ready"; detail: PublicItemDetail }
  | { ref: string; status: "gone" | "error" };

export function ItemDialog({ slug, ownerName }: { slug: string; ownerName: string }) {
  const raw = useSyncExternalStore(subscribe, currentItem, noItemOnServer);
  const ref = parseItemRef(raw);
  const open = raw !== null;

  const dialog = useRef<HTMLDialogElement>(null);
  const opener = useRef<HTMLElement | null>(null);
  /** True when this page pushed the history entry itself: closing then goes back instead of rewriting. */
  const pushed = useRef(false);
  const [hint, setHint] = useState<Category | null>(null);
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [attempt, setAttempt] = useState(0);

  // A closed dialog forgets its content, so the next opening (or a retry after an error) starts clean.
  const [wasOpen, setWasOpen] = useState(open);
  if (wasOpen !== open) {
    setWasOpen(open);
    if (!open) setLoaded(null);
  }

  useEffect(() => {
    if (!ref) return;
    let cancelled = false;
    getJson<PublicItemDetail>(
      `/api/public/profiles/${encodeURIComponent(slug)}/items/${encodeURIComponent(ref)}`,
    )
      .then((detail) => {
        if (!cancelled) setLoaded({ ref, status: "ready", detail });
      })
      .catch((error: unknown) => {
        const status = error instanceof ApiError && error.status === 404 ? "gone" : "error";
        if (!cancelled) setLoaded({ ref, status });
      });
    return () => {
      cancelled = true;
    };
  }, [slug, ref, attempt]);

  // Native <dialog>: showModal gives the backdrop, the focus trap and an inert page behind it.
  useEffect(() => {
    const element = dialog.current;
    if (!element) return;
    if (open && !element.open) {
      element.showModal();
    } else if (!open && element.open) {
      element.close();
      if (opener.current?.isConnected) opener.current.focus();
      opener.current = null;
    }
  }, [open]);

  useEffect(() => {
    function onClick(event: MouseEvent) {
      if (event.defaultPrevented || event.button !== 0) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const target = event.target instanceof Element ? event.target : null;
      const link = target?.closest<HTMLAnchorElement>("a[data-item-ref]");
      const itemRef = parseItemRef(link?.dataset.itemRef);
      if (!link || !itemRef) return;
      event.preventDefault();
      opener.current = link;
      const category = link.dataset.itemCategory ?? "";
      setHint(category in CATEGORY_META ? (category as Category) : null);
      const url = new URL(window.location.href);
      url.searchParams.set("item", itemRef);
      window.history.pushState(null, "", url.pathname + url.search);
      pushed.current = true;
      window.dispatchEvent(new Event(URL_EVENT));
    }
    function onPopState() {
      pushed.current = false;
    }
    document.addEventListener("click", onClick);
    window.addEventListener("popstate", onPopState);
    return () => {
      document.removeEventListener("click", onClick);
      window.removeEventListener("popstate", onPopState);
    };
  }, []);

  const close = useCallback(() => {
    if (pushed.current) {
      window.history.back(); // opened by a click: going back closes it
      return;
    }
    const url = new URL(window.location.href); // opened from a shared link: drop the parameter
    url.searchParams.delete("item");
    window.history.replaceState(null, "", url.pathname + url.search);
    window.dispatchEvent(new Event(URL_EVENT));
  }, []);

  const retry = useCallback(() => {
    setLoaded(null);
    setAttempt((n) => n + 1);
  }, []);

  const view = !open ? null : !ref ? "gone" : loaded?.ref === ref ? loaded.status : "loading";

  return (
    <dialog
      ref={dialog}
      className="dialog dialog--fit"
      aria-labelledby="item-h"
      onCancel={(event) => {
        event.preventDefault(); // Esc: close through the URL like every other way out
        close();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) close(); // the dimmed backdrop
      }}
    >
      {view ? (
        <>
          <button type="button" className="ib dialog__close" aria-label="Fechar" onClick={close}>
            <X className="icon" aria-hidden="true" />
          </button>
          <div className="dialog__body detail-wrap">
            {view === "ready" && loaded?.status === "ready" ? (
              <ItemDetailBody detail={loaded.detail} ownerName={ownerName} />
            ) : view === "loading" ? (
              <ItemDetailLoading category={hint} />
            ) : view === "error" ? (
              <ItemDetailError onRetry={retry} />
            ) : (
              <ItemDetailGone ownerName={ownerName} onClose={close} />
            )}
          </div>
        </>
      ) : null}
    </dialog>
  );
}
