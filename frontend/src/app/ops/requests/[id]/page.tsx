"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { QuoteList, RevisionList } from "@/components/RequestParts";
import { ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { ApiError, api, describeError, downloadUrl } from "@/lib/api";
import { label } from "@/lib/format";
import type { CustomRequest, Quote } from "@/lib/types";
import { OpsGuard } from "../../OpsGuard";

/** Review of one custom request: download, ask for changes, reject or quote. */
export default function OpsRequestPage() {
  return (
    <OpsGuard>
      <Review />
    </OpsGuard>
  );
}

function Review() {
  const { id } = useParams<{ id: string }>();
  const [request, setRequest] = useState<CustomRequest | null>(null);
  const [quotes, setQuotes] = useState<Quote[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    Promise.all([api.opsRequest(id), api.opsRequestQuotes(id)])
      .then(([r, q]) => {
        setRequest(r);
        setQuotes(q);
      })
      .catch((e: unknown) =>
        setError(e instanceof ApiError && e.status === 404 ? "This request was not found." : describeError(e)),
      );
  }, [id]);

  useEffect(load, [load]);

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

  const latest = request?.revisions[request.revisions.length - 1];
  const inReview = request?.status === "SUBMITTED" || request?.status === "CHANGES_REQUIRED";
  const quotable = request?.status === "SUBMITTED" || request?.status === "QUOTED";

  return (
    <>
      <p>
        <Link href="/ops/requests">← Custom requests</Link>
      </p>
      <h1>Review request</h1>
      {error && <ErrorMessage message={error} />}
      <p role="status" aria-live="polite">
        {message}
      </p>
      {!request && !error && <Loading what="the request" />}
      {request && latest && (
        <>
          <p>
            <StatusBadge status={request.status} label={label(request.status)} />
          </p>
          {request.operatorMessage && <p className="notice">Last message to the customer: {request.operatorMessage}</p>}

          <section className="section" aria-labelledby="revisions-heading">
            <h2 id="revisions-heading">Revisions</h2>
            <RevisionList
              revisions={request.revisions}
              download={(r) => downloadUrl.revisionFile(request.id, r.number)}
            />
          </section>

          {quotable && (
            <section className="section" aria-labelledby="quote-heading">
              <h2 id="quote-heading">Quote revision {latest.number}</h2>
              <QuoteForm
                busy={busy}
                material={latest.material}
                onSubmit={(quote) =>
                  act(() => api.issueQuote(request.id, { ...quote, revisionId: latest.id }), "Quote sent.")
                }
              />
            </section>
          )}

          {inReview && (
            <section className="section" aria-labelledby="decide-heading">
              <h2 id="decide-heading">Ask for changes or reject</h2>
              <MessageForm
                busy={busy}
                onRequestChanges={
                  request.status === "SUBMITTED"
                    ? (text) => act(() => api.requestChanges(request.id, text), "Changes requested.")
                    : undefined
                }
                onReject={(text) => act(() => api.rejectRequest(request.id, text), "Request rejected.")}
              />
            </section>
          )}

          <section className="section" aria-labelledby="quotes-heading">
            <h2 id="quotes-heading">Quotes</h2>
            <QuoteList quotes={quotes} revisions={request.revisions} />
          </section>
        </>
      )}
    </>
  );
}

function QuoteForm({
  busy,
  material: initialMaterial,
  onSubmit,
}: {
  busy: boolean;
  material: string;
  onSubmit: (quote: {
    priceMinor: number;
    validDays: number;
    material: string;
    layerHeightMicrons: number;
    recipeNotes: string;
  }) => void;
}) {
  const [rupees, setRupees] = useState("");
  const [validDays, setValidDays] = useState("7");
  const [material, setMaterial] = useState(initialMaterial);
  const [layerHeight, setLayerHeight] = useState("200");
  const [recipeNotes, setRecipeNotes] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    onSubmit({
      priceMinor: Math.round(Number(rupees) * 100),
      validDays: Number(validDays),
      material,
      layerHeightMicrons: Number(layerHeight),
      recipeNotes,
    });
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>
        Price (₹)
        <input type="number" required min="1" step="0.01" value={rupees} onChange={(e) => setRupees(e.target.value)} />
      </label>
      <label>
        Valid for (days, 1–14)
        <input type="number" required min="1" max="14" value={validDays} onChange={(e) => setValidDays(e.target.value)} />
      </label>
      <label>
        Material
        <input type="text" required maxLength={80} value={material} onChange={(e) => setMaterial(e.target.value)} />
      </label>
      <label>
        Layer height (microns, 50–400)
        <input
          type="number"
          required
          min="50"
          max="400"
          value={layerHeight}
          onChange={(e) => setLayerHeight(e.target.value)}
        />
      </label>
      <label>
        Recipe notes (walls, infill, orientation)
        <textarea maxLength={500} rows={2} value={recipeNotes} onChange={(e) => setRecipeNotes(e.target.value)} />
      </label>
      <div className="actions">
        <button type="submit" className="button" disabled={busy}>
          Send quote
        </button>
      </div>
    </form>
  );
}

function MessageForm({
  busy,
  onRequestChanges,
  onReject,
}: {
  busy: boolean;
  onRequestChanges?: (message: string) => void;
  onReject: (message: string) => void;
}) {
  const [text, setText] = useState("");
  const ready = text.trim().length > 0 && !busy;
  return (
    <div className="form">
      <label>
        Message to the customer
        <textarea maxLength={1000} rows={3} value={text} onChange={(e) => setText(e.target.value)} />
      </label>
      <div className="actions">
        {onRequestChanges && (
          <button type="button" className="button" disabled={!ready} onClick={() => onRequestChanges(text)}>
            Ask for changes
          </button>
        )}
        <button type="button" className="button secondary" disabled={!ready} onClick={() => onReject(text)}>
          Reject request
        </button>
      </div>
    </div>
  );
}
