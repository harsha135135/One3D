import { expect, test, type Page, type Route } from "@playwright/test";

const ITEM = {
  configurationId: "cfg-1",
  productName: "Hex Desk Organizer (demo)",
  configurationCode: "HEX-ORG-PLA-BLACK",
  description: "Matte black PLA+, 0.20 mm layers, single plate",
  price: { amountMinor: 49900, currency: "INR" },
  recipeRevisionNumber: 1,
};

function order(status: string) {
  return {
    id: "11111111-2222-4333-8444-555555555555",
    customerId: "c-1",
    status,
    createdAt: "2026-10-09T08:00:00Z",
    confirmedAt: status === "AWAITING_PAYMENT" ? null : "2026-10-09T08:01:00Z",
    confirmationSource: status === "AWAITING_PAYMENT" ? null : "DEMO_SIMULATED",
    readyForHandoffAt: null,
    line: {
      productConfigurationId: "cfg-1",
      productName: ITEM.productName,
      configurationCode: ITEM.configurationCode,
      configurationDescription: ITEM.description,
      recipeRevisionId: "r-1",
      recipeRevisionNumber: 1,
      quantity: 1,
      unitPrice: ITEM.price,
    },
    total: ITEM.price,
  };
}

const CUSTOMER = { id: "c-1", email: "customer@example.test", roles: ["CUSTOMER"], demoPaymentEnabled: true };
const OPERATOR = { id: "o-1", email: "operator@example.test", roles: ["CUSTOMER", "OPERATOR"], demoPaymentEnabled: true };

type Handler = (route: Route) => Promise<void> | void;

// Next.js adds its own role="alert" route announcer, so alerts are found by text.

/** Mocks /api/** in the browser; unmatched calls fail the test loudly. */
async function mockApi(page: Page, me: object | null, handlers: Record<string, Handler>) {
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const key = `${route.request().method()} ${url.pathname}`;
    if (key === "GET /api/me") {
      return me
        ? route.fulfill({ json: me })
        : route.fulfill({ status: 401, json: { code: "UNAUTHENTICATED", detail: "Sign in." } });
    }
    const handler = handlers[key];
    if (!handler) {
      return route.fulfill({ status: 500, json: { detail: `Unmocked ${key}` } });
    }
    return handler(route);
  });
}

async function tabTo(page: Page, accessibleName: RegExp) {
  for (let i = 0; i < 25; i++) {
    await page.keyboard.press("Tab");
    const name = await page.evaluate(
      () => document.activeElement?.getAttribute("aria-label") ?? document.activeElement?.textContent ?? "",
    );
    if (accessibleName.test(name)) {
      return;
    }
  }
  throw new Error(`Could not reach ${accessibleName} with the keyboard`);
}

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
}

test("a signed-out visitor sees the catalog and is sent to sign in to order", async ({ page }) => {
  await mockApi(page, null, { "GET /api/catalog/configurations": (r) => r.fulfill({ json: [ITEM] }) });
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Catalog" })).toBeVisible();
  await expect(page.getByText("₹499.00")).toBeVisible();
  await expect(page.getByRole("link", { name: "Sign in to order" })).toHaveAttribute("href", "/oauth2/authorization/oidc");
  await expectNoHorizontalScroll(page);
});

test("a customer orders and pays (demo) using only the keyboard, sending the CSRF header", async ({ page, context }) => {
  await context.addCookies([{ name: "XSRF-TOKEN", value: "csrf-123", url: "http://localhost:3100" }]);
  const csrfHeaders: (string | undefined)[] = [];
  let current = order("AWAITING_PAYMENT");
  await mockApi(page, CUSTOMER, {
    "GET /api/catalog/configurations": (r) => r.fulfill({ json: [ITEM] }),
    "POST /api/orders": (r) => {
      csrfHeaders.push(r.request().headers()["x-xsrf-token"]);
      return r.fulfill({ status: 201, json: current });
    },
    [`GET /api/orders/${current.id}`]: (r) => r.fulfill({ json: current }),
    [`POST /api/demo/orders/${current.id}/simulate-payment`]: (r) => {
      csrfHeaders.push(r.request().headers()["x-xsrf-token"]);
      current = order("CONFIRMED");
      return r.fulfill({ json: current });
    },
  });
  await page.goto("/");
  await expect(page.getByText("₹499.00")).toBeVisible();

  await tabTo(page, /^Order Hex Desk Organizer/);
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { name: "Order" })).toBeVisible();
  await expect(page.getByText("Awaiting payment")).toBeVisible();

  await tabTo(page, /^Pay \(demo/);
  await page.keyboard.press("Enter");

  await expect(page.getByText("Payment simulated. Your order is confirmed.")).toBeVisible();
  await expect(page.locator("[data-status=CONFIRMED]")).toBeVisible();
  expect(csrfHeaders).toEqual(["csrf-123", "csrf-123"]);
  await expectNoHorizontalScroll(page);
});

test("an order that is not the customer's is shown as not found", async ({ page }) => {
  await mockApi(page, CUSTOMER, {
    "GET /api/orders/someone-elses": (r) => r.fulfill({ status: 404, json: { code: "ORDER_NOT_FOUND", detail: "Not found" } }),
  });
  await page.goto("/orders/someone-elses");

  await expect(page.getByRole("alert").filter({ hasText: /This order was not found/ })).toBeVisible();
});

test("a customer does not get operator pages", async ({ page }) => {
  await mockApi(page, CUSTOMER, {});
  await page.goto("/ops");

  await expect(page.getByRole("alert").filter({ hasText: /This page is for operators/ })).toBeVisible();
});

test("the operator queue shows a refusal from the API as an alert", async ({ page }) => {
  await mockApi(page, OPERATOR, {
    "GET /api/ops/orders": (r) =>
      r.fulfill({
        json: { items: [{ order: order("CONFIRMED"), jobId: null, jobStatus: null }], page: 0, size: 20, totalItems: 1, totalPages: 1 },
      }),
    "GET /api/ops/work": (r) => r.fulfill({ json: [] }),
    [`POST /api/ops/orders/${order("CONFIRMED").id}/release`]: (r) =>
      r.fulfill({ status: 409, json: { code: "ALREADY_RELEASED", detail: "Order already has a production job." } }),
  });
  await page.goto("/ops");

  await page.getByRole("button", { name: "Release" }).click();

  await expect(page.getByRole("alert").filter({ hasText: /Order already has a production job/ })).toBeVisible();
  await expectNoHorizontalScroll(page);
});

test("clearing a bed names the print that is on it", async ({ page }) => {
  let body: unknown = null;
  const printer = {
    id: "p-1",
    name: "SIM-X1C-01 (simulated)",
    simulated: true,
    bedState: "OCCUPIED",
    bedCycleAttemptId: "attempt-7",
    currentAttemptId: null,
    bedStateChangedAt: "2026-10-09T08:00:00Z",
  };
  await mockApi(page, OPERATOR, {
    "GET /api/ops/printers": (r) => r.fulfill({ json: [printer] }),
    "POST /api/ops/printers/p-1/clear-bed": (r) => {
      body = r.request().postDataJSON();
      printer.bedState = "CLEAR";
      printer.bedCycleAttemptId = null as unknown as string;
      return r.fulfill({ json: printer });
    },
  });
  await page.goto("/ops/printers");

  await page.getByRole("button", { name: /mark bed clear/ }).click();

  await expect(page.getByText("Bed of SIM-X1C-01 (simulated) marked clear.")).toBeVisible();
  expect(body).toEqual({ attemptId: "attempt-7" });
});

// --- custom parts (Checkpoint 5) ---------------------------------------------

const ARTIFACT = {
  id: "a-1",
  filename: "bracket.stl",
  sizeBytes: 684,
  status: "VERIFIED",
  versionId: "v-123",
  sha256: "9f".repeat(32),
  rejectionReason: null,
  createdAt: "2026-10-10T08:00:00Z",
  uploadExpiresAt: "2026-10-10T08:15:00Z",
  verifiedAt: "2026-10-10T08:00:05Z",
};

function customRequest(status: string) {
  return {
    id: "req-1",
    customerId: "c-1",
    status,
    operatorMessage: null,
    createdAt: "2026-10-10T08:00:10Z",
    updatedAt: "2026-10-10T08:00:10Z",
    revisions: [
      { id: "rev-1", number: 1, material: "PETG", colour: "Orange", notes: "Outdoor use", createdAt: "2026-10-10T08:00:10Z", file: ARTIFACT },
    ],
  };
}

function quote(overrides: object = {}) {
  return {
    id: "q-1",
    requestId: "req-1",
    revisionId: "rev-1",
    price: { amountMinor: 129900, currency: "INR" },
    status: "ISSUED",
    issuedAt: "2026-10-10T09:00:00Z",
    expiresAt: "2026-10-17T09:00:00Z",
    expired: false,
    acceptable: true,
    acceptedAt: null,
    withdrawnAt: null,
    orderId: null,
    recipe: { id: "rr-1", revisionNumber: 1, material: "PETG", layerHeightMicrons: 200, notes: "3 walls" },
    ...overrides,
  };
}

test("a customer uploads a model straight to storage, waits for the check and sends a request", async ({ page, context }) => {
  await context.addCookies([{ name: "XSRF-TOKEN", value: "csrf-123", url: "http://localhost:3100" }]);
  const storagePuts: { headers: Record<string, string>; size: number }[] = [];
  let checks = 0;
  let submitted: unknown = null;
  await page.route("**/s3/**", async (route) => {
    storagePuts.push({ headers: route.request().headers(), size: route.request().postDataBuffer()?.length ?? 0 });
    await route.fulfill({ status: 200, body: "" });
  });
  await mockApi(page, CUSTOMER, {
    "POST /api/files/uploads": (r) =>
      r.fulfill({
        status: 201,
        json: {
          artifact: { ...ARTIFACT, status: "UPLOAD_PENDING", versionId: null, sha256: null },
          upload: {
            method: "PUT",
            url: "http://localhost:3100/s3/one3d-artifacts/uploads/a-1?X-Amz-Signature=test",
            headers: { "Content-Type": "application/octet-stream" },
            expiresAt: "2026-10-10T08:15:00Z",
            maxBytes: 20971520,
          },
        },
      }),
    "POST /api/files/a-1/finalize": (r) => r.fulfill({ json: { ...ARTIFACT, status: "VERIFYING", sha256: null } }),
    "GET /api/files/a-1": (r) =>
      r.fulfill({ json: checks++ === 0 ? { ...ARTIFACT, status: "VERIFYING", sha256: null } : ARTIFACT }),
    "POST /api/requests": (r) => {
      submitted = r.request().postDataJSON();
      return r.fulfill({ status: 201, json: customRequest("SUBMITTED") });
    },
    "GET /api/requests/req-1": (r) => r.fulfill({ json: customRequest("SUBMITTED") }),
    "GET /api/requests/req-1/quotes": (r) => r.fulfill({ json: [] }),
  });
  await page.goto("/requests/new");

  await page.getByLabel("Model file (.stl)").setInputFiles({
    name: "bracket.stl",
    mimeType: "model/stl",
    buffer: Buffer.alloc(684, 1),
  });
  await page.getByLabel("Material").selectOption("PETG");
  await page.getByLabel("Colour").fill("Orange");
  await page.getByLabel("Notes for the operator").fill("Outdoor use");
  await page.getByRole("button", { name: "Send request" }).click();

  await expect(page.getByRole("heading", { name: "Custom part request" })).toBeVisible();
  await expect(page.getByText("No quote yet.")).toBeVisible();
  expect(storagePuts).toHaveLength(1);
  const [put] = storagePuts;
  expect(put?.size).toBe(684);
  expect(put?.headers["content-type"]).toBe("application/octet-stream");
  // Nothing from the session goes to storage.
  expect(put?.headers["x-xsrf-token"]).toBeUndefined();
  expect(put?.headers["cookie"]).toBeUndefined();
  expect(submitted).toEqual({ artifactId: "a-1", material: "PETG", colour: "Orange", notes: "Outdoor use" });
  await expectNoHorizontalScroll(page);
});

test("a file that is too large is refused before anything is uploaded", async ({ page }) => {
  await mockApi(page, CUSTOMER, {});
  await page.goto("/requests/new");

  await page.getByLabel("Model file (.stl)").setInputFiles({
    name: "huge.stl",
    mimeType: "model/stl",
    buffer: Buffer.alloc(20 * 1024 * 1024 + 1),
  });

  await expect(page.getByRole("alert").filter({ hasText: /larger than 20 MB/ })).toBeVisible();
});

test("a customer accepts an open quote and goes to the new order; an expired quote has no button", async ({ page, context }) => {
  await context.addCookies([{ name: "XSRF-TOKEN", value: "csrf-123", url: "http://localhost:3100" }]);
  let acceptHeader: string | undefined;
  const created = { ...order("AWAITING_PAYMENT"), line: { ...order("AWAITING_PAYMENT").line, productName: "Custom part: bracket.stl" } };
  await mockApi(page, CUSTOMER, {
    "GET /api/requests/req-1": (r) => r.fulfill({ json: customRequest("QUOTED") }),
    "GET /api/requests/req-1/quotes": (r) =>
      r.fulfill({ json: [quote(), quote({ id: "q-0", expired: true, acceptable: false, price: { amountMinor: 99900, currency: "INR" } })] }),
    "POST /api/quotes/q-1/accept": (r) => {
      acceptHeader = r.request().headers()["x-xsrf-token"];
      return r.fulfill({ json: created });
    },
    [`GET /api/orders/${created.id}`]: (r) => r.fulfill({ json: created }),
  });
  await page.goto("/requests/req-1");

  await expect(page.getByText("Expired")).toBeVisible();
  await expect(page.getByRole("button", { name: /Accept/ })).toHaveCount(1);
  await expect(page.getByText("9f".repeat(32))).toBeVisible();
  await expectNoHorizontalScroll(page);

  await page.getByRole("button", { name: "Accept ₹1,299.00 and create order" }).click();

  await expect(page.getByRole("heading", { name: "Custom part: bracket.stl" })).toBeVisible();
  expect(acceptHeader).toBe("csrf-123");
});

test("an operator quotes the latest revision in rupees", async ({ page }) => {
  let body: unknown = null;
  await mockApi(page, OPERATOR, {
    "GET /api/ops/requests/req-1": (r) => r.fulfill({ json: customRequest("SUBMITTED") }),
    "GET /api/ops/requests/req-1/quotes": (r) => r.fulfill({ json: body ? [quote()] : [] }),
    "POST /api/ops/requests/req-1/quotes": (r) => {
      body = r.request().postDataJSON();
      return r.fulfill({ status: 201, json: quote() });
    },
  });
  await page.goto("/ops/requests/req-1");

  await expect(page.getByRole("link", { name: "Download" })).toHaveAttribute("href", "/api/ops/requests/req-1/revisions/1/file");
  await page.getByLabel("Price (₹)").fill("1299.50");
  await page.getByLabel("Recipe notes (walls, infill, orientation)").fill("3 walls");
  await page.getByRole("button", { name: "Send quote" }).click();

  await expect(page.getByText("Quote sent.")).toBeVisible();
  expect(body).toEqual({
    revisionId: "rev-1",
    priceMinor: 129950,
    validDays: 7,
    material: "PETG",
    layerHeightMicrons: 200,
    recipeNotes: "3 walls",
  });
  await expectNoHorizontalScroll(page);
});
