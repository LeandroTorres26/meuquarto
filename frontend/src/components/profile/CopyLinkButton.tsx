"use client";

import { Check, Copy, Share2 } from "lucide-react";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";

type State = "idle" | "copied" | "failed";

const noSubscription = () => () => {};
const deviceCanShare = () => typeof navigator.share === "function";
const serverCannotShare = () => false;

/**
 * The one share action of the profile (ADR 0023). Where the device has a share sheet it reads
 * "Compartilhar" and opens it; everywhere else it reads "Copiar link" and copies the address (without any
 * ?item). The label is decided after hydration: the server and the first render always say "Copiar
 * link", so the HTML never disagrees with what the browser builds. Says what happened for screen readers too.
 */
export function CopyLinkButton({ slug }: { slug: string }) {
  const [state, setState] = useState<State>("idle");
  const canShare = useSyncExternalStore(noSubscription, deviceCanShare, serverCannotShare);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);

  async function act() {
    const url = `${window.location.origin}/${encodeURIComponent(slug)}`;
    clearTimeout(timer.current);
    try {
      if (canShare) {
        await navigator.share({ url, title: document.title });
        return;
      }
      await navigator.clipboard.writeText(url);
      setState("copied");
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return; // the visitor closed the sheet
      setState("failed");
    }
    timer.current = setTimeout(() => setState("idle"), 2500);
  }

  return (
    <>
      <button type="button" className="mq-btn mq-btn--primary" onClick={act}>
        {state === "copied" ? (
          <Check className="icon" aria-hidden="true" />
        ) : canShare ? (
          <Share2 className="icon" aria-hidden="true" />
        ) : (
          <Copy className="icon" aria-hidden="true" />
        )}
        {state === "copied"
          ? "Link copiado"
          : state === "failed"
            ? canShare
              ? "Não foi possível compartilhar"
              : "Não foi possível copiar"
            : canShare
              ? "Compartilhar"
              : "Copiar link"}
      </button>
      <span className="sr" role="status">
        {state === "copied"
          ? "Link copiado"
          : state === "failed"
            ? canShare
              ? "Não foi possível compartilhar o link"
              : "Não foi possível copiar o link"
            : ""}
      </span>
    </>
  );
}
