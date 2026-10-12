"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Empty, ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { api, describeError } from "@/lib/api";
import { formatMoney, formatTime, label, shortId } from "@/lib/format";
import type { Page, QueueItem, Work } from "@/lib/types";
import { OpsGuard } from "./OpsGuard";

const FILTERS = ["CONFIRMED", "READY_FOR_HANDOFF", "AWAITING_PAYMENT", "HANDED_OVER"] as const;

/** The operator queue: orders by state with their production jobs. */
export default function OperationsPage() {
  return (
    <OpsGuard>
      <Queue />
    </OpsGuard>
  );
}

function Queue() {
  const [status, setStatus] = useState<(typeof FILTERS)[number]>("CONFIRMED");
  const [queue, setQueue] = useState<Page<QueueItem> | null>(null);
  const [work, setWork] = useState<Work[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.queue(status, 0).then(setQueue).catch((e: unknown) => setError(describeError(e)));
    api.exhaustedWork().then(setWork).catch(() => setWork([]));
  }, [status]);

  useEffect(load, [load]);

  const starts = work.filter((w) => w.kind === "DISPATCH_START");
  const checks = work.filter((w) => w.kind === "VERIFY_ARTIFACT" && w.artifactId);

  async function act(action: () => Promise<unknown>, done: string) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await action();
      setMessage(done);
      load();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1>Operations</h1>
      {starts.length > 0 && (
        <p className="notice" role="alert">
          {starts.length} print start(s) could not be confirmed after all retries. Open the job and choose “Check
          again”.
        </p>
      )}
      {checks.length > 0 && (
        <div className="notice" role="alert">
          <p>{checks.length} uploaded file check(s) could not read storage after all retries.</p>
          <ul>
            {checks.map((w) => (
              <li key={w.id}>
                File {shortId(w.artifactId)}: {w.lastError ?? "no error recorded"}{" "}
                <button
                  type="button"
                  className="button secondary"
                  disabled={busy}
                  onClick={() => act(() => api.retryVerification(w.artifactId!), "File check queued again.")}
                >
                  Retry check
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
      <div className="form-row">
        <label>
          Orders
          <select value={status} onChange={(e) => setStatus(e.target.value as (typeof FILTERS)[number])}>
            {FILTERS.map((f) => (
              <option key={f} value={f}>
                {label(f)}
              </option>
            ))}
          </select>
        </label>
      </div>
      {error && <ErrorMessage message={error} />}
      <p role="status" aria-live="polite">
        {message}
      </p>
      {!queue && !error && <Loading what="the queue" />}
      {queue && queue.items.length === 0 && <Empty>No orders are {label(status).toLowerCase()}.</Empty>}
      {queue && queue.items.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Order</th>
              <th scope="col">Item</th>
              <th scope="col">Total</th>
              <th scope="col">Placed</th>
              <th scope="col">Production</th>
              <th scope="col">Action</th>
            </tr>
          </thead>
          <tbody>
            {queue.items.map(({ order, jobId, jobStatus }) => (
              <tr key={order.id}>
                <td data-label="Order">
                  {shortId(order.id)} <StatusBadge status={order.status} label={label(order.status)} />
                </td>
                <td data-label="Item">{order.line.productName}</td>
                <td data-label="Total">{formatMoney(order.total)}</td>
                <td data-label="Placed">{formatTime(order.createdAt)}</td>
                <td data-label="Production">
                  {jobId ? (
                    <Link href={`/ops/jobs/${jobId}`}>
                      Job {shortId(jobId)} · {label(jobStatus)}
                    </Link>
                  ) : (
                    <span className="muted">Not released</span>
                  )}
                </td>
                <td data-label="Action">
                  <div className="actions">
                    {order.status === "CONFIRMED" && !jobId && (
                      <button
                        type="button"
                        className="button"
                        disabled={busy}
                        onClick={() => act(() => api.release(order.id), `Order ${shortId(order.id)} released.`)}
                      >
                        Release
                      </button>
                    )}
                    {order.status === "READY_FOR_HANDOFF" && (
                      <button
                        type="button"
                        className="button"
                        disabled={busy}
                        onClick={() =>
                          act(() => api.handedOver(order.id), `Order ${shortId(order.id)} recorded as handed over.`)
                        }
                      >
                        Handed over
                      </button>
                    )}
                    {order.status === "CONFIRMED" && jobStatus === "ACCEPTED" && (
                      <button
                        type="button"
                        className="button"
                        disabled={busy}
                        onClick={() =>
                          act(() => api.readyForHandoff(order.id), `Order ${shortId(order.id)} is ready for handoff.`)
                        }
                      >
                        Ready for handoff
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
