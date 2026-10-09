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
