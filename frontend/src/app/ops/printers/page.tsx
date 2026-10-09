"use client";

import { useCallback, useEffect, useState } from "react";
import { Empty, ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { api, describeError } from "@/lib/api";
import { formatTime, label, shortId } from "@/lib/format";
import type { Printer } from "@/lib/types";
import { OpsGuard } from "../OpsGuard";

/** Printers: who is using each one and whether its bed is clear. */
export default function PrintersPage() {
  return (
    <OpsGuard>
      <Printers />
    </OpsGuard>
  );
}

function Printers() {
  const [printers, setPrinters] = useState<Printer[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.printers().then(setPrinters).catch((e: unknown) => setError(describeError(e)));
  }, []);

  useEffect(load, [load]);

  async function clear(printer: Printer) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await api.clearBed(printer);
      setMessage(`Bed of ${printer.name} marked clear.`);
      load();
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1>Printers</h1>
      {error && <ErrorMessage message={error} />}
      <p role="status" aria-live="polite">
        {message}
      </p>
      {!printers && !error && <Loading what="printers" />}
      {printers && printers.length === 0 && <Empty>No printers are set up.</Empty>}
      {printers && printers.length > 0 && (
        <ul className="cards">
          {printers.map((printer) => (
            <li key={printer.id} className="card">
              <h2>{printer.name}</h2>
              <dl className="facts">
                <dt>Kind</dt>
                <dd>{printer.simulated ? "Simulated (no hardware)" : "Physical"}</dd>
                <dt>In use by</dt>
                <dd>{printer.currentAttemptId ? `Attempt ${shortId(printer.currentAttemptId)}` : "Nobody"}</dd>
                <dt>Bed</dt>
                <dd>
                  <StatusBadge status={printer.bedState} label={label(printer.bedState)} />
                  {printer.bedCycleAttemptId && (
                    <span className="muted"> · print of attempt {shortId(printer.bedCycleAttemptId)}</span>
                  )}
                </dd>
                <dt>Since</dt>
                <dd>{formatTime(printer.bedStateChangedAt)}</dd>
              </dl>
              {printer.bedState === "OCCUPIED" && !printer.currentAttemptId && (
                <div className="actions" style={{ marginTop: 12 }}>
                  <button type="button" className="button" disabled={busy} onClick={() => clear(printer)}>
                    Print removed: mark bed clear
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
