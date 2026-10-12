"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { useSession } from "@/components/Session";
import { ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { ApiError, api, describeError } from "@/lib/api";
import { formatMoney, formatTime, label, shortId } from "@/lib/format";
import { openRazorpay } from "@/lib/razorpay";
import type { Order, TimelineEntry } from "@/lib/types";

const TIMELINE_TEXT: Record<TimelineEntry["kind"], string> = {
  PLACED: "Order placed",
  CONFIRMED: "Confirmed",
  PAYMENT_UNDER_REVIEW: "Payment being checked",
  RELEASED_TO_PRODUCTION: "In production",
  QC_PASSED: "Passed quality check",
  READY_FOR_HANDOFF: "Ready for handoff",
  HANDED_OVER: "Handed over",
};

/** After Checkout reports success, ask the backend a few times; a webhook may also arrive meanwhile. */
const CHECKS = 5;
const CHECK_INTERVAL_MS = 2000;

/** One of the customer's orders: payment (Razorpay test mode or the demo shortcut) and its timeline. */
export default function OrderPage() {
  const { id } = useParams<{ id: string }>();
  const { session } = useSession();
  const [order, setOrder] = useState<Order | null>(null);
  const [timeline, setTimeline] = useState<TimelineEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api
      .order(id)
      .then(setOrder)
      .catch((e: unknown) =>
        setError(e instanceof ApiError && e.status === 404 ? "This order was not found." : describeError(e)),
      );
    api.timeline(id).then(setTimeline).catch(() => setTimeline([]));
  }, [id]);

  useEffect(load, [load]);

  async function payDemo() {
    setBusy(true);
    setMessage(null);
    try {
      setOrder(await api.simulatePayment(id));
      setMessage("Payment simulated. Your order is confirmed.");
      load();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  }

  async function payWithRazorpay() {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const checkout = await api.openCheckout(id);
      const email = session.status === "signed-in" ? session.me.email : null;
      const result = await openRazorpay(checkout, email);
      if (result === "closed") {
        setMessage("Payment window closed. You can try again.");
        return;
      }
      setMessage("Checking your payment with Razorpay…");
      await checkPayment();
    } catch (e) {
      setError(e instanceof Error && !(e instanceof ApiError) ? e.message : describeError(e));
    } finally {
      setBusy(false);
    }
  }

  async function checkPayment() {
    for (let i = 0; i < CHECKS; i++) {
      const result = await api.paymentCheck(id);
      if (result.orderStatus !== "AWAITING_PAYMENT") {
        setMessage("Payment received. Your order is confirmed.");
        load();
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, CHECK_INTERVAL_MS));
    }
    setMessage("We have not received a confirmed payment yet. If you paid, check again in a minute.");
    load();
  }

  const mode = session.status === "signed-in" ? session.me.paymentMode : "NONE";
  const windowOpen = order ? new Date(order.paymentWindowEndsAt).getTime() > Date.now() : false;

  return (
    <>
      <p>
        <Link href="/orders">← My orders</Link>
      </p>
      <h1>Order</h1>
      {error && <ErrorMessage message={error} />}
      {!order && !error && <Loading what="the order" />}
      {order && (
        <section className="card" aria-labelledby="order-heading">
          <h2 id="order-heading">{order.line.productName}</h2>
          <dl className="facts">
            <dt>Status</dt>
            <dd>
              <StatusBadge status={order.status} label={label(order.status)} />
            </dd>
            <dt>{order.line.source === "CUSTOM_QUOTE" ? "Requirements" : "Configuration"}</dt>
            <dd>{order.line.configurationDescription}</dd>
            {order.line.source === "CUSTOM_QUOTE" && (
              <>
                <dt>From quote</dt>
                <dd>{shortId(order.line.quoteId)}</dd>
              </>
            )}
            <dt>Quantity</dt>
            <dd>{order.line.quantity}</dd>
            <dt>Total</dt>
            <dd>{formatMoney(order.total)}</dd>
            <dt>Placed</dt>
            <dd>{formatTime(order.createdAt)}</dd>
            {order.status === "AWAITING_PAYMENT" && (
              <>
                <dt>Pay by</dt>
                <dd>{formatTime(order.paymentWindowEndsAt)}</dd>
              </>
            )}
          </dl>
          {order.status === "AWAITING_PAYMENT" && mode === "RAZORPAY_TEST" && windowOpen && (
            <div className="actions" style={{ marginTop: 16 }}>
              <button type="button" className="button" onClick={payWithRazorpay} disabled={busy}>
                {busy ? "Please wait…" : `Pay ${formatMoney(order.total)} with Razorpay (test mode)`}
              </button>
              <button type="button" className="button secondary" onClick={() => void checkPayment()} disabled={busy}>
                I already paid: check again
              </button>
            </div>
          )}
          {order.status === "AWAITING_PAYMENT" && mode === "RAZORPAY_TEST" && (
            <p className="muted">
              Test mode: use Razorpay&apos;s test cards or test UPI. No real money is taken.
            </p>
          )}
          {order.status === "AWAITING_PAYMENT" && mode === "RAZORPAY_TEST" && !windowOpen && (
            <p className="notice">The payment window for this order has closed.</p>
          )}
          {order.status === "AWAITING_PAYMENT" && mode === "DEMO" && (
            <div className="actions" style={{ marginTop: 16 }}>
              <button type="button" className="button" onClick={payDemo} disabled={busy}>
                {busy ? "Confirming…" : "Pay (demo: no money is taken)"}
              </button>
            </div>
          )}
          {order.status === "AWAITING_PAYMENT" && mode === "NONE" && (
            <p className="muted">Payment is not available in this environment.</p>
          )}
        </section>
      )}
      <p role="status" aria-live="polite">
        {message}
      </p>
      {order && timeline.length > 0 && (
        <section className="section" aria-labelledby="timeline-heading">
          <h2 id="timeline-heading">Progress</h2>
          <ol className="timeline">
            {timeline.map((entry) => (
              <li key={`${entry.kind}-${entry.occurredAt}`}>
                <strong>{TIMELINE_TEXT[entry.kind]}</strong>{" "}
                <span className="muted">{formatTime(entry.occurredAt)}</span>
                {entry.detail && <div className="muted">{entry.detail}</div>}
              </li>
            ))}
          </ol>
        </section>
      )}
    </>
  );
}
