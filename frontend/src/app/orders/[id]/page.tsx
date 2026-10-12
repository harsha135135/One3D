"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { useSession } from "@/components/Session";
import { ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { ApiError, api, describeError } from "@/lib/api";
import { formatMoney, formatTime, label, shortId } from "@/lib/format";
import type { Order } from "@/lib/types";

/** One of the customer's orders. Payment here is the demo-only simulation. */
export default function OrderPage() {
  const { id } = useParams<{ id: string }>();
  const { session } = useSession();
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .order(id)
      .then(setOrder)
      .catch((e: unknown) =>
        setError(e instanceof ApiError && e.status === 404 ? "This order was not found." : describeError(e)),
      );
  }, [id]);

  async function pay() {
    setBusy(true);
    setMessage(null);
    try {
      setOrder(await api.simulatePayment(id));
      setMessage("Payment simulated. Your order is confirmed.");
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  }

  const demoPayment = session.status === "signed-in" && session.me.demoPaymentEnabled;

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
            <dt>Confirmed</dt>
            <dd>{formatTime(order.confirmedAt)}</dd>
            <dt>Ready for handoff</dt>
            <dd>{formatTime(order.readyForHandoffAt)}</dd>
          </dl>
          {order.status === "AWAITING_PAYMENT" && demoPayment && (
            <div className="actions" style={{ marginTop: 16 }}>
              <button type="button" className="button" onClick={pay} disabled={busy}>
                {busy ? "Confirming…" : "Pay (demo: no money is taken)"}
              </button>
            </div>
          )}
          {order.status === "AWAITING_PAYMENT" && !demoPayment && (
            <p className="muted">Payment is not available in this environment yet.</p>
          )}
        </section>
      )}
      <p role="status" aria-live="polite">
        {message}
      </p>
    </>
  );
}
