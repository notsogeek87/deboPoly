// Test du script res/raw/page_fit.js sur une maquette de l'accueil Lexis Poly (en-tête plus large que l'écran).
// Usage : node tools/page-fit-test/test-page-fit.js [dossier-captures]
// Vérifie : en portrait, plus aucun élément de l'en-tête ni du menu coupé ; panneau replié hors écran ignoré ;
// largeur plafonnée (menu trop long : défilable)  ; en paysage, mise en page d'origine.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../..');
const script = fs.readFileSync(path.join(root, 'app/src/main/res/raw/page_fit.js'), 'utf8');
const shots = process.argv[2];

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({
    viewport: { width: 412, height: 915 }, screen: { width: 412, height: 915 },
    isMobile: true, hasTouch: true, deviceScaleFactor: shots ? 2 : 1,
  });
  await ctx.route('https://www.lexispoly.fr/**', (r) => r.fulfill({ path: path.join(__dirname, 'maquette-accueil.html'), contentType: 'text/html' }));
  await ctx.addInitScript(script);
  const p = await ctx.newPage();
  const errors = [];
  p.on('pageerror', (e) => errors.push(e.message));
  const state = () => p.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const cut = [...document.querySelectorAll('.logo,.search,.user,.nav li,.icons span')]
      .filter((e) => { const r = e.getBoundingClientRect(); const ul = e.closest('ul');
        if (ul && /auto|scroll/.test(getComputedStyle(ul).overflowX)) return false; // menu défilable
        const lim = ul ? ul.getBoundingClientRect().right : vw; return r.left < 0 || r.right > Math.min(vw, lim) + 0.5; })
      .map((e) => e.textContent.trim());
    const ul = document.querySelector('.nav ul');
    return { menuScroll: getComputedStyle(ul).overflowX, viewport: document.querySelector('meta[name=viewport]').content, width: vw, cut };
  });
  await p.goto('https://www.lexispoly.fr/lexispoly/');

  await p.waitForTimeout(6000);
  const portrait = await state();
  if (shots) await p.screenshot({ path: path.join(shots, 'accueil.png') });
  await p.setViewportSize({ width: 915, height: 412 });
  await p.waitForTimeout(6000);
  const landscape = await state();
  await browser.close();
  console.log(JSON.stringify({ portrait, landscape }, null, 1), errors);
  const ok = errors.length === 0 && portrait.cut.length === 0 && portrait.width > 412 &&
    portrait.width <= 600 && landscape.viewport === 'initial-scale=1.0';
  console.log(ok ? 'OK' : 'ÉCHEC');
  process.exit(ok ? 0 : 1);
})();
