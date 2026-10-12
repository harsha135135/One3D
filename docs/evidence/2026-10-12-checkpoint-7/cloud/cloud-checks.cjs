// Checkpoint 7: checks on the deployed demo that need a signed-in user, with
// real Cognito sign-in (password + TOTP), real S3 and Razorpay test mode. Run
// from the frontend directory (for Playwright) as the deployment user:
//   NODE_PATH=$PWD/node_modules node cloud-checks.cjs <part> <output dir>
// Parts:
//   files    sign-in, S3 enforcement and the upload path
//   order    places a catalog order and keeps its id (order-id.txt)
//   recheck* signs in again and reads the kept order and file
//   csp      opens every customer and operator page and reports CSP violations
//   pay*     places an order and pays it in Razorpay's hosted Checkout (test card,
//            test bank page), then reads what the database recorded, including
//            webhook deliveries from Razorpay (<part>-order-id.txt)
//   journey  the order paid by the "pay" part through production to handover; the
//            print is finished on the instance through SSM, as there is no printer
// The demo users' sign-in details are read from SSM and never printed. Payment
// ids are shortened in the output.
const { chromium } = require("@playwright/test");
const { execFileSync } = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const SITE = "https://demo.one3d.store";
const PRINTER = "0d3d0000-0000-4000-8000-000000000101";
const CONFIGURATION = "0d3d0000-0000-4000-8000-000000000002";
const [part, OUT] = process.argv.slice(2);
const AWS_ENV = { ...process.env, AWS_PROFILE: process.env.AWS_PROFILE || "one3d-deployer", AWS_REGION: "ap-south-1", AWS_PAGER: "" };
const log = [];
const say = (line) => { log.push(line); console.log(line); };
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const aws = (...args) => execFileSync("aws", args, { env: AWS_ENV }).toString().trim();
const short = (text) => String(text).replace(/\b(pay|order|evt)_[A-Za-z0-9]+/g, "$1_…");

function demoUser(role) {
  return JSON.parse(aws("ssm", "get-parameter", "--name", `/one3d/demo-users/${role}`,
    "--with-decryption", "--query", "Parameter.Value", "--output", "text"));
}

function totp(secret, step) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const c of secret.replace(/=+$/, "").toUpperCase()) bits += alphabet.indexOf(c).toString(2).padStart(5, "0");
  const key = Buffer.from(bits.match(/.{8}/g).map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const h = crypto.createHmac("sha1", key).update(counter).digest();
  const o = h[h.length - 1] & 15;
  return String(((h.readUInt32BE(o) & 0x7fffffff) % 1000000)).padStart(6, "0");
}

/** A code for a 30-second step not used before by this user (Cognito accepts each code once). */
async function freshTotp(role, secret) {
  const used = path.join(os.tmpdir(), `one3d-demo-totp-${role}`);
  const last = fs.existsSync(used) ? Number(fs.readFileSync(used, "utf8")) : 0;
  let step = Math.floor(Date.now() / 30000);
  while (step <= last) { await sleep(1000); step = Math.floor(Date.now() / 30000); }
  fs.writeFileSync(used, String(step));
  return totp(secret, step);
}

/** A browser context signed in through Cognito's hosted pages, recording CSP violations per page. */
async function signIn(browser, role) {
  const user = demoUser(role);
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript(() => {
    window.__csp = [];
    document.addEventListener("securitypolicyviolation", (e) =>
      window.__csp.push(`${e.violatedDirective} blocked ${e.blockedURI}`));
  });
  const page = await context.newPage();
  await page.goto(`${SITE}/oauth2/authorization/oidc`);
  await page.locator('input[name="username"]:visible').fill(user.email);
  await page.locator('input[name="password"]:visible').fill(user.password);
  await page.locator('input[name="signInSubmitButton"]:visible').click();
  await page.locator("#totpCodeInput").waitFor();
  await page.locator("#totpCodeInput").fill(await freshTotp(role, user.totp_secret));
  await page.locator('input[type="submit"]:visible, button[type="submit"]:visible').first().click();
  await page.waitForURL(`${SITE}/`);
  const me = await (await page.request.get(`${SITE}/api/me`)).json();
  say(`signed in through Cognito (password + TOTP) as the demo ${role}: roles ${me.roles.join(", ")}, payment mode ${me.paymentMode}`);
  return { context, page, user };
}

/** Opens a page and returns the CSP violations its document reported (the record starts empty on each page). */
async function visit(page, pathname) {
  await page.goto(`${SITE}${pathname}`);
  await page.waitForLoadState("networkidle");
  return page.evaluate(() => window.__csp);
}

async function api(page, method, pathname, body) {
  const cookies = await page.context().cookies(SITE);
  const xsrf = cookies.find((c) => c.name === "XSRF-TOKEN")?.value;
  const response = await page.request.fetch(`${SITE}${pathname}`, {
    method, data: body, maxRedirects: 0,
    headers: { "X-XSRF-TOKEN": xsrf ? decodeURIComponent(xsrf) : "", "Content-Type": "application/json" },
  });
  return response;
}
const json = async (page, method, pathname, body) => (await api(page, method, pathname, body)).json();

/** Runs shell commands on the demo instance through SSM Run Command and returns their output. */
async function onInstance(...commands) {
  const tf = path.resolve(__dirname, "../../../../infra/terraform/demo");
  const instance = execFileSync("terraform", [`-chdir=${tf}`, "output", "-raw", "instance_id"]).toString().trim();
  const id = aws("ssm", "send-command", "--instance-ids", instance, "--document-name", "AWS-RunShellScript",
    "--parameters", JSON.stringify({ commands, executionTimeout: ["300"] }), "--query", "Command.CommandId", "--output", "text");
  for (let i = 0; i < 100; i++) {
    await sleep(3000);
    let invocation;
    try {
      invocation = JSON.parse(aws("ssm", "get-command-invocation", "--command-id", id, "--instance-id", instance, "--output", "json"));
    } catch { continue; }
    if (["Pending", "InProgress", "Delayed"].includes(invocation.Status)) continue;
    if (invocation.Status !== "Success") throw new Error(`SSM command ${invocation.Status}: ${invocation.StandardErrorContent}`);
    return invocation.StandardOutputContent.trim();
  }
  throw new Error("SSM command timed out");
}

/** Read-only SQL on the demo database (ids are checked before they reach the query). */
function sql(query) {
  return onInstance(`docker exec one3d-db-1 psql -U one3d -d one3d -At -F ' | ' -c "${query}"`);
}
const uuid = (id) => { if (!/^[0-9a-f-]{36}$/.test(id)) throw new Error("not a uuid"); return id; };

function stl(triangles) {
  const b = Buffer.alloc(84 + 50 * triangles);
  b.write("one3d checkpoint 7 cloud check", 0, "ascii");
  b.writeUInt32LE(triangles, 80);
  crypto.randomFillSync(b, 84);
  return b;
}

async function put(url, headers, body) {
  const response = await fetch(url, { method: "PUT", headers, body });
  const text = await response.text();
  return `${response.status} ${(text.match(/<Code>([^<]+)<\/Code>/) || [])[1] || ""}`.trim();
}

async function filesPart(browser) {
  const { context, page } = await signIn(browser, "customer");
  const violations = [];
  for (const pathname of ["/", "/requests/new", "/orders", "/requests"]) violations.push(...await visit(page, pathname));
  await page.screenshot({ path: `${OUT}/customer-requests.png`, fullPage: true });

  const model = stl(200);
  const started = await json(page, "POST", "/api/files/uploads", { filename: "cloud check.stl", sizeBytes: model.length });
  const { url, headers } = started.upload;
  say(`presigned PUT goes to: ${new URL(url).origin}/uploads/<id> (expires ${started.upload.expiresAt})`);
  const tampered = url.replace(/(X-Amz-Signature=)(.)/, (m, p, c) => p + (c === "a" ? "b" : "a"));
  say(`S3, one byte more than signed: ${await put(url, headers, Buffer.concat([model, Buffer.from([0])]))}`);
  say(`S3, Content-Type text/html instead of the signed type: ${await put(url, { ...headers, "Content-Type": "text/html" }, model)}`);
  say(`S3, signature changed by one character: ${await put(tampered, headers, model)}`);
  say(`S3, exactly as signed: ${await put(url, headers, model)}`);

  const finalized = await json(page, "POST", `/api/files/${started.artifact.id}/finalize`);
  say(`finalize: ${finalized.status}, pinned version ${finalized.versionId ? "yes" : "no"}`);
  let checked;
  for (let i = 0; i < 30; i++) {
    checked = await json(page, "GET", `/api/files/${started.artifact.id}`);
    if (checked.status !== "VERIFYING") break;
    await page.waitForTimeout(1000);
  }
  const sha = crypto.createHash("sha256").update(model).digest("hex");
  say(`check on the instance (reading the pinned version from S3): ${checked.status}, sha256 matches: ${checked.sha256 === sha}`);
  const redirect = await api(page, "GET", `/api/files/${started.artifact.id}/download`);
  const location = redirect.headers()["location"];
  const download = await fetch(location);
  const bytes = Buffer.from(await download.arrayBuffer());
  say(`download: ${redirect.status()} to a link pinned to versionId ${location.includes("versionId=" + checked.versionId)}; ` +
      `S3 answered ${download.status}, ${download.headers.get("content-type")}, ` +
      `${download.headers.get("content-disposition")}, same bytes: ${bytes.equals(model)}`);
  say(`the same object without a signature: ${(await fetch(location.split("?")[0])).status}`);
  say(`CSP violations on the catalog, custom part, orders and requests pages: ${violations.length}${violations.length ? " " + JSON.stringify(violations) : ""}`);
  fs.writeFileSync(`${OUT}/files-artifact-id.txt`, started.artifact.id + "\n");
  await context.close();
}

/** Places a catalog order and keeps its id, to look for it again after containers or the instance restart. */
async function orderPart(browser) {
  const { context, page } = await signIn(browser, "customer");
  const order = await json(page, "POST", "/api/orders", { productConfigurationId: CONFIGURATION, quantity: 1 });
  fs.writeFileSync(`${OUT}/order-id.txt`, order.id + "\n");
  say(`placed order ${order.id.slice(0, 8)}: ${order.status}, ${order.total.amountMinor / 100} ${order.total.currency}`);
  await context.close();
}

/** Signs in again (sessions live in memory) and reads the kept order and file. */
async function recheckPart(browser) {
  const { context, page } = await signIn(browser, "customer");
  const orderId = fs.readFileSync(`${OUT}/order-id.txt`, "utf8").trim();
  const artifactId = fs.readFileSync(`${OUT}/files-artifact-id.txt`, "utf8").trim();
  const order = await api(page, "GET", `/api/orders/${orderId}`);
  const file = await api(page, "GET", `/api/files/${artifactId}`);
  say(`order ${orderId.slice(0, 8)}: HTTP ${order.status()} ${(await order.json()).status}; ` +
      `file ${artifactId.slice(0, 8)}: HTTP ${file.status()} ${(await file.json()).status}`);
  await context.close();
}

/** Every page of the site, as a customer and as an operator, under the deployed Content-Security-Policy. */
async function cspPart(browser) {
  const orderId = fs.readFileSync(`${OUT}/order-id.txt`, "utf8").trim();
  const pages = {
    customer: [["/", "customer-catalog"], ["/requests/new", null], ["/orders", null], [`/orders/${orderId}`, "customer-order"], ["/requests", null]],
    operator: [["/ops", "operator-queue"], ["/ops/printers", null], ["/ops/requests", null], ["/ops/payments", null]],
  };
  for (const [role, list] of Object.entries(pages)) {
    const { context, page } = await signIn(browser, role);
    for (const [pathname, shot] of list) {
      const violations = await visit(page, pathname);
      if (shot) await page.screenshot({ path: `${OUT}/${shot}.png`, fullPage: true });
      say(`  ${pathname.replace(orderId, "<order>")}: ${violations.length} CSP violations${violations.length ? " " + JSON.stringify(violations) : ""}`);
    }
    await context.close();
  }
}

async function dismissPrompts(page, frame) {
  for (const name of [/Maybe later/i, /Skip/i, /Continue without/i]) {
    const button = frame.getByRole("button", { name });
    if (await button.isVisible().catch(() => false)) { await button.click(); await page.waitForTimeout(1500); }
  }
}

/** A new order paid in Razorpay's hosted Checkout on the deployed site (test mode: test card, test bank page). */
async function payPart(browser) {
  const { context, page } = await signIn(browser, "customer");
  const order = await json(page, "POST", "/api/orders", { productConfigurationId: CONFIGURATION, quantity: 1 });
  fs.writeFileSync(`${OUT}/${part}-order-id.txt`, order.id + "\n");
  say(`placed order ${order.id.slice(0, 8)}: ${order.status}, ${order.total.amountMinor / 100} ${order.total.currency}`);
  await visit(page, `/orders/${order.id}`);
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
  await page.waitForTimeout(800);
  await frame.locator('input[placeholder*="MM"], input[name="card.expiry"]').first().fill("12 / 30");
  await frame.locator('input[placeholder*="CVV"], input[name="card.cvv"]').first().fill("123");
  say("Checkout opened from the order page; entered Razorpay's documented test card (Visa 4100 2800 0000 1007) and a dummy contact number");
  await page.screenshot({ path: `${OUT}/${part}-1-checkout.png` });
  const popupPromise = context.waitForEvent("page", { timeout: 30000 }).catch(() => null);
  await frame.locator("button:visible").filter({ hasText: /^(Continue|Pay)/ }).last().click();
  await page.waitForTimeout(3000);
  await dismissPrompts(page, frame);
  const bank = await popupPromise;
  if (!bank) throw new Error("No bank page opened");
  await bank.waitForURL((url) => url.href !== "about:blank", { timeout: 45000 });
  await bank.waitForLoadState("domcontentloaded");
  say(`Razorpay's test bank page: ${bank.url().replace(/\?.*/, "")}; chose Success (test mode only; no real bank or money)`);
  await bank.getByRole("button", { name: "Success" }).click();
  const paidAt = Date.now();
  await page.getByText(/Payment received|not received a confirmed payment/).waitFor({ timeout: 90000 });
  await page.waitForTimeout(1000);
  say(`back on the order page: ${await page.getByRole("status").first().innerText()}`);
  await page.screenshot({ path: `${OUT}/${part}-2-order-confirmed.png`, fullPage: true });
  const violations = await page.evaluate(() => window.__csp);
  say(`CSP violations on the order page during Checkout: ${violations.length}${violations.length ? " " + JSON.stringify(violations) : ""}`);
  const after = await json(page, "GET", `/api/orders/${order.id}`);
  say(`order: ${after.status}, confirmed by ${after.confirmationSource}`);

  // Razorpay retries webhooks, but the first delivery normally arrives within seconds.
  const id = uuid(order.id);
  let events = "";
  for (let i = 0; i < 12 && !/payment\.captured|order\.paid/.test(events); i++) {
    await sleep(5000);
    events = await sql(`SELECT e.event_type || ' | ' || e.outcome || ' | ' || round(extract(epoch FROM e.received_at - p.recorded_at))::text || ' s after the payment was recorded' FROM payment_event e JOIN payment p ON p.provider_order_id = e.provider_order_id WHERE p.order_id = '${id}' ORDER BY e.received_at`);
  }
  say(`payment recorded: ${short(await sql(`SELECT provider_payment_id || ' | ' || source || ' | ' || outcome || ' | ' || amount_minor || ' ' || currency FROM payment WHERE order_id = '${id}'`))}`);
  say(`webhook deliveries from Razorpay for this payment (signature checked; event, outcome, timing):${events ? "\n  " + events.split("\n").join("\n  ") : " none within 60 s"}`);
  say(`seconds from choosing Success to this check: ${Math.round((Date.now() - paidAt) / 1000)}`);
  await context.close();
}

/** The paid order through production: release, print (finished through SSM), QC, ready, handed over. */
async function journeyPart(browser) {
  const orderId = fs.readFileSync(`${OUT}/pay-order-id.txt`, "utf8").trim();
  const customer = await signIn(browser, "customer");
  const operator = await signIn(browser, "operator");
  const o = operator.page;
  const job = await json(o, "POST", `/api/ops/orders/${orderId}/release`);
  say(`released order ${orderId.slice(0, 8)}: job ${job.status}`);
  const printer = (await json(o, "GET", "/api/ops/printers")).find((p) => p.id === PRINTER);
  if (printer.bedCycleAttemptId) {
    const cleared = await json(o, "POST", `/api/ops/printers/${PRINTER}/clear-bed`, { attemptId: printer.bedCycleAttemptId });
    say(`operator confirmed the previous print was removed: bed ${cleared.bedState}`);
  }
  const started = await json(o, "POST", `/api/ops/jobs/${job.id}/attempts`, { printerId: PRINTER });
  const attemptId = started.attempts.at(-1).id;
  let current;
  for (let i = 0; i < 20; i++) {
    current = await json(o, "GET", `/api/ops/jobs/${job.id}`);
    if (current.attempts.at(-1).status !== "START_INTENT") break;
    await sleep(1000);
  }
  say(`attempt delivered to the simulator by the background worker: ${current.attempts.at(-1).status}`);
  say(`on the instance through SSM, simulator.sh complete: ${await onInstance("bash /opt/one3d/app/infra/deploy/simulator.sh complete")}`);
  for (let i = 0; i < 20; i++) {
    current = await json(o, "GET", `/api/ops/jobs/${job.id}`);
    if (current.status === "AWAITING_QC") break;
    await sleep(1000);
  }
  say(`job after the simulator reported completion: ${current.status}`);
  const qc = await json(o, "POST", `/api/ops/attempts/${attemptId}/qc`, { result: "PASSED", notes: "Whole-part check" });
  const ready = await json(o, "POST", `/api/ops/orders/${orderId}/ready-for-handoff`);
  const handed = await json(o, "POST", `/api/ops/orders/${orderId}/handed-over`);
  say(`QC: ${qc.status}; ready for handoff: ${ready.status}; handed over: ${handed.status}`);
  const timeline = await json(customer.page, "GET", `/api/orders/${orderId}/timeline`);
  say("the customer's timeline:\n" + timeline.map((f) => `  ${f.kind}${f.detail ? ` (${short(f.detail)})` : ""}`).join("\n"));
  await visit(customer.page, `/orders/${orderId}`);
  await customer.page.screenshot({ path: `${OUT}/journey-customer-order.png`, fullPage: true });
  await visit(o, `/ops/jobs/${job.id}`);
  await o.screenshot({ path: `${OUT}/journey-operator-job.png`, fullPage: true });
  await customer.context.close();
  await operator.context.close();
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const parts = { files: filesPart, order: orderPart, csp: cspPart, journey: journeyPart };
  const run = part.startsWith("recheck") ? recheckPart : part.startsWith("pay") ? payPart : parts[part];
  if (!run) throw new Error(`unknown part ${part}`);
  await run(browser);
  fs.writeFileSync(`${OUT}/${part}.txt`, log.join("\n") + "\n");
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
