"use client";

import { useEffect, useState } from "react";

/**
 * The current time in ms, refreshed every `everyMs`. It starts from the instant the server rendered
 * the page (`initial`), so the first client render matches the HTML and nothing flickers on hydration.
 */
export function useNow(initial: number, everyMs = 15_000): number {
  const [now, setNow] = useState(initial);
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), everyMs);
    return () => window.clearInterval(id);
  }, [everyMs]);
  return now;
}
