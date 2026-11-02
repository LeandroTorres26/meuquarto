"use client";

import { useSyncExternalStore } from "react";

// The open item of the room panel lives in the URL (`/{slug}/quarto?item={ref}`), like S17 on the
// profile: a shared link opens it, Back and Esc close it. It is read with useSyncExternalStore
// (popstate plus our own event after pushState/replaceState) and written with the History API, so
// opening and closing never re-fetches the page.

const URL_EVENT = "mq:urlchange";

/** True when this page pushed the current history entry (Back closes it); false for a shared link. */
let pushed = false;

function onPop() {
  pushed = false;
}

function subscribe(onChange: () => void) {
  window.addEventListener("popstate", onPop);
  window.addEventListener("popstate", onChange);
  window.addEventListener(URL_EVENT, onChange);
  return () => {
    window.removeEventListener("popstate", onPop);
    window.removeEventListener("popstate", onChange);
    window.removeEventListener(URL_EVENT, onChange);
  };
}
export const readItemParam = (): string | null =>
  new URLSearchParams(window.location.search).get("item");

/** The raw `?item=` value (null when absent), live. */
export function useItemParam(): string | null {
  return useSyncExternalStore(subscribe, readItemParam, () => null);
}

function notify() {
  window.dispatchEvent(new Event(URL_EVENT));
}

/** Opens an item: a new history entry, so Back closes it. */
export function pushItem(ref: string) {
  const url = new URL(window.location.href);
  url.searchParams.set("item", ref);
  window.history.pushState(null, "", url.pathname + url.search);
  pushed = true;
  notify();
}

/** Drops `?item=` without adding a history entry. */
export function dropItem() {
  const url = new URL(window.location.href);
  url.searchParams.delete("item");
  window.history.replaceState(null, "", url.pathname + url.search);
  pushed = false;
  notify();
}

/** Closes the open item the way the visitor opened it: Back for a pushed entry, a rewrite for a shared link. */
export function closeItem() {
  if (pushed) window.history.back();
  else dropItem();
}
