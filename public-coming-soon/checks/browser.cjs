// Uses the existing frontend test dependency; the public page needs no runtime JS.
const { chromium } = require('../../frontend/node_modules/@playwright/test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const origin = process.env.PUBLIC_SITE_PREVIEW || 'http://127.0.0.1:4179';
  const base = path.resolve(__dirname, '..');
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();
  const failures = [];
  const requests = new Set();
  page.on('pageerror', error => failures.push(error.message));
  page.on('request', request => requests.add(request.url()));
  page.on('response', response => { if (response.status() >= 400) failures.push(`${response.status()} ${response.url()}`); });
  try {
    // Rasterize the authored SVG for broad social-preview compatibility.
    await page.setViewportSize({ width: 1200, height: 630 });
    await page.goto(`${origin}/assets/social.svg`);
    await page.screenshot({ path: path.join(base, 'dist/assets/social.png') });
    const viewports = [];
    for (const width of [320, 390, 780, 1024, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      const response = await page.goto(origin, { waitUntil: 'networkidle' });
      assert.equal(response.status(), 200);
      await page.getByRole('heading', { name: 'Ideas, taking shape.' }).waitFor();
      assert.equal(await page.locator('h1').count(), 1);
      assert.equal(await page.locator('script, form, iframe').count(), 0);
      assert.match(await page.locator('body').innerText(), /Orders are not open yet/);
      const layout = await page.evaluate(() => ({ viewport: window.innerWidth, content: document.documentElement.scrollWidth, brokenImages: [...document.images].filter(image => !image.complete || !image.naturalWidth).length }));
      assert.ok(layout.content <= width, `Horizontal overflow at ${width}`);
      assert.equal(layout.brokenImages, 0);
      const targets = await page.locator('a:not(.skip-link)').evaluateAll(links => links.map(link => ({ href: link.href, height: link.getBoundingClientRect().height })));
      assert.ok(targets.every(link => link.height >= 44), `Small link target at ${width}`);
      assert.ok(targets.every(link => !link.href.includes('demo.one3d.store')));
      await page.keyboard.press('Tab');
      assert.equal(await page.evaluate(() => document.activeElement.className), 'skip-link');
      await page.keyboard.press('Enter');
      assert.equal(await page.evaluate(() => document.activeElement.id), 'main');
      if ([390, 1440].includes(width)) await page.screenshot({ path: path.join(base, `review/preview-${width}.png`), fullPage: true });
      viewports.push({ width, horizontalOverflow: false, brokenImages: 0, minimumLinkHeight: 44, keyboardSkipLink: 'passed' });
    }
    await page.emulateMedia({ reducedMotion: 'reduce' });
    assert.equal(await page.evaluate(() => document.getAnimations().length), 0);
    for (const asset of ['/robots.txt', '/sitemap.xml', '/assets/favicon.svg', '/assets/social.png', '/404.html']) assert.equal((await context.request.get(`${origin}${asset}`)).status(), 200);
    assert.ok([...requests].every(url => url.startsWith(origin)), 'External request from static page');
    assert.deepEqual(failures, []);
    const result = { checkedAt: new Date().toISOString(), browser: await browser.version(), origin, viewports, reducedMotion: 'No animations', runtimeJavaScript: false, formsOrFrames: false, externalPageRequests: false, errors: failures, limitations: ['Python preview does not apply hosting _headers or _redirects', 'Hosting, HTTPS, custom DNS and social-network parsing remain unverified', 'No screen-reader or field-performance audit claimed'] };
    fs.writeFileSync(path.join(base, 'review/browser-results.json'), JSON.stringify(result, null, 2) + '\n');
    console.log(JSON.stringify(result, null, 2));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
