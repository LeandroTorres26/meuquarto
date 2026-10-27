import { TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";

/** Form-level error (design system `.alert`). Announced when it appears. */
export function AlertBox({ children }: { children: ReactNode }) {
  return (
    <div className="alert" role="alert">
      <TriangleAlert className="icon" aria-hidden="true" />
      <span>{children}</span>
    </div>
  );
}
