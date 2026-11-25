// Test du script agenda (app/src/main/res/raw/agenda_mobile.js) sur une maquette Kendo UI Scheduler (fr-FR,
// groupée par collaborateur, ouverte volontairement sur J+3).
// Usage : node tools/agenda-test/test-agenda.js   (Playwright + Chromium ; Kendo chargé depuis son CDN)
// Vérifie : portrait → vue « agenda » standard ouverte sur aujourd'hui, plein écran SOUS le menu du site ;
// flèches = ± 1 semaine, période visible, bouton Retour ; bouton Filtres ↔ Agenda ; paysage → vue d'origine ; retour portrait → agenda.
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
    const el = document.querySelector('.k-scheduler');
    const r = el.getBoundingClientRect();
    const t = new Date();
    return {
      view: s.view().name,
      start: s.view().startDate().toDateString() === t.toDateString(),
      tasks: document.querySelectorAll('.k-scheduler-content .k-task').length,
      fixed: getComputedStyle(el).position === 'fixed',
      top: Math.round(r.top),
      width: Math.round(r.width),
      menuVisible: document.querySelector('.nav').getBoundingClientRect().bottom <= r.top,
      button: (document.getElementById('lp-agenda-toggle') || {}).textContent,
      back: !!document.getElementById('lp-agenda-back'),
      period: ((document.querySelector('.k-nav-current .k-sm-date-format') || {}).textContent || '').trim(),
      periodVisible: !!(document.querySelector('.k-nav-current .k-sm-date-format') || {}).offsetWidth,
      offset: Math.round((new Date(s.view().startDate()).setHours(0,0,0,0) - new Date().setHours(0,0,0,0)) / 864e5),
    };
  });
  const r = {};
  r.portrait = await state();
  if (shots) await p.screenshot({ path: path.join(shots, 'agenda.png') });
  await p.click('.k-nav-next');
  await p.waitForTimeout(800);
  r.nextWeek = await state();
  await p.click('.k-nav-prev');
  await p.waitForTimeout(800);
  r.prevWeek = await state();
  await p.tap('#lp-agenda-toggle');
  await p.waitForTimeout(600);
  r.filters = await state();
  if (shots) await p.screenshot({ path: path.join(shots, 'filtres.png') });
  await p.tap('#lp-agenda-toggle');
  await p.waitForTimeout(600);
  r.back = await state();
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
    r.portrait.view === 'agenda' && r.portrait.start && r.portrait.fixed && r.portrait.menuVisible &&
    r.portrait.width === 412 && r.portrait.tasks >= 2 && r.portrait.button === 'Filtres' &&
    r.portrait.back && r.portrait.periodVisible && r.portrait.period.length > 0 &&
    r.nextWeek.offset === 7 && r.prevWeek.offset === 0 &&
    !r.filters.fixed && r.filters.button === 'Agenda' &&
    r.back.fixed && r.back.menuVisible &&
    r.landscape.view === 'week' && !r.landscape.fixed &&
    r.portraitAgain.view === 'agenda' && r.portraitAgain.fixed;
  console.log(ok ? 'OK' : 'ÉCHEC');
  process.exit(ok ? 0 : 1);
})();
