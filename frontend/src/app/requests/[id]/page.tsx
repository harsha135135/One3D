"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ModelSubmissionForm } from "@/components/ModelSubmissionForm";
import { QuoteList, RevisionList } from "@/components/RequestParts";
import { ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { ApiError, api, describeError, downloadUrl } from "@/lib/api";
import { formatMoney, label } from "@/lib/format";
import type { CustomRequest, Quote } from "@/lib/types";

const OPEN_FOR_REVISION = ["SUBMITTED", "CHANGES_REQUIRED", "QUOTED"];

/** One of the customer's requests: its revisions, quotes, and a new revision. */
export default function RequestPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [request, setRequest] = useState<CustomRequest | null>(null);
  const [quotes, setQuotes] = useState<Quote[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(() => {
    Promise.all([api.myRequest(id), api.myRequestQuotes(id)])
      .then(([r, q]) => {
        setRequest(r);
        setQuotes(q);
      })
      .catch((e: unknown) =>
        setError(e instanceof ApiError && e.status === 404 ? "This request was not found." : describeError(e)),
      );
  }, [id]);

  useEffect(load, [load]);

  async function accept(quote: Quote) {
    setBusy(true);
    setError(null);
    try {
      const order = await api.acceptQuote(quote.id);
      router.push(`/orders/${order.id}`);
    } catch (e) {
      setError(describeError(e));
      setBusy(false);
      load();
    }
  }

  return (
    <>
      <p>
        <Link href="/requests">← My requests</Link>
      </p>
      <h1>Custom part request</h1>
      {error && <ErrorMessage message={error} />}
      {!request && !error && <Loading what="the request" />}
      {request && (
        <>
          <p>
            <StatusBadge status={request.status} label={label(request.status)} />
          </p>
          {request.operatorMessage && (
            <p className="notice" role="note">
              Message from the operator: {request.operatorMessage}
            </p>
          )}

          <section className="section" aria-labelledby="quotes-heading">
            <h2 id="quotes-heading">Quotes</h2>
            <QuoteList
              quotes={quotes}
              revisions={request.revisions}
              action={(quote) =>
                quote.acceptable ? (
                  <button type="button" className="button" disabled={busy} onClick={() => accept(quote)}>
                    Accept {formatMoney(quote.price)} and create order
                  </button>
                ) : quote.orderId ? (
                  <Link href={`/orders/${quote.orderId}`}>View order</Link>
                ) : null
              }
            />
          </section>

          <section className="section" aria-labelledby="revisions-heading">
            <h2 id="revisions-heading">Revisions</h2>
            <RevisionList revisions={request.revisions} download={(r) => downloadUrl.ownFile(r.file.id)} />
          </section>

          {OPEN_FOR_REVISION.includes(request.status) && (
            <section className="section" aria-labelledby="revise-heading">
              <h2 id="revise-heading">Send a new revision</h2>
              <p className="muted">
                A new revision is reviewed from scratch. Any open quote is withdrawn.
              </p>
              <ModelSubmissionForm
                submitLabel="Send new revision"
                onSubmit={async (requirements) => {
                  await api.reviseRequest(request.id, requirements);
                  setMessage("New revision sent for review.");
                  load();
                }}
              />
            </section>
          )}
          <p role="status" aria-live="polite">
            {message}
          </p>
        </>
      )}
    </>
  );
}
