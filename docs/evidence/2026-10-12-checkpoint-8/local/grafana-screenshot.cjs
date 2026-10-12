// Signs in to Grafana and saves a screenshot of the "One3D overview" dashboard
// (last 15 minutes). The password comes from GRAFANA_PASSWORD (default: the
// local development one) and is never printed.
//   NODE_PATH=frontend/node_modules node grafana-screenshot.cjs <grafana url> <png path>
const path = require("path");
const { chromium } = require(require.resolve("@playwright/test", {
  paths: [process.env.NODE_PATH || path.resolve(__dirname, "../../../../frontend/node_modules")],
}));

const [base, out] = process.argv.slice(2);
const password = process.env.GRAFANA_PASSWORD || "local-dev-only";

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 1500 } });
  await page.goto(`${base}/login`);
  await page.getByTestId("data-testid Username input field").fill("admin");
  await page.getByTestId("data-testid Password input field").fill(password);
  await page.getByTestId("data-testid Login button").click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
  await page.goto(`${base}/d/one3d-overview/one3d-overview?orgId=1&from=now-15m&to=now`);
  // Panels render lazily; wait until all twelve have headers, then for their data.
  for (let i = 0; i < 30; i++) {
    if (await page.locator('[data-testid^="data-testid Panel header"]').count() >= 12) break;
    await page.waitForTimeout(1000);
  }
  await page.waitForTimeout(8000);
  await page.screenshot({ path: out, fullPage: true });
  console.log(`saved ${path.basename(out)}`);
  await browser.close();
})().catch((e) => { console.error(e.message); process.exit(1); });
