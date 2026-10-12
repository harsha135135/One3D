// Response shapes of the backend API (see backend/src/main/java/.../*Response.java).

export type Money = { amountMinor: number; currency: string };

/** paymentMode: RAZORPAY_TEST (hosted test-mode checkout), DEMO (shortcut, no payment) or NONE. */
export type Me = {
  id: string;
  email: string | null;
  roles: string[];
  demoPaymentEnabled: boolean;
  paymentMode: "RAZORPAY_TEST" | "DEMO" | "NONE";
};

export type CatalogItem = {
  configurationId: string;
  productName: string;
  configurationCode: string;
  description: string;
  price: Money;
  recipeRevisionNumber: number;
};

export type OrderStatus = "AWAITING_PAYMENT" | "CONFIRMED" | "READY_FOR_HANDOFF" | "HANDED_OVER";

export type Order = {
  id: string;
  customerId: string;
  status: OrderStatus;
  createdAt: string;
  confirmedAt: string | null;
  confirmationSource: string | null;
  readyForHandoffAt: string | null;
  handedOverAt: string | null;
  paymentWindowEndsAt: string;
  line: {
    /** CATALOG lines have a productConfigurationId; CUSTOM_QUOTE lines a quoteId. */
    source: "CATALOG" | "CUSTOM_QUOTE";
    productConfigurationId: string | null;
    quoteId: string | null;
    productName: string;
    configurationCode: string;
    configurationDescription: string;
    recipeRevisionId: string;
    recipeRevisionNumber: number;
    quantity: number;
    unitPrice: Money;
  };
  total: Money;
};

export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

export type JobStatus = "RELEASED" | "PRINTING" | "AWAITING_QC" | "ACCEPTED";

export type AttemptStatus =
  | "START_INTENT"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "REJECTED_BEFORE_START"
  | "RECONCILIATION_REQUIRED";

export type Attempt = {
  id: string;
  attemptNumber: number;
  printerId: string;
  status: AttemptStatus;
  startedAt: string;
  endedAt: string | null;
  failureReason: string | null;
  qcResult: "PASSED" | "FAILED" | null;
  qcNotes: string | null;
  qcRecordedAt: string | null;
};

export type Job = {
  id: string;
  orderId: string;
  recipeRevisionId: string;
  status: JobStatus;
  releasedAt: string;
  acceptedAt: string | null;
  attempts: Attempt[];
};

export type QueueItem = { order: Order; jobId: string | null; jobStatus: JobStatus | null };

export type Printer = {
  id: string;
  name: string;
  simulated: boolean;
  bedState: "CLEAR" | "OCCUPIED";
  bedCycleAttemptId: string | null;
  currentAttemptId: string | null;
  bedStateChangedAt: string;
};

export type Work = {
  id: string;
  /** DISPATCH_START names a commandId; VERIFY_ARTIFACT names an artifactId. */
  kind: "DISPATCH_START" | "VERIFY_ARTIFACT";
  commandId: string | null;
  artifactId: string | null;
  status: string;
  attempts: number;
  maxAttempts: number;
  lastError: string | null;
};

export type ArtifactStatus = "UPLOAD_PENDING" | "VERIFYING" | "VERIFIED" | "REJECTED";

/** An uploaded model file. versionId and sha256 identify the exact checked bytes. */
export type Artifact = {
  id: string;
  filename: string;
  sizeBytes: number;
  status: ArtifactStatus;
  versionId: string | null;
  sha256: string | null;
  rejectionReason: string | null;
  createdAt: string;
  uploadExpiresAt: string;
  verifiedAt: string | null;
};

export type UploadStart = {
  artifact: Artifact;
  upload: { method: "PUT"; url: string; headers: Record<string, string>; expiresAt: string; maxBytes: number };
};

export type Requirements = { artifactId: string; material: string; colour: string; notes: string };

export type RequestStatus = "SUBMITTED" | "CHANGES_REQUIRED" | "REJECTED" | "QUOTED" | "ORDERED";

export type Revision = {
  id: string;
  number: number;
  material: string;
  colour: string;
  notes: string;
  createdAt: string;
  file: Artifact;
};

export type CustomRequest = {
  id: string;
  customerId: string;
  status: RequestStatus;
  operatorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  revisions: Revision[];
};

export type RequestSummary = {
  id: string;
  status: RequestStatus;
  latestRevision: number;
  filename: string;
  material: string;
  colour: string;
  createdAt: string;
  updatedAt: string;
};

export type Recipe = { id: string; revisionNumber: number; material: string; layerHeightMicrons: number; notes: string };

export type Quote = {
  id: string;
  requestId: string;
  revisionId: string;
  price: Money;
  status: "ISSUED" | "ACCEPTED" | "WITHDRAWN";
  issuedAt: string;
  expiresAt: string;
  expired: boolean;
  acceptable: boolean;
  acceptedAt: string | null;
  withdrawnAt: string | null;
  orderId: string | null;
  recipe: Recipe | null;
};

export type IssueQuote = {
  revisionId: string;
  priceMinor: number;
  validDays: number;
  material: string;
  layerHeightMicrons: number;
  recipeNotes: string;
};

export type TimelineEntry = {
  kind:
    | "PLACED"
    | "CONFIRMED"
    | "PAYMENT_UNDER_REVIEW"
    | "RELEASED_TO_PRODUCTION"
    | "QC_PASSED"
    | "READY_FOR_HANDOFF"
    | "HANDED_OVER";
  occurredAt: string;
  detail: string | null;
};

/** What Razorpay Checkout needs; the key id is public, the secret never leaves the server. */
export type Checkout = {
  checkoutId: string;
  orderId: string;
  keyId: string;
  providerOrderId: string;
  amountMinor: number;
  currency: string;
  expiresAt: string;
  itemName: string;
  testMode: boolean;
};

export type PaymentCheck = { orderStatus: OrderStatus; newlyRecorded: number };

export type PaymentRecord = {
  id: string;
  providerPaymentId: string;
  providerOrderId: string;
  orderId: string | null;
  amountMinor: number;
  currency: string;
  providerCreatedAt: string;
  recordedAt: string;
  source: "WEBHOOK" | "RECONCILIATION";
  outcome: "APPLIED" | "NEEDS_REVIEW";
  reviewReason: "UNKNOWN_PROVIDER_ORDER" | "AMOUNT_MISMATCH" | "LATE_PAYMENT" | "SECOND_PAYMENT" | null;
  resolvedAt: string | null;
  resolutionNote: string | null;
};
