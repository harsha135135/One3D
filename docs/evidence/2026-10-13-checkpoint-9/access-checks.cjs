// Checkpoint 9 on the deployed demo: a signed-in customer (Cognito, password and
// TOTP) is refused every operator endpoint, cannot change state without the
// CSRF token, and a request without a session is refused. Run from frontend/:
//   NODE_PATH=$PWD/node_modules node access-checks.cjs <out dir>
const { chromium } = require("@playwright/test");
const fs = require("fs");
const path = require("path");
const cp7 = require("../2026-10-12-checkpoint-7/cloud/cloud-checks.cjs");

const OUT = path.resolve(process.argv[2] || ".");
const say = (line) => { cp7.log.push(line); console.log(line); };
const someId = "00000000-0000-4000-8000-000000000000";

(async () => {
  const browser = await chromium.launch();
  const { context, page } = await cp7.signIn(browser, "customer");
  for (const [method, url] of [
    ["GET", "/api/ops/orders?status=CONFIRMED"], ["GET", "/api/ops/printers"], ["GET", "/api/ops/payments"],
    ["GET", "/api/ops/requests?status=SUBMITTED"], ["GET", "/api/ops/work?status=EXHAUSTED"],
    ["POST", `/api/ops/orders/${someId}/release`], ["POST", `/api/ops/attempts/${someId}/qc`],
  ]) {
    const response = await cp7.api(page, method, url, method === "POST" ? {} : undefined);
    say(`customer ${method} ${url}: HTTP ${response.status()} ${(await response.json().catch(() => ({}))).code || ""}`.trim());
  }
  const own = await cp7.api(page, "GET", "/api/orders?page=0&size=5");
  say(`customer GET /api/orders (own orders): HTTP ${own.status()}`);
  const noCsrf = await page.request.post(`${cp7.SITE}/api/orders`, {
    data: { productConfigurationId: "0d3d0000-0000-4000-8000-000000000002", quantity: 1 }, maxRedirects: 0 });
  say(`customer POST /api/orders without the CSRF header: HTTP ${noCsrf.status()}`);
  const anonymous = await (await browser.newContext()).request.get(`${cp7.SITE}/api/orders`, { maxRedirects: 0 });
  say(`no session GET /api/orders: HTTP ${anonymous.status()}`);
  await context.close();
  await browser.close();
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, "access-checks.txt"), cp7.log.join("\n") + "\n");
})().catch((e) => { console.error(e); process.exit(1); });
