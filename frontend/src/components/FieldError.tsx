import { X } from "lucide-react";

/** Inline field error (design system `.status--bad`). The live region is always rendered. */
export function FieldError({ id, children }: { id: string; children?: string }) {
  return (
    <p id={id} aria-live="polite">
      {children && (
        <span className="status status--bad">
          <X className="icon" aria-hidden="true" />
          {children}
        </span>
      )}
    </p>
  );
}
