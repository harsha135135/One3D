// Checkpoint 7: checks on the deployed demo that need a signed-in user, with
// real Cognito sign-in (password + TOTP) and real S3. Run from the frontend
// directory (for Playwright) as the deployment user:
//   NODE_PATH=$PWD/node_modules node cloud-checks.cjs <part> <output dir>
// Parts: "files" (sign-in, CSP, S3 enforcement and the upload path),
//        "pay" (a Razorpay test payment in hosted Checkout; prints the order id).
// The demo users' sign-in details are read from SSM and never printed.
const { chromium } = require("@playwright/test");
const { execFileSync } = require("child_process");
const crypto = require("crypto");
const fs = require("fs");

const SITE = "https://demo.one3d.store";
const [part, OUT] = process.argv.slice(2);
const AWS_ENV = { ...process.env, AWS_PROFILE: process.env.AWS_PROFILE || "one3d-deployer", AWS_REGION: "ap-south-1" };
const log = [];
const say = (line) => { log.push(line); console.log(line); };

function demoUser(role) {
  return JSON.parse(execFileSync("aws", ["ssm", "get-parameter", "--name", `/one3d/demo-users/${role}`,
    "--with-decryption", "--query", "Parameter.Value", "--output", "text"], { env: AWS_ENV }).toString());
}

function totp(secret) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const c of secret.replace(/=+$/, "").toUpperCase()) bits += alphabet.indexOf(c).toString(2).padStart(5, "0");
  const key = Buffer.from(bits.match(/.{8}/g).map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30000)));
  const h = crypto.createHmac("sha1", key).update(counter).digest();
  const o = h[h.length - 1] & 15;
  return String(((h.readUInt32BE(o) & 0x7fffffff) % 1000000)).padStart(6, "0");
}

/** A browser context signed in through Cognito's hosted pages, recording CSP violations. */
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
  await page.locator("#totpCodeInput").fill(totp(user.totp_secret));
  await page.locator('input[type="submit"]:visible, button[type="submit"]:visible').first().click();
  await page.waitForURL(`${SITE}/`);
  const me = await (await page.request.get(`${SITE}/api/me`)).json();
  say(`signed in through Cognito (password + TOTP) as the demo ${role}: roles ${me.roles.join(", ")}, payment mode ${me.paymentMode}`);
  return { context, page, user };
}

async function api(page, method, path, body) {
  const cookies = await page.context().cookies(SITE);
  const xsrf = cookies.find((c) => c.name === "XSRF-TOKEN")?.value;
  const response = await page.request.fetch(`${SITE}${path}`, {
    method, data: body, maxRedirects: 0,
    headers: { "X-XSRF-TOKEN": xsrf ? decodeURIComponent(xsrf) : "", "Content-Type": "application/json" },
  });
  return response;
}

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
  for (const path of ["/", "/requests/new", "/orders", "/requests"]) {
    await page.goto(`${SITE}${path}`);
    await page.waitForLoadState("networkidle");
  }
  await page.screenshot({ path: `${OUT}/customer-catalog.png`, fullPage: true });

  const model = stl(200);
  const started = await (await api(page, "POST", "/api/files/uploads",
    { filename: "cloud check.stl", sizeBytes: model.length })).json();
  const { url, headers } = started.upload;
  say(`presigned PUT goes to: ${new URL(url).origin}/uploads/<id> (expires ${started.upload.expiresAt})`);
  const tampered = url.replace(/(X-Amz-Signature=)(.)/, (m, p, c) => p + (c === "a" ? "b" : "a"));
  say(`S3, one byte more than signed: ${await put(url, headers, Buffer.concat([model, Buffer.from([0])]))}`);
  say(`S3, Content-Type text/html instead of the signed type: ${await put(url, { ...headers, "Content-Type": "text/html" }, model)}`);
  say(`S3, signature changed by one character: ${await put(tampered, headers, model)}`);
  say(`S3, exactly as signed: ${await put(url, headers, model)}`);

  const finalized = await (await api(page, "POST", `/api/files/${started.artifact.id}/finalize`)).json();
  say(`finalize: ${finalized.status}, pinned version ${finalized.versionId ? "yes" : "no"}`);
  let checked;
  for (let i = 0; i < 30; i++) {
    checked = await (await api(page, "GET", `/api/files/${started.artifact.id}`)).json();
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

  const violations = await page.evaluate(() => window.__csp);
  say(`CSP violations on the catalog, custom part, orders and requests pages: ${violations.length}${violations.length ? " " + JSON.stringify(violations) : ""}`);
  fs.writeFileSync(`${OUT}/files-artifact-id.txt`, started.artifact.id + "\n");
  await context.close();
}

/** Places a catalog order and keeps its id, to look for it again after containers or the instance restart. */
async function orderPart(browser) {
  const { context, page } = await signIn(browser, "customer");
  const order = await (await api(page, "POST", "/api/orders",
    { productConfigurationId: "0d3d0000-0000-4000-8000-000000000002", quantity: 1 })).json();
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

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  if (part === "files") await filesPart(browser);
  else if (part === "order") await orderPart(browser);
  else if (part.startsWith("recheck")) await recheckPart(browser);
  else throw new Error(`unknown part ${part}`);
  fs.writeFileSync(`${OUT}/${part}.txt`, log.join("\n") + "\n");
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
