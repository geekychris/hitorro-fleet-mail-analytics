// usage: node ui_routes.js <baseUrl> <out.json> <route>...   (SPA: visits each route, collects errors)
const { chromium } = require('playwright');
(async () => {
  const [base, out, ...routes] = process.argv.slice(2);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  const res = {};
  let cur = null;
  const ev = () => (res[cur] = res[cur] || { console: [], pageerrors: [], failed: [], http: [] });
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) ev().console.push(m.type() + ': ' + m.text().replace(/:\d{4,5}\//g, ':<PORT>/').slice(0, 300)); });
  page.on('pageerror', e => ev().pageerrors.push(String(e).slice(0, 300)));
  page.on('requestfailed', r => ev().failed.push(r.method() + ' ' + r.url().replace(base, '') + ' ' + (r.failure() || {}).errorText));
  page.on('response', r => { if (r.status() >= 400) ev().http.push(r.status() + ' ' + r.request().method() + ' ' + r.url().replace(base, '')); });
  for (const r of routes) {
    cur = r; ev();
    await page.goto(base + r, { waitUntil: 'networkidle' }).catch(e => ev().pageerrors.push('goto: ' + e));
    await page.waitForTimeout(1500);
    ev().title = await page.title();
    ev().textLength = (await page.textContent('body') || '').length > 0;
  }
  require('fs').writeFileSync(out, JSON.stringify(res, null, 2));
  await browser.close();
})();
