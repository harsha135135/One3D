"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useSession } from "@/components/Session";
import { Empty, ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { SIGN_IN_URL, api, describeError } from "@/lib/api";
import { formatTime, label, shortId } from "@/lib/format";
import type { Page, RequestSummary } from "@/lib/types";

/** The signed-in customer's custom part requests, newest first. */
export default function MyRequestsPage() {
  const { session } = useSession();
  const [page, setPage] = useState(0);
  const [requests, setRequests] = useState<Page<RequestSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (session.status !== "signed-in") {
      return;
    }
    api.myRequests(page).then(setRequests).catch((e: unknown) => setError(describeError(e)));
  }, [session.status, page]);

  if (session.status === "signed-out") {
    return (
      <>
        <h1>My requests</h1>
        <p>
          <a className="button" href={SIGN_IN_URL}>
            Sign in to see your requests
          </a>
        </p>
      </>
    );
  }

  return (
    <>
      <h1>My requests</h1>
      <p>
        <Link className="button" href="/requests/new">
          New custom part
        </Link>
      </p>
      {error && <ErrorMessage message={error} />}
      {!requests && !error && <Loading what="your requests" />}
      {requests && requests.items.length === 0 && <Empty>You have not sent a model yet.</Empty>}
      {requests && requests.items.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Request</th>
                <th scope="col">Model</th>
                <th scope="col">Revision</th>
                <th scope="col">Status</th>
                <th scope="col">Updated</th>
              </tr>
            </thead>
            <tbody>
              {requests.items.map((request) => (
                <tr key={request.id}>
                  <td data-label="Request">
                    <Link href={`/requests/${request.id}`}>{shortId(request.id)}</Link>
                  </td>
                  <td data-label="Model">{request.filename}</td>
                  <td data-label="Revision">{request.latestRevision}</td>
                  <td data-label="Status">
                    <StatusBadge status={request.status} label={label(request.status)} />
                  </td>
                  <td data-label="Updated">{formatTime(request.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav className="pager" aria-label="Pages">
            <button type="button" className="button secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span className="muted">
              Page {requests.page + 1} of {Math.max(requests.totalPages, 1)}
            </span>
            <button
              type="button"
              className="button secondary"
              disabled={requests.page + 1 >= requests.totalPages}
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
