"use client";

import { useEffect, useRef } from "react";
import { AlertBox } from "@/components/AlertBox";

/**
 * Confirmation before unlinking (FR-16). Native <dialog>: Esc, focus trap and an inert page come from
 * showModal(). Cancel is the default focus, so Enter never unlinks by accident.
 */
export function UnlinkDialog({
  open,
  providerName,
  sending,
  error,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  providerName: string;
  sending: boolean;
  error: string | null;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const element = dialog.current;
    if (!element) return;
    if (open && !element.open) element.showModal();
    if (!open && element.open) element.close();
  }, [open]);

  return (
    <dialog
      ref={dialog}
      className="dialog dialog--fit"
      aria-labelledby={`unlink-${providerName}-h`}
      onCancel={(event) => {
        event.preventDefault();
        if (!sending) onCancel();
      }}
    >
      <div className="dialog__body">
        <h2 id={`unlink-${providerName}-h`} className="t-h2">
          Desvincular {providerName}?
        </h2>
        <p className="t-body">
          Para de ler essa conta. As obras que vieram só dela saem da sua coleção. As que você
          destacou ou escreveu uma nota continuam, e passam a contar como adicionadas à mão.
        </p>
        {error && <AlertBox>{error}</AlertBox>}
        <div className="actions actions--end">
          <button
            type="button"
            className="mq-btn mq-btn--ghost mq-btn--sm"
            onClick={onCancel}
            disabled={sending}
            autoFocus
          >
            Cancelar
          </button>
          <button
            type="button"
            className={`mq-btn mq-btn--danger mq-btn--sm${sending ? " is-loading" : ""}`}
            onClick={onConfirm}
            disabled={sending}
            aria-busy={sending}
          >
            Desvincular
            {sending && <span className="mq-spinner" aria-hidden="true" />}
          </button>
        </div>
      </div>
    </dialog>
  );
}
