"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Empty, ErrorMessage, Loading, StatusBadge } from "@/components/States";
import { api, describeError } from "@/lib/api";
import { formatTime, label, shortId } from "@/lib/format";
import type { Page, RequestStatus, RequestSummary } from "@/lib/types";
import { OpsGuard } from "../OpsGuard";

const FILTERS: RequestStatus[] = ["SUBMITTED", "QUOTED", "CHANGES_REQUIRED", "ORDERED", "REJECTED"];

/** Custom part requests by state; submitted ones wait for review, longest waiting first. */
export default function OpsRequestsPage() {
  return (
    <OpsGuard>
      <Requests />
    </OpsGuard>
  );
}

function Requests() {
  const [status, setStatus] = useState<RequestStatus>("SUBMITTED");
  const [requests, setRequests] = useState<Page<RequestSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.opsRequests(status, 0).then(setRequests).catch((e: unknown) => setError(describeError(e)));
  }, [status]);

  return (
    <>
      <h1>Custom requests</h1>
      <div className="form-row">
        <label>
          Requests
          <select value={status} onChange={(e) => setStatus(e.target.value as RequestStatus)}>
            {FILTERS.map((f) => (
              <option key={f} value={f}>
                {label(f)}
              </option>
            ))}
          </select>
        </label>
      </div>
      {error && <ErrorMessage message={error} />}
      {!requests && !error && <Loading what="requests" />}
      {requests && requests.items.length === 0 && <Empty>No requests are {label(status).toLowerCase()}.</Empty>}
      {requests && requests.items.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Request</th>
              <th scope="col">Model</th>
              <th scope="col">Requirements</th>
              <th scope="col">Revision</th>
              <th scope="col">Status</th>
              <th scope="col">Waiting since</th>
            </tr>
          </thead>
          <tbody>
            {requests.items.map((request) => (
              <tr key={request.id}>
                <td data-label="Request">
                  <Link href={`/ops/requests/${request.id}`}>{shortId(request.id)}</Link>
                </td>
                <td data-label="Model">{request.filename}</td>
                <td data-label="Requirements">
                  {request.material}, {request.colour}
                </td>
                <td data-label="Revision">{request.latestRevision}</td>
                <td data-label="Status">
                  <StatusBadge status={request.status} label={label(request.status)} />
                </td>
                <td data-label="Waiting since">{formatTime(request.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
