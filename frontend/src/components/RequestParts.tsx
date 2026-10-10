import type { ReactNode } from "react";
import { formatMoney, formatTime, label, shortId } from "@/lib/format";
import type { Quote, Revision } from "@/lib/types";
import { Empty, StatusBadge } from "./States";

function formatBytes(bytes: number): string {
  return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Every revision of a request, newest first, with the exact file it names
 * (stored version and SHA-256). {@code download} gives the link for a revision.
 */
export function RevisionList({ revisions, download }: { revisions: Revision[]; download: (r: Revision) => string }) {
  return (
    <ol className="cards" reversed>
      {[...revisions].reverse().map((revision, index) => (
        <li key={revision.id} className="card">
          <h3>
            Revision {revision.number}
            {index === 0 && <span className="muted"> · latest</span>}
          </h3>
          <dl className="facts">
            <dt>File</dt>
            <dd>
              {revision.file.filename} ({formatBytes(revision.file.sizeBytes)}){" "}
              {revision.file.status === "VERIFIED" && <a href={download(revision)}>Download</a>}
            </dd>
            <dt>SHA-256</dt>
            <dd className="mono">{revision.file.sha256 ?? "—"}</dd>
            <dt>Stored version</dt>
            <dd className="mono">{revision.file.versionId ?? "—"}</dd>
            <dt>Material</dt>
            <dd>{revision.material}</dd>
            <dt>Colour</dt>
            <dd>{revision.colour}</dd>
            <dt>Notes</dt>
            <dd>{revision.notes || "—"}</dd>
            <dt>Submitted</dt>
            <dd>{formatTime(revision.createdAt)}</dd>
          </dl>
        </li>
      ))}
    </ol>
  );
}

/** Quotes of a request, newest first. {@code action} renders a control for a quote (for example Accept). */
export function QuoteList({
  quotes,
  revisions,
  action,
}: {
  quotes: Quote[];
  revisions: Revision[];
  action?: (quote: Quote) => ReactNode;
}) {
  if (quotes.length === 0) {
    return <Empty>No quote yet.</Empty>;
  }
  const revisionNumber = (id: string) => revisions.find((r) => r.id === id)?.number ?? "?";
  return (
    <ul className="cards">
      {quotes.map((quote) => (
        <li key={quote.id} className="card">
          <h3>
            {formatMoney(quote.price)}{" "}
            <StatusBadge
              status={quote.status === "ISSUED" && quote.expired ? "EXPIRED" : quote.status}
              label={quote.status === "ISSUED" && quote.expired ? "Expired" : label(quote.status)}
            />
          </h3>
          <dl className="facts">
            <dt>Quote</dt>
            <dd>{shortId(quote.id)}</dd>
            <dt>For revision</dt>
            <dd>{revisionNumber(quote.revisionId)}</dd>
            <dt>Valid until</dt>
            <dd>{formatTime(quote.expiresAt)}</dd>
            {quote.recipe && (
              <>
                <dt>How it will be made</dt>
                <dd>
                  {quote.recipe.material}, {quote.recipe.layerHeightMicrons / 1000} mm layers
                  {quote.recipe.notes && ` · ${quote.recipe.notes}`}
                </dd>
              </>
            )}
            {quote.orderId && (
              <>
                <dt>Order</dt>
                <dd>{shortId(quote.orderId)}</dd>
              </>
            )}
          </dl>
          {action && <div className="actions" style={{ marginTop: 12 }}>{action(quote)}</div>}
        </li>
      ))}
    </ul>
  );
}
