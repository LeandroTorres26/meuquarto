"use client";

import { Check, Copy } from "lucide-react";
import { useEffect, useRef, useState } from "react";

type State = "idle" | "copied" | "failed";

/** "Copiar link": copies the profile address (without any ?item) and says so for screen readers too. */
export function CopyLinkButton({ slug }: { slug: string }) {
  const [state, setState] = useState<State>("idle");
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);

  async function copy() {
    clearTimeout(timer.current);
    try {
      await navigator.clipboard.writeText(`${window.location.origin}/${encodeURIComponent(slug)}`);
      setState("copied");
    } catch {
      setState("failed");
    }
    timer.current = setTimeout(() => setState("idle"), 2500);
  }

  return (
    <>
      <button type="button" className="mq-btn mq-btn--primary" onClick={copy}>
        {state === "copied" ? (
          <Check className="icon" aria-hidden="true" />
        ) : (
          <Copy className="icon" aria-hidden="true" />
        )}
        {state === "copied"
          ? "Link copiado"
          : state === "failed"
            ? "Não foi possível copiar"
            : "Copiar link"}
      </button>
      <span className="sr" role="status">
        {state === "copied"
          ? "Link copiado"
          : state === "failed"
            ? "Não foi possível copiar o link"
            : ""}
      </span>
    </>
  );
}
