import type { ReactNode } from "react";

/** Loading, error and empty states shared by all pages. */
export function Loading({ what }: { what: string }) {
  return (
    <p className="state" role="status" aria-live="polite">
      Loading {what}…
    </p>
  );
}

export function ErrorMessage({ message }: { message: string }) {
  return (
    <p className="state error" role="alert">
      <span aria-hidden="true">⚠ </span>
      {message}
    </p>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="state empty">{children}</p>;
}

/** A status name shown with text, not colour alone. */
export function StatusBadge({ status, label }: { status: string; label: string }) {
  return (
    <span className={`badge badge-${status.toLowerCase().replaceAll("_", "-")}`} data-status={status}>
      {label}
    </span>
  );
}
