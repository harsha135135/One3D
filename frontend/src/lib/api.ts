// Typed access to the backend API. The browser sends the session cookie
// automatically (same origin). State-changing requests also send the CSRF
// token from the XSRF-TOKEN cookie in the X-XSRF-TOKEN header.

import type {
  Artifact,
  Attempt,
  CatalogItem,
  Checkout,
  CustomRequest,
  IssueQuote,
  Job,
  Me,
  Order,
  Page,
  PaymentCheck,
  PaymentRecord,
  Printer,
  QueueItem,
  Quote,
  RequestStatus,
  RequestSummary,
  Requirements,
  TimelineEntry,
  UploadStart,
  Work,
} from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    message: string,
    readonly fieldErrors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}

/** Where the browser goes to sign in (the backend starts the OIDC login). */
export const SIGN_IN_URL = "/oauth2/authorization/oidc";

function csrfToken(): string | undefined {
  return document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith("XSRF-TOKEN="))
    ?.slice("XSRF-TOKEN=".length);
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (method !== "GET") {
    const token = csrfToken();
    if (token) {
      headers["X-XSRF-TOKEN"] = decodeURIComponent(token);
    }
  }
  const response = await fetch(path, {
    method,
    headers,
    credentials: "same-origin",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 204) {
    return undefined as T;
  }
  const json: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const problem = (json ?? {}) as { code?: string; detail?: string; errors?: { field: string; message: string }[] };
    throw new ApiError(response.status, problem.code ?? null, problem.detail ?? response.statusText, problem.errors ?? []);
  }
  return json as T;
}

const get = <T>(path: string) => request<T>("GET", path);
const post = <T>(path: string, body?: unknown) => request<T>("POST", path, body);

export const api = {
  me: () => get<Me>("/api/me"),
  catalog: () => get<CatalogItem[]>("/api/catalog/configurations"),

  placeOrder: (productConfigurationId: string) => post<Order>("/api/orders", { productConfigurationId, quantity: 1 }),
  myOrders: (page: number) => get<Page<Order>>(`/api/orders?page=${page}&size=10`),
  order: (id: string) => get<Order>(`/api/orders/${id}`),
  simulatePayment: (id: string) => post<Order>(`/api/demo/orders/${id}/simulate-payment`),
  timeline: (id: string) => get<TimelineEntry[]>(`/api/orders/${id}/timeline`),
  openCheckout: (id: string) => post<Checkout>(`/api/orders/${id}/checkout`),
  paymentCheck: (id: string) => post<PaymentCheck>(`/api/orders/${id}/payment-check`),

  queue: (status: string, page: number) => get<Page<QueueItem>>(`/api/ops/orders?status=${status}&page=${page}&size=20`),
  release: (orderId: string) => post<Job>(`/api/ops/orders/${orderId}/release`),
  readyForHandoff: (orderId: string) => post<Order>(`/api/ops/orders/${orderId}/ready-for-handoff`),
  handedOver: (orderId: string) => post<Order>(`/api/ops/orders/${orderId}/handed-over`),
  paymentReviews: () => get<PaymentRecord[]>("/api/ops/payments?reviews=true"),
  resolvePayment: (id: string, note: string) => post<PaymentRecord[]>(`/api/ops/payments/${id}/resolve`, { note }),
  job: (id: string) => get<Job>(`/api/ops/jobs/${id}`),
  startAttempt: (jobId: string, printerId: string) => post<Job>(`/api/ops/jobs/${jobId}/attempts`, { printerId }),
  recordQc: (attempt: Attempt, result: "PASSED" | "FAILED", notes: string) =>
    post<Job>(`/api/ops/attempts/${attempt.id}/qc`, { result, notes: notes || null }),
  reconcile: (attemptId: string) => post<Job>(`/api/ops/attempts/${attemptId}/reconcile`),
  printers: () => get<Printer[]>("/api/ops/printers"),
  clearBed: (printer: Printer) =>
    post<Printer>(`/api/ops/printers/${printer.id}/clear-bed`, { attemptId: printer.bedCycleAttemptId }),
  exhaustedWork: () => get<Work[]>("/api/ops/work?status=EXHAUSTED"),
  retryVerification: (artifactId: string) => post<Artifact>(`/api/ops/files/${artifactId}/retry-verification`),

  startUpload: (filename: string, sizeBytes: number) => post<UploadStart>("/api/files/uploads", { filename, sizeBytes }),
  finalizeUpload: (artifactId: string) => post<Artifact>(`/api/files/${artifactId}/finalize`),
  file: (artifactId: string) => get<Artifact>(`/api/files/${artifactId}`),

  submitRequest: (requirements: Requirements) => post<CustomRequest>("/api/requests", requirements),
  reviseRequest: (requestId: string, requirements: Requirements) =>
    post<CustomRequest>(`/api/requests/${requestId}/revisions`, requirements),
  myRequests: (page: number) => get<Page<RequestSummary>>(`/api/requests?page=${page}&size=10`),
  myRequest: (id: string) => get<CustomRequest>(`/api/requests/${id}`),
  myRequestQuotes: (id: string) => get<Quote[]>(`/api/requests/${id}/quotes`),
  acceptQuote: (quoteId: string) => post<Order>(`/api/quotes/${quoteId}/accept`),

  opsRequests: (status: RequestStatus, page: number) =>
    get<Page<RequestSummary>>(`/api/ops/requests?status=${status}&page=${page}&size=20`),
  opsRequest: (id: string) => get<CustomRequest>(`/api/ops/requests/${id}`),
  opsRequestQuotes: (id: string) => get<Quote[]>(`/api/ops/requests/${id}/quotes`),
  requestChanges: (id: string, message: string) => post<CustomRequest>(`/api/ops/requests/${id}/request-changes`, { message }),
  rejectRequest: (id: string, message: string) => post<CustomRequest>(`/api/ops/requests/${id}/reject`, { message }),
  issueQuote: (id: string, quote: IssueQuote) => post<Quote>(`/api/ops/requests/${id}/quotes`, quote),

  logout: () => post<void>("/logout"),
};

/** Download links: the backend checks access, then redirects to a short-lived link for the exact version. */
export const downloadUrl = {
  ownFile: (artifactId: string) => `/api/files/${artifactId}/download`,
  revisionFile: (requestId: string, revisionNumber: number) =>
    `/api/ops/requests/${requestId}/revisions/${revisionNumber}/file`,
};

/**
 * Sends the file straight to storage with the presigned URL, exactly as
 * signed (size and headers). No cookies or CSRF token go to storage.
 */
export async function putToStorage(upload: UploadStart["upload"], file: File): Promise<void> {
  const response = await fetch(upload.url, {
    method: "PUT",
    headers: upload.headers,
    body: file,
    credentials: "omit",
  });
  if (!response.ok) {
    throw new ApiError(response.status, "UPLOAD_FAILED", "The file could not be uploaded to storage.");
  }
}

/** A message for the person using the page, from any error the API calls raise. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "Please sign in to continue.";
    }
    const fields = error.fieldErrors.map((f) => `${f.field} ${f.message}`).join("; ");
    return fields ? `${error.message} (${fields})` : error.message;
  }
  return "The request could not be completed. Check your connection and try again.";
}
