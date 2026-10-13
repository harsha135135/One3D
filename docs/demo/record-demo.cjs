// Records the five-minute V1 demo (docs/demo.md) on the deployed site as one
// video, by driving the real UI with captions: a custom part request, the
// operator's quote, acceptance, a Razorpay test payment, bed clearance, release,
// a start whose acknowledgement the simulator loses (reconciled through its
// journal), QC and handoff, and the customer's timeline. The printer is the
// simulator; payment is Razorpay test mode. Run from frontend/ as the deployment
// user while the demo is running (optionally with Grafana forwarded, GRAFANA_URL):
//   NODE_PATH=$PWD/node_modules node ../docs/demo/record-demo.cjs <out dir>
// Writes <out dir>/one3d-v1-demo.webm and a step log (record-demo.txt).
const { chromium } = require("@playwright/test");
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const cp7 = require("../evidence/2026-10-12-checkpoint-7/cloud/cloud-checks.cjs");

const SITE = cp7.SITE;
const OUT = path.resolve(process.argv[2] || ".");
const FIXTURE = path.resolve(__dirname, "fixtures/wall-hook.stl");
const AWS_ENV = { ...process.env, AWS_PROFILE: process.env.AWS_PROFILE || "one3d-deployer", AWS_REGION: "ap-south-1" };
const lines = [];
const step = (text) => { const line = `${new Date().toISOString().slice(11, 19)} ${text}`; lines.push(line); console.log(line); };
const short = (id) => id.slice(0, 8);

/** A caption bar at the bottom of the page (added again after every page load). */
async function caption(page, text, hold = 2500) {
  await page.evaluate((t) => {
    let bar = document.getElementById("demo-caption");
    if (!bar) {
      bar = document.createElement("div");
      bar.id = "demo-caption";
      Object.assign(bar.style, {
        position: "fixed", left: "0", right: "0", bottom: "0", zIndex: "2147483647", padding: "12px 20px",
        background: "rgba(16,16,16,0.94)", color: "#fff", font: "17px/1.45 system-ui, sans-serif",
        borderTop: "3px solid #ff7a00",
      });
      document.body.appendChild(bar);
    }
    bar.textContent = t;
  }, text);
  step(text);
  await page.waitForTimeout(hold);
}

/** Signs in as a demo user through Cognito's hosted pages, after clearing every cookie (both sessions). */
async function signInAs(context, page, role) {
  await context.clearCookies();
  const user = cp7.demoUser(role);
  await page.goto(`${SITE}/oauth2/authorization/oidc`);
  await page.locator('input[name="username"]:visible').fill(user.email);
  await page.locator('input[name="password"]:visible').fill(user.password);
  await page.locator('input[name="signInSubmitButton"]:visible').click();
  await page.locator("#totpCodeInput").waitFor();
  await page.locator("#totpCodeInput").fill(await cp7.freshTotp(role, user.totp_secret));
  await page.locator('input[type="submit"]:visible, button[type="submit"]:visible').first().click();
  await page.waitForURL(`${SITE}/`);
  step(`signed in as the demo ${role} (Cognito, password and TOTP)`);
}

async function payInCheckout(page, context) {
  await page.getByRole("button", { name: /with Razorpay \(test mode\)/ }).click();
  const frame = await (await page.waitForSelector("iframe.razorpay-checkout-frame", { timeout: 30000 })).contentFrame();
  await frame.locator('input[type="tel"]').first().waitFor({ timeout: 30000 });
  await page.waitForTimeout(1500);
  const mobile = frame.getByPlaceholder("Mobile number");
  if (await mobile.isVisible().catch(() => false)) {
    await mobile.fill("9000090000");
    await frame.getByRole("button", { name: "Continue" }).last().click();
    await page.waitForTimeout(1500);
  }
  await frame.getByPlaceholder("Card Number").fill("4100 2800 0000 1007");
  await frame.locator('input[placeholder*="MM"], input[name="card.expiry"]').first().fill("12 / 30");
  await frame.locator('input[placeholder*="CVV"], input[name="card.cvv"]').first().fill("123");
  await page.waitForTimeout(1500);
  const bankPage = context.waitForEvent("page", { timeout: 30000 });
  await frame.locator("button:visible").filter({ hasText: /^(Continue|Pay)/ }).last().click();
  await page.waitForTimeout(3000);
  for (const name of [/Maybe later/i, /Skip/i, /Continue without/i]) {
    const button = frame.getByRole("button", { name });
    if (await button.isVisible().catch(() => false)) { await button.click(); await page.waitForTimeout(1500); }
  }
  const bank = await bankPage;
  await bank.waitForURL((url) => url.href !== "about:blank", { timeout: 45000 });
  await bank.waitForLoadState("domcontentloaded");
  await bank.waitForTimeout(1500);
  await bank.getByRole("button", { name: "Success" }).click();
  await page.getByText(/Payment received|not received a confirmed payment/).waitFor({ timeout: 90000 });
}

/** The latest attempt's status, read through the API with the page's own session. */
async function attemptStatus(page, jobId) {
  return (await cp7.json(page, "GET", `/api/ops/jobs/${jobId}`)).attempts.at(-1)?.status;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    recordVideo: { dir: OUT, size: { width: 1280, height: 800 } },
  });
  const page = await context.newPage();

  // 0:00 Problem, scope, what is simulated
  await page.goto(SITE);
  await caption(page, "One3D V1: a small custom-manufacturing order system (Java/Spring Boot, PostgreSQL, Next.js) on AWS. " +
    "The printer is a simulator and payments are Razorpay TEST MODE: no real printer, no real money.", 6000);
  await caption(page, "This run: a customer sends a model, the operator quotes it, the customer accepts and pays, " +
    "and the operator produces it through to handoff.", 5000);

  // 0:45 Request, reviewed revision, quote, accepted order
  await signInAs(context, page, "customer");
  await page.goto(`${SITE}/requests/new`);
  await caption(page, "Customer: one STL (at most 20 MB) with requirements. The browser uploads it straight to a private S3 bucket.");
  await page.locator('input[type="file"]').setInputFiles(FIXTURE);
  await page.getByLabel("Material").selectOption("PETG");
  await page.getByLabel("Colour").fill("Orange");
  await page.getByLabel("Notes for the operator").fill("Hangs a 2 kg bag; matte finish preferred.");
  await page.waitForTimeout(1500);
  await page.getByRole("button", { name: "Send request" }).click();
  await page.waitForURL(/\/requests\/[0-9a-f-]{36}$/, { timeout: 120000 });
  const requestId = page.url().split("/").pop();
  await page.waitForLoadState("networkidle");
  await caption(page, `Request ${short(requestId)} sent. The backend checked the exact stored file version: size, STL structure and SHA-256.`, 4000);

  await signInAs(context, page, "operator");
  await page.goto(`${SITE}/ops/requests/${requestId}`);
  await page.waitForLoadState("networkidle");
  await caption(page, "Operator: reviews revision 1 (and can download that exact checked file), then quotes it.", 3500);
  await page.getByLabel("Price (₹)").fill("349");
  await page.getByLabel("Valid for (days, 1–14)").fill("3");
  await page.getByLabel("Material").fill("PETG");
  await page.getByLabel("Layer height (microns, 50–400)").fill("200");
  await page.getByLabel("Recipe notes (walls, infill, orientation)").fill("4 walls, 20% infill, back plate flat on the bed");
  await page.waitForTimeout(1200);
  await page.getByRole("button", { name: "Send quote" }).click();
  await page.waitForLoadState("networkidle");
  await caption(page, "The quote is tied to this revision and expires. A new revision would withdraw it.", 3500);

  await signInAs(context, page, "customer");
  await page.goto(`${SITE}/requests/${requestId}`);
  await page.waitForLoadState("networkidle");
  await caption(page, "Customer: accepting freezes the quote and creates exactly one order (accepting again returns the same order).", 3500);
  await page.getByRole("button", { name: /^Accept .* and create order$/ }).click();
  await page.waitForURL(/\/orders\/[0-9a-f-]{36}$/, { timeout: 30000 });
  const orderId = page.url().split("/").pop();
  await page.waitForLoadState("networkidle");

  // 1:30 Verified test payment, separate release
  await caption(page, `Order ${short(orderId)}: pay in Razorpay's hosted Checkout (test card, test bank page).`, 3000);
  await payInCheckout(page, context);
  await caption(page, "Confirmed only by a verified, captured payment (signed webhook or the backend asking Razorpay), " +
    "never by the browser's word. Payment does not start production.", 5000);

  // 2:15 Uncertain start: lost acknowledgement, reconciled
  await signInAs(context, page, "operator");
  await page.goto(`${SITE}/ops/printers`);
  await page.waitForLoadState("networkidle");
  const clear = page.getByRole("button", { name: "Print removed: mark bed clear" });
  if (await clear.isVisible().catch(() => false)) {
    await caption(page, "Operator: the last print is still on the bed. Nothing clears it automatically; the operator confirms it was removed.", 3500);
    await clear.click();
    await page.waitForLoadState("networkidle");
    await caption(page, "Bed clear: that clearance belongs to that print and is used by the next start.", 3000);
  }
  // Through SSM, waiting until the simulator has the fault (the dev controls are not public).
  step(`fault set: ${await cp7.onInstance(`docker run --rm --network one3d_default curlimages/curl:8.17.0 -s -X POST -H 'Content-Type: application/json' -d '{"dropNextStartAck": true}' http://simulator:8090/dev/printers/${cp7.PRINTER}/faults`)}`);
  await caption(page, "Fault injected (through SSM): the simulator will start the next print but lose its acknowledgement.", 3500);

  await page.goto(`${SITE}/ops`);
  await page.waitForLoadState("networkidle");
  const row = page.locator("tr", { hasText: short(orderId) });
  await caption(page, "Operator queue: release is a separate, explicit operator action.", 2500);
  await row.getByRole("button", { name: "Release" }).click();
  await page.waitForLoadState("networkidle");
  const jobLink = row.getByRole("link", { name: /^Job / });
  await jobLink.waitFor({ timeout: 20000 });
  await jobLink.click();
  await page.waitForURL(/\/ops\/jobs\/[0-9a-f-]{36}$/);
  const jobId = page.url().split("/").pop();
  await page.waitForLoadState("networkidle");
  await caption(page, `Job ${short(jobId)}: start an attempt on the simulated printer.`, 2500);
  await page.getByRole("button", { name: "Start attempt" }).click();
  const seen = [];
  for (let i = 0; i < 40; i++) {
    const status = await attemptStatus(page, jobId);
    if (status && seen.at(-1) !== status) {
      seen.push(status);
      await page.reload();
      await page.waitForLoadState("networkidle");
      const explain = {
        START_INTENT: "START_INTENT: the backend recorded the intent before sending anything; a worker delivers it.",
        RECONCILIATION_REQUIRED: "RECONCILIATION_REQUIRED: no acknowledgement came back. The attempt keeps the printer; a timeout is NOT permission to start again.",
        RUNNING: "RUNNING: the worker asked the simulator's journal about the SAME command id; it had started, so nothing was sent twice.",
      }[status] || status;
      await caption(page, explain, 4500);
    }
    if (status === "RUNNING") break;
    await page.waitForTimeout(1000);
  }
  step(`attempt states seen: ${seen.join(" -> ")}`);

  // 3:15 QC and handoff
  step(`simulator.sh complete: ${await cp7.onInstance("bash /opt/one3d/app/infra/deploy/simulator.sh complete")}`);
  for (let i = 0; i < 40; i++) {
    if ((await cp7.json(page, "GET", `/api/ops/jobs/${jobId}`)).status === "AWAITING_QC") break;
    await page.waitForTimeout(1000);
  }
  await page.reload();
  await page.waitForLoadState("networkidle");
  await caption(page, "The simulated printer reported the print complete. Completion is not acceptance: the operator inspects the whole part.", 4000);
  await page.getByLabel("Notes (optional)").fill("Whole part checked: dimensions and finish");
  await page.getByRole("button", { name: "Passed" }).click();
  await page.waitForLoadState("networkidle");
  await caption(page, "QC passed. The bed stays occupied until someone removes the part and says so.", 3000);

  await page.goto(`${SITE}/ops`);
  await page.waitForLoadState("networkidle");
  await page.locator("tr", { hasText: short(orderId) }).getByRole("button", { name: "Ready for handoff" }).click();
  await page.waitForLoadState("networkidle");
  await page.getByLabel("Orders").selectOption("READY_FOR_HANDOFF");
  await page.waitForLoadState("networkidle");
  await caption(page, "Ready for handoff, then handed over when the customer collects it.", 2500);
  await page.locator("tr", { hasText: short(orderId) }).getByRole("button", { name: "Handed over" }).click();
  await page.waitForLoadState("networkidle");

  await signInAs(context, page, "customer");
  await page.goto(`${SITE}/orders/${orderId}`);
  await page.waitForLoadState("networkidle");
  await caption(page, "Customer: the order's progress, built from recorded facts (placed, paid, released, QC passed, ready, handed over).", 6000);

  // 4:00 Evidence
  if (process.env.GRAFANA_URL) {
    const password = execFileSync("aws", ["ssm", "get-parameter", "--name", "/one3d/demo/one3d-grafana-admin-password",
      "--with-decryption", "--query", "Parameter.Value", "--output", "text"], { env: AWS_ENV }).toString().trim();
    await page.goto(`${process.env.GRAFANA_URL}/login`);
    await page.getByTestId("data-testid Username input field").fill("admin");
    await page.getByTestId("data-testid Password input field").fill(password);
    await page.getByTestId("data-testid Login button").click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"));
    await page.goto(`${process.env.GRAFANA_URL}/d/one3d-overview/one3d-overview?orgId=1&from=now-15m&to=now`);
    await page.waitForTimeout(10000);
    await caption(page, "Private Grafana (through SSM port forwarding): the lost acknowledgement shows as an 'unknown' start delivery, then reconciled.", 7000);
  }
  await caption(page, "Evidence for tests, CI, deployment, Terraform, monitoring, restore and cost: docs/evidence/ in the repository. " +
    "Limits: one simulated printer, test-mode payments, one small demo instance.", 6000);

  const video = page.video();
  await context.close();
  await browser.close();
  fs.renameSync(await video.path(), path.join(OUT, "one3d-v1-demo.webm"));
  step(`order ${orderId}, request ${requestId}, job ${jobId}`);
  fs.writeFileSync(path.join(OUT, "record-demo.txt"), lines.join("\n") + "\n");
})().catch((e) => { console.error(e); process.exit(1); });
