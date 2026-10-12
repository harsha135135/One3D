// Real Razorpay TEST-MODE payment through hosted Checkout, driven by Playwright:
// Razorpay's documented test card and its demo bank page ("Success"). No real
// money or bank is involved. Usage: node razorpay-test-payment.cjs <screenshot dir> <order id> <username>
const { chromium } = require("@playwright/test");
const fs = require("fs");
const OUT = process.argv[2], ORDER = process.argv[3], USER = process.argv[4] || "customer-pay";
const F = "http://localhost:3000";
const log = [];
const step = (s) => { log.push(s); console.log(s); };
async function dump(frame, label) {
  const inputs = await frame.locator("input:visible").evaluateAll((els) => els.map((e) => `${e.name}|${e.type}|${e.placeholder}`));
  const buttons = await frame.locator("button:visible").evaluateAll((els) => els.map((e) => e.innerText.trim()).filter(Boolean));
  console.log(`[${label}] inputs: ${inputs.join(", ")} | buttons: ${buttons.join(", ")}`);
}
(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${F}/oauth2/authorization/oidc`);
  await page.locator('input[name="username"]').fill(USER);
  await page.locator('input[type="submit"], button[type="submit"]').first().click();
  await page.waitForURL(`${F}/`);
  await page.goto(`${F}/orders/${ORDER}`);
  await page.screenshot({ path: `${OUT}/0-order-before.png`, fullPage: true });
  await page.getByRole("button", { name: /with Razorpay \(test mode\)/ }).click();
  step("Opened Razorpay Checkout from the order page");
  const frame = await (await page.waitForSelector("iframe.razorpay-checkout-frame", { timeout: 20000 })).contentFrame();
  await frame.locator('input[type="tel"]').first().waitFor({ timeout: 20000 });
  await page.waitForTimeout(1500);
  const mobile = frame.getByPlaceholder("Mobile number");
  if (await mobile.isVisible().catch(() => false)) {
    await mobile.fill("9000090000");
    await frame.getByRole("button", { name: "Continue" }).last().click();
    step("Entered a dummy contact number");
    await page.waitForTimeout(1500);
  }
  await dump(frame, "after contact");
  await frame.getByPlaceholder("Card Number").fill("4100 2800 0000 1007");
  await page.waitForTimeout(800);
  await dump(frame, "after card number");
  const expiry = frame.locator('input[placeholder*="MM"], input[name="card.expiry"]').first();
  await expiry.fill("12 / 30");
  const cvv = frame.locator('input[placeholder*="CVV"], input[name="card.cvv"]').first();
  await cvv.fill("123");
  step("Entered Razorpay's documented test card (Visa 4100 2800 0000 1007), a future expiry and a random CVV");
  await page.screenshot({ path: `${OUT}/1-card-entered.png` });
  await dump(frame, "before pay");
  const popupPromise = context.waitForEvent("page", { timeout: 20000 }).catch(() => null);
  const payButton = frame.locator("button:visible").filter({ hasText: /^(Continue|Pay)/ }).last();
  await payButton.click();
  await page.waitForTimeout(3000);
  await dump(frame, "after pay click");
  // Optional "save card" style prompts.
  for (const name of [/Maybe later/i, /Skip/i, /Continue without/i]) {
    const b = frame.getByRole("button", { name });
    if (await b.isVisible().catch(() => false)) { await b.click(); step(`Dismissed prompt: ${name}`); await page.waitForTimeout(1500); }
  }
  const popup = await popupPromise;
  if (!popup) throw new Error("No bank page opened");
  await popup.waitForURL((url) => url.href !== "about:blank", { timeout: 45000 });
  await popup.waitForLoadState("domcontentloaded");
  await popup.waitForTimeout(2000);
  await popup.screenshot({ path: `${OUT}/2-bank-page.png` });
  console.log("BANK URL:", popup.url().replace(/\?.*/, ""));
  console.log("BANK TEXT:", (await popup.locator("body").innerText()).slice(0, 500));
  const bankInputs = await popup.locator("input:visible").evaluateAll((els) => els.map((e) => `${e.name}|${e.type}|${e.placeholder}`));
  const bankButtons = await popup.locator("button:visible, input[type=submit]:visible").evaluateAll((els) => els.map((e) => (e.innerText || e.value || "").trim()));
  console.log("BANK INPUTS:", bankInputs.join(", "), "| BUTTONS:", bankButtons.join(", "));
  await popup.getByRole("button", { name: "Success" }).click();
  step("Chose Success on Razorpay's test bank page (test mode only; no real bank)");
  await page.getByText(/Payment received|not received a confirmed payment/).waitFor({ timeout: 60000 });
  await page.waitForTimeout(1000);
  step("Back on the order page: " + (await page.getByRole("status").first().innerText()));
  await page.screenshot({ path: `${OUT}/3-order-after.png`, fullPage: true });
  fs.writeFileSync(`${OUT}/steps.txt`, log.join("\n") + "\n");
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
