import type { Money } from "./types";

export function formatMoney(money: Money): string {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: money.currency }).format(money.amountMinor / 100);
}

export function formatTime(iso: string | null): string {
  if (!iso) {
    return "—";
  }
  return new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}

/** The first 8 characters of an id, for display only. */
export function shortId(id: string | null): string {
  return id ? id.slice(0, 8) : "—";
}

/** "AWAITING_PAYMENT" → "Awaiting payment". */
export function label(status: string | null): string {
  if (!status) {
    return "—";
  }
  const words = status.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}
