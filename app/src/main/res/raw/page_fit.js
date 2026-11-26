/*
 * DeboPoly (app Android) : pages Lexis Poly entières sur téléphone.
 *
 * Injecté par l'app (addDocumentStartJavaScript, domaines autorisés seulement, hors version ordinateur). Sur un
 * écran étroit, l'en-tête du site (logo, recherche, nom de l'utilisateur, menu) est plus large que l'écran : il
 * déborde à gauche et à droite, et des éléments sont coupés. On mesure ce débordement en haut de la page et on
 * élargit la page d'autant (balise viewport `width=…`) : la WebView la dézoome pour qu'elle tienne à l'écran.
 * Plafonné à MAX_WIDTH (texte encore lisible), jamais réduit, recalculé à chaque rotation. Rien n'est envoyé :
 * tout se passe dans le navigateur.
 */
(function () {
  if (window.__lpFit) return;
  window.__lpFit = true;
  if (window.top !== window) return;

  var MAX_WIDTH = 600;
  var HEADER = 260; // hauteur (px CSS) de la zone mesurée : en-tête + menu du site
  var original = null; // contenu d'origine de la balise viewport
  var width = 0; // largeur imposée (0 = aucune)

  function meta() {
    var m = document.querySelector("meta[name=viewport]");
    if (!m && (document.head || document.documentElement)) {
      m = document.createElement("meta");
      m.name = "viewport";
      m.content = "width=device-width, initial-scale=1";
      (document.head || document.documentElement).appendChild(m);
    }
    if (m && original === null) original = m.getAttribute("content") || "";
    return m;
  }

  function deviceWidth() {
    var w = screen.width;
    if (window.matchMedia && matchMedia("(orientation: landscape)").matches) w = Math.max(screen.width, screen.height);
    else w = Math.min(screen.width, screen.height);
    return w;
  }

  function hidden(el) {
    var cs = getComputedStyle(el);
    return cs.visibility === "hidden" || cs.display === "none" || parseFloat(cs.opacity) === 0;
  }

  // Bloc parent qui coupe ce qui dépasse (menu : entrées masquées sous les icônes de droite) : renvoie la
  // largeur cachée à droite et ce bloc, ou null.
  function clipRight(el, r) {
    var a = el.parentElement;
    for (var d = 0; a && a !== document.body && d < 6; d++, a = a.parentElement) {
      var o = getComputedStyle(a).overflowX;
      if (o === "visible") continue;
      var hiddenPart = r.right - a.getBoundingClientRect().right;
      return hiddenPart > 0.5 ? { amount: hiddenPart, el: a } : null;
    }
    return null;
  }

  // Débordement horizontal de l'en-tête : éléments du haut de la page qui dépassent du bord gauche ou droit de
  // l'écran, ou du bloc qui les coupe. Les panneaux repliés entièrement hors écran sont ignorés.
  function overflow() {
    var vw = document.documentElement.clientWidth || window.innerWidth;
    var sy = window.scrollY || 0;
    var left = 0, right = 0, clippers = [];
    var els = document.body ? document.body.querySelectorAll("*") : [];
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el.childElementCount > 0 && !/^(A|BUTTON|IMG|SVG|INPUT)$/i.test(el.tagName)) continue;
      var r = el.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0 || r.height > 150) continue;
      if (r.top + sy > HEADER || r.bottom + sy < 0) continue;
      if (r.right <= 0 || r.left >= vw) continue;
      var clip = clipRight(el, r);
      if (r.left >= 0 && r.right <= vw && !clip) continue;
      if (hidden(el)) continue;
      left = Math.max(left, -r.left);
      right = Math.max(right, r.right - vw, clip ? clip.amount : 0);
      if (clip && clippers.indexOf(clip.el) < 0) clippers.push(clip.el);
    }
    return { extra: Math.ceil(left + right), clippers: clippers };
  }

  function apply(w) {
    var m = meta();
    if (!m) return;
    var c = w ? "width=" + w : original;
    if (m.getAttribute("content") !== c) m.setAttribute("content", c);
  }

  function fit() {
    if (!document.body) return;
    var dw = deviceWidth();
    if (dw >= MAX_WIDTH) {
      if (width) { width = 0; apply(0); }
      return;
    }
    var o = overflow();
    if (o.extra < 4) return;
    var current = width || document.documentElement.clientWidth || dw;
    var next = Math.min(MAX_WIDTH, Math.max(dw, current + o.extra + 8));
    if (next > current && next !== width) {
      width = next;
      apply(width);
    } else if (current >= MAX_WIDTH) {
      // Plafond atteint et menu encore coupé : il devient défilable du doigt (glisser vers la gauche).
      o.clippers.forEach(function (c) { c.style.setProperty("overflow-x", "auto", "important"); });
    }
  }

  // Le site construit une partie de l'en-tête en JavaScript : plusieurs passes, chacune peut révéler la suite.
  function schedule() {
    [0, 400, 1200, 2500, 5000].forEach(function (t) { setTimeout(fit, t); });
  }

  document.addEventListener("DOMContentLoaded", schedule);
  window.addEventListener("load", schedule);
  var lastDevice = 0;
  window.addEventListener("resize", function () {
    var dw = deviceWidth();
    if (dw === lastDevice) return;
    lastDevice = dw;
    // Rotation : on repart de la mise en page d'origine, puis on remesure.
    width = 0;
    apply(0);
    schedule();
  });
  lastDevice = deviceWidth();
})();
