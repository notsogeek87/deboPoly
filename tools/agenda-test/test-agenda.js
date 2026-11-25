// Test du script agenda (res/raw/agenda_mobile.js) sur une maquette Kendo UI Scheduler.
// Usage : node tools/agenda-test/test-agenda.js   (Playwright + Chromium requis ; Kendo chargé depuis son CDN)
// Vérifie : portrait → vue « agenda » plein écran ; paysage → vue d'origine ; retour portrait → « agenda ».
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../..');
const script = fs.readFileSync(path.join(root, 'app/src/main/res/raw/agenda_mobile.js'), 'utf8');
const page = path.join(__dirname, 'maquette-agenda.html');
const url = 'https://www.lexispoly.fr/lexispoly/Agenda/Agenda/GeneralAgendaPlanning';

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
  await ctx.route('https://www.lexispoly.fr/**', (r) => r.fulfill({ path: page, contentType: 'text/html' }));
  await ctx.addInitScript(script);
  const p = await ctx.newPage();
  const errors = [];
  p.on('pageerror', (e) => errors.push(e.message));
  await p.goto(url);
  await p.waitForTimeout(2500);
  const view = () => p.evaluate(() => window.jQuery('.k-scheduler').data('kendoScheduler').view().name);
  const full = () => p.evaluate(() => document.documentElement.classList.contains('lp-agenda-full'));
  const results = [];
  results.push(['portrait', await view(), await full()]);
  await p.setViewportSize({ width: 915, height: 412 });
  await p.waitForTimeout(1200);
  results.push(['paysage', await view(), await full()]);
  await p.setViewportSize({ width: 412, height: 915 });
  await p.waitForTimeout(1200);
  results.push(['portrait', await view(), await full()]);
  await browser.close();
  console.log(results, errors);
  const ok =
    errors.length === 0 &&
    results[0][1] === 'agenda' && results[0][2] &&
    results[1][1] === 'week' && !results[1][2] &&
    results[2][1] === 'agenda';
  console.log(ok ? 'OK' : 'ÉCHEC');
  process.exit(ok ? 0 : 1);
})();
