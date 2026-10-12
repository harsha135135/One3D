"use client";

import { useCallback, useEffect, useState } from "react";
import { Empty, ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { api, describeError } from "@/lib/api";
import { formatMoney, formatTime, label, shortId } from "@/lib/format";
import type { PaymentRecord } from "@/lib/types";
import { OpsGuard } from "../OpsGuard";

const REASON_TEXT: Record<NonNullable<PaymentRecord["reviewReason"]>, string> = {
  UNKNOWN_PROVIDER_ORDER: "Not for any of our checkouts",
  AMOUNT_MISMATCH: "Amount or currency differs from the order",
  LATE_PAYMENT: "Started after the checkout closed",
  SECOND_PAYMENT: "The order was already paid",
};

/** Payments that need a manual check. They never changed an order; refunds happen in the Razorpay dashboard. */
export default function PaymentReviewsPage() {
  return (
    <OpsGuard>
      <Reviews />
    </OpsGuard>
  );
}

function Reviews() {
  const [reviews, setReviews] = useState<PaymentRecord[] | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.paymentReviews().then(setReviews).catch((e: unknown) => setError(describeError(e)));
  }, []);

  useEffect(load, [load]);

  async function resolve(payment: PaymentRecord) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      setReviews(await api.resolvePayment(payment.id, notes[payment.id] ?? ""));
      setMessage(`Payment ${payment.providerPaymentId} marked as checked.`);
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1>Payments to check</h1>
      <p className="muted">
        These payments were captured in Razorpay test mode but did not confirm an order. Decide what to do (for
        example refund in the Razorpay dashboard), then record it here.
      </p>
      {error && <ErrorMessage message={error} />}
      <p role="status" aria-live="polite">
        {message}
      </p>
      {!reviews && !error && <Loading what="payments" />}
      {reviews && reviews.length === 0 && <Empty>No payments need a check.</Empty>}
      {reviews && reviews.length > 0 && (
        <ul className="cards">
          {reviews.map((payment) => (
            <li key={payment.id} className="card">
              <h2>
                {formatMoney({ amountMinor: payment.amountMinor, currency: payment.currency })}{" "}
                <StatusBadge status={payment.outcome} label={label(payment.outcome)} />
              </h2>
              <dl className="facts">
                <dt>Why</dt>
                <dd>{payment.reviewReason ? REASON_TEXT[payment.reviewReason] : "—"}</dd>
                <dt>Payment</dt>
                <dd className="mono">{payment.providerPaymentId}</dd>
                <dt>Razorpay order</dt>
                <dd className="mono">{payment.providerOrderId}</dd>
                <dt>Our order</dt>
                <dd>{shortId(payment.orderId)}</dd>
                <dt>Reported by</dt>
                <dd>{label(payment.source)}</dd>
                <dt>Recorded</dt>
                <dd>{formatTime(payment.recordedAt)}</dd>
              </dl>
              <div className="form" style={{ marginTop: 12 }}>
                <label>
                  What was decided
                  <textarea
                    rows={2}
                    maxLength={1000}
                    value={notes[payment.id] ?? ""}
                    onChange={(e) => setNotes({ ...notes, [payment.id]: e.target.value })}
                  />
                </label>
                <div className="actions">
                  <button
                    type="button"
                    className="button"
                    disabled={busy || !(notes[payment.id] ?? "").trim()}
                    onClick={() => resolve(payment)}
                  >
                    Mark as checked
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
