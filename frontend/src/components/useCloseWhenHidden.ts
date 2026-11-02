"use client";

import { useLayoutEffect, type RefObject } from "react";

/**
 * Closes a modal <dialog> when its page is left. Next.js keeps visited pages in the document, hidden
 * (Activity), instead of unmounting them: a dialog left open there would keep the next page inert and
 * its scroll locked (site.css `html:has(dialog.dialog[open])`). The dialog's own open effect shows it
 * again if the page comes back with it still open.
 */
export function useCloseWhenHidden(dialog: RefObject<HTMLDialogElement | null>) {
  useLayoutEffect(() => {
    const element = dialog.current;
    return () => {
      if (element?.open) element.close();
    };
  }, [dialog]);
}
