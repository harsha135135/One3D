"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { api, describeError } from "@/lib/api";
import { formatTime, label, shortId } from "@/lib/format";
import type { Attempt, Job, Printer } from "@/lib/types";
import { OpsGuard } from "../../OpsGuard";

/** One production job: its attempts, starting the next one, QC and reconciliation. */
export default function JobPage() {
  return (
    <OpsGuard>
      <JobDetail />
    </OpsGuard>
  );
}

function JobDetail() {
  const { id } = useParams<{ id: string }>();
  const [job, setJob] = useState<Job | null>(null);
  const [printers, setPrinters] = useState<Printer[]>([]);
  const [printerId, setPrinterId] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.job(id).then(setJob).catch((e: unknown) => setError(describeError(e)));
    api
      .printers()
      .then((list) => {
        setPrinters(list);
        setPrinterId((current) => current || list[0]?.id || "");
      })
      .catch(() => setPrinters([]));
  }, [id]);

  useEffect(load, [load]);

  // While a print is in progress, the worker and observer update it in the
  // background; refresh every 2 seconds to show their results.
  useEffect(() => {
    if (job?.status !== "PRINTING") {
      return;
    }
    const timer = setInterval(() => api.job(id).then(setJob).catch(() => undefined), 2000);
    return () => clearInterval(timer);
  }, [job?.status, id]);

  async function act(action: () => Promise<Job>, done: string) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      setJob(await action());
      setMessage(done);
      api.printers().then(setPrinters).catch(() => undefined);
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  }

  const awaitingQc: Attempt | undefined =
    job?.status === "AWAITING_QC" ? job.attempts.findLast((a) => a.status === "COMPLETED" && !a.qcResult) : undefined;

  return (
    <>
      <p>
        <Link href="/ops">← Operations</Link>
      </p>
      <h1>Job {shortId(id)}</h1>
      {error && <ErrorMessage message={error} />}
      <p role="status" aria-live="polite">
        {message}
      </p>
      {!job && !error && <Loading what="the job" />}
      {job && (
        <>
          <dl className="facts card">
            <dt>Status</dt>
            <dd>
              <StatusBadge status={job.status} label={label(job.status)} />
            </dd>
            <dt>Order</dt>
            <dd>{shortId(job.orderId)}</dd>
            <dt>Released</dt>
            <dd>{formatTime(job.releasedAt)}</dd>
            <dt>Accepted</dt>
            <dd>{formatTime(job.acceptedAt)}</dd>
          </dl>

          {job.status === "RELEASED" && (
            <section aria-labelledby="start-heading">
              <h2 id="start-heading">Start the next attempt</h2>
              <div className="form-row">
                <label>
                  Printer
                  <select value={printerId} onChange={(e) => setPrinterId(e.target.value)}>
                    {printers.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} ({label(p.bedState)})
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  className="button"
                  disabled={busy || !printerId}
                  onClick={() => act(() => api.startAttempt(job.id, printerId), "Start recorded; sending it to the printer.")}
                >
                  Start attempt
                </button>
              </div>
            </section>
          )}

          {awaitingQc && (
            <section aria-labelledby="qc-heading">
              <h2 id="qc-heading">Inspect attempt {awaitingQc.attemptNumber}</h2>
              <div className="form-row">
                <label>
                  Notes (optional)
                  <input type="text" value={notes} maxLength={500} onChange={(e) => setNotes(e.target.value)} />
                </label>
                <button
                  type="button"
                  className="button"
                  disabled={busy}
                  onClick={() => act(() => api.recordQc(awaitingQc, "PASSED", notes), "Part passed inspection.")}
                >
                  Passed
                </button>
                <button
                  type="button"
                  className="button secondary"
                  disabled={busy}
                  onClick={() => act(() => api.recordQc(awaitingQc, "FAILED", notes), "Part failed inspection.")}
                >
                  Failed
                </button>
              </div>
            </section>
          )}

          <h2>Attempts</h2>
          {job.attempts.length === 0 ? (
            <p className="muted">No attempts yet.</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">Status</th>
                  <th scope="col">Started</th>
                  <th scope="col">Ended</th>
                  <th scope="col">Reason / QC</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {job.attempts.map((attempt) => (
                  <tr key={attempt.id}>
                    <td data-label="#">{attempt.attemptNumber}</td>
                    <td data-label="Status">
                      <StatusBadge status={attempt.status} label={label(attempt.status)} />
                    </td>
                    <td data-label="Started">{formatTime(attempt.startedAt)}</td>
                    <td data-label="Ended">{formatTime(attempt.endedAt)}</td>
                    <td data-label="Reason / QC">
                      {attempt.failureReason ?? (attempt.qcResult ? `QC ${label(attempt.qcResult)}` : "—")}
                    </td>
                    <td data-label="Action">
                      {attempt.status === "RECONCILIATION_REQUIRED" && (
                        <button
                          type="button"
                          className="button secondary"
                          disabled={busy}
                          onClick={() => act(() => api.reconcile(attempt.id), "Checking with the printer again.")}
                        >
                          Check again
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </>
  );
}
