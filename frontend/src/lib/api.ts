// Typed access to the backend API. The browser sends the session cookie
// automatically (same origin). State-changing requests also send the CSRF
// token from the XSRF-TOKEN cookie in the X-XSRF-TOKEN header.

import type { Attempt, CatalogItem, Job, Me, Order, Page, Printer, QueueItem, Work } from "./types";

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

  queue: (status: string, page: number) => get<Page<QueueItem>>(`/api/ops/orders?status=${status}&page=${page}&size=20`),
  release: (orderId: string) => post<Job>(`/api/ops/orders/${orderId}/release`),
  readyForHandoff: (orderId: string) => post<Order>(`/api/ops/orders/${orderId}/ready-for-handoff`),
  job: (id: string) => get<Job>(`/api/ops/jobs/${id}`),
  startAttempt: (jobId: string, printerId: string) => post<Job>(`/api/ops/jobs/${jobId}/attempts`, { printerId }),
  recordQc: (attempt: Attempt, result: "PASSED" | "FAILED", notes: string) =>
    post<Job>(`/api/ops/attempts/${attempt.id}/qc`, { result, notes: notes || null }),
  reconcile: (attemptId: string) => post<Job>(`/api/ops/attempts/${attemptId}/reconcile`),
  printers: () => get<Printer[]>("/api/ops/printers"),
  clearBed: (printer: Printer) =>
    post<Printer>(`/api/ops/printers/${printer.id}/clear-bed`, { attemptId: printer.bedCycleAttemptId }),
  exhaustedWork: () => get<Work[]>("/api/ops/work?status=EXHAUSTED"),

  logout: () => post<void>("/logout"),
};

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
