"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useSession } from "@/components/Session";
import { Empty, ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { SIGN_IN_URL, api, describeError } from "@/lib/api";
import { formatMoney, formatTime, label, shortId } from "@/lib/format";
import type { Order, Page } from "@/lib/types";

/** The signed-in customer's own orders, newest first. */
export default function MyOrdersPage() {
  const { session } = useSession();
  const [page, setPage] = useState(0);
  const [orders, setOrders] = useState<Page<Order> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (session.status !== "signed-in") {
      return;
    }
    api.myOrders(page).then(setOrders).catch((e: unknown) => setError(describeError(e)));
  }, [session.status, page]);

  if (session.status === "signed-out") {
    return (
      <>
        <h1>My orders</h1>
        <p>
          <a className="button" href={SIGN_IN_URL}>
            Sign in to see your orders
          </a>
        </p>
      </>
    );
  }

  return (
    <>
      <h1>My orders</h1>
      {error && <ErrorMessage message={error} />}
      {!orders && !error && <Loading what="your orders" />}
      {orders && orders.items.length === 0 && (
        <Empty>
          You have no orders yet. <Link href="/">Browse the catalog</Link>.
        </Empty>
      )}
      {orders && orders.items.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Order</th>
                <th scope="col">Item</th>
                <th scope="col">Total</th>
                <th scope="col">Status</th>
                <th scope="col">Placed</th>
              </tr>
            </thead>
            <tbody>
              {orders.items.map((order) => (
                <tr key={order.id}>
                  <td data-label="Order">
                    <Link href={`/orders/${order.id}`}>{shortId(order.id)}</Link>
                  </td>
                  <td data-label="Item">{order.line.productName}</td>
                  <td data-label="Total">{formatMoney(order.total)}</td>
                  <td data-label="Status">
                    <StatusBadge status={order.status} label={label(order.status)} />
                  </td>
                  <td data-label="Placed">{formatTime(order.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav className="pager" aria-label="Pages">
            <button type="button" className="button secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span className="muted">
              Page {orders.page + 1} of {Math.max(orders.totalPages, 1)}
            </span>
            <button
              type="button"
              className="button secondary"
              disabled={orders.page + 1 >= orders.totalPages}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </nav>
        </>
      )}
    </>
  );
}
