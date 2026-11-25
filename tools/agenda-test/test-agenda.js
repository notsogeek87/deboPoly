// Test du script agenda (app/src/main/res/raw/agenda_mobile.js) sur une maquette Kendo UI Scheduler (fr-FR,
// groupée par collaborateur, ouverte volontairement sur J+3).
// Usage : node tools/agenda-test/test-agenda.js   (Playwright + Chromium ; Kendo chargé depuis son CDN)
// Vérifie : portrait → « liste du jour » sur AUJOURD'HUI, page non plein écran ; flèche → jour suivant ;
// jour vide → message ; paysage → vue d'origine ; retour portrait → liste du jour.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../..');
const script = fs.readFileSync(path.join(root, 'app/src/main/res/raw/agenda_mobile.js'), 'utf8');
const page = path.join(__dirname, 'maquette-agenda.html');
const url = 'https://www.lexispoly.fr/lexispoly/Agenda/Agenda/GeneralAgendaPlanning';
const shots = process.argv[2];

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({
    viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true, deviceScaleFactor: shots ? 2 : 1,
  });
  await ctx.route('https://www.lexispoly.fr/**', (r) => r.fulfill({ path: page, contentType: 'text/html' }));
  await ctx.addInitScript(script);
  const p = await ctx.newPage();
  const errors = [];
  p.on('pageerror', (e) => errors.push(e.message));
  await p.goto(url);
  await p.waitForTimeout(2500);

  const state = () => p.evaluate(() => {
    const s = window.jQuery('.k-scheduler').data('kendoScheduler');
    const d = s.date();
    const t = new Date();
    return {
      view: s.view() instanceof window.kendo.ui.LpDayListView ? 'lpDayList' : s.view().name,
      today: d.toDateString() === t.toDateString(),
      offset: Math.round((new Date(d).setHours(0, 0, 0, 0) - new Date(t).setHours(0, 0, 0, 0)) / 864e5),
      tasks: document.querySelectorAll('.k-scheduler-content .k-task').length,
      empty: !!document.querySelector('.lp-agenda-empty'),
      title: (document.querySelector('.k-nav-current .k-sm-date-format') || {}).textContent,
      fixed: getComputedStyle(document.querySelector('.k-scheduler')).position === 'fixed',
    };
  });
  const r = {};
  r.portrait = await state();
  if (shots) await p.screenshot({ path: path.join(shots, 'jour.png') });
  await p.click('.k-nav-next');
  await p.waitForTimeout(600);
  r.next = await state();
  for (let i = 0; i < 3; i++) { await p.click('.k-nav-next'); await p.waitForTimeout(400); }
  r.emptyDay = await state();
  if (shots) await p.screenshot({ path: path.join(shots, 'vide.png') });
  await p.setViewportSize({ width: 915, height: 412 });
  await p.waitForTimeout(1200);
  r.landscape = await state();
  await p.setViewportSize({ width: 412, height: 915 });
  await p.waitForTimeout(1200);
  r.portraitAgain = await state();
  await browser.close();
  console.log(JSON.stringify(r, null, 1), errors);

  const ok =
    errors.length === 0 &&
    r.portrait.view === 'lpDayList' && r.portrait.today && !r.portrait.fixed && r.portrait.tasks === 2 &&
    r.next.view === 'lpDayList' && r.next.offset === 1 && r.next.tasks === 1 &&
    r.emptyDay.tasks === 0 && r.emptyDay.empty &&
    r.landscape.view === 'week' && !r.landscape.empty &&
    r.portraitAgain.view === 'lpDayList';
  console.log(ok ? 'OK' : 'ÉCHEC');
  process.exit(ok ? 0 : 1);
})();
