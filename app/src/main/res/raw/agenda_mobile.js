/*
 * DeboPoly (app Android) : agenda Lexis Poly lisible sur téléphone.
 *
 * Injecté par l'app (addDocumentStartJavaScript, domaines autorisés seulement), actif uniquement sur les pages
 * /Agenda/. L'agenda Lexis Poly est un Kendo UI Scheduler. En portrait étroit :
 * - vue « agenda » STANDARD de Kendo (liste des rendez-vous, regroupés par jour), ouverte sur aujourd'hui ;
 * - l'agenda occupe l'écran SOUS le menu du site (qui reste visible), par-dessus le panneau de filtres ;
 *   un bouton « Filtres » rend la page normale, « Agenda » y revient.
 * En paysage ou en version ordinateur, la vue d'origine revient. Tout se passe dans le navigateur : aucune
 * requête ajoutée ni modifiée.
 *
 * Leçon (v1.0.14) : une vue Kendo dérivée (liste d'un seul jour) n'affichait aucune donnée sur le vrai site.
 * S'en tenir aux vues standard de Kendo.
 */
(function () {
  if (window.__lpAgenda) return;
  window.__lpAgenda = true;
  if (!/\/agenda\//i.test(location.pathname)) return;

  var NARROW = 700;
  var CSS =
    "html.lp-agenda-full,html.lp-agenda-full body{overflow:hidden!important;}" +
    "html.lp-agenda-full .k-scheduler{position:fixed!important;left:0!important;" +
    "top:var(--lp-agenda-top,0px)!important;width:100vw!important;" +
    "height:calc(100% - var(--lp-agenda-top,0px))!important;max-width:none!important;" +
    "z-index:2147483000!important;box-sizing:border-box;margin:0!important;}" +
    "html.lp-agenda-narrow .k-scheduler-toolbar{flex-wrap:wrap!important;height:auto!important;row-gap:4px;}" +
    // Sélecteur de vue (liste déroulante sur mobile) : ne connaît pas toujours la vue ajoutée, il serait vide.
    "html.lp-agenda-narrow .k-scheduler-toolbar .k-views-dropdown{display:none!important;}" +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-table td," +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-table th{" +
    "font-size:15px!important;padding:8px 6px!important;line-height:1.35;white-space:normal!important;}" +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-datecolumn{width:58px!important;}" +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-timecolumn{width:76px!important;" +
    "font-size:13px!important;}" +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-agendaday{font-size:26px!important;}" +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-agendaweek," +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-agendadate{font-size:12px!important;}" +
    "#lp-agenda-toggle{position:fixed;right:12px;bottom:16px;z-index:2147483001;border:0;border-radius:20px;" +
    "padding:10px 16px;font:600 14px sans-serif;background:#1f3a5f;color:#fff;box-shadow:0 2px 8px rgba(0,0,0,.3);}";

  var full = true;

  function narrow() {
    return Math.min(window.innerWidth, document.documentElement.clientWidth || 1e9) < NARROW;
  }

  function addCss() {
    var host = document.head || document.documentElement;
    if (!host || document.getElementById("lp-agenda-css")) return;
    var s = document.createElement("style");
    s.id = "lp-agenda-css";
    s.textContent = CSS;
    host.appendChild(s);
  }

  function widget() {
    var $ = (window.kendo && window.kendo.jQuery) || window.jQuery;
    if (!$) return null;
    var els = document.querySelectorAll(".k-scheduler");
    for (var i = 0; i < els.length; i++) {
      var w = $(els[i]).data("kendoScheduler");
      if (w) return w;
    }
    return null;
  }

  function hasView(s, name) {
    if (s.views && Object.prototype.hasOwnProperty.call(s.views, name)) return true;
    return (s.options.views || []).some(function (v) {
      return v === name || (v && (v.type === name || v.name === name));
    });
  }

  // Ajoute la vue « agenda » standard si le cabinet ne l'a pas configurée (API interne Kendo `_views`).
  function ensureAgendaView(s) {
    if (hasView(s, "agenda")) return true;
    if (typeof s._views !== "function" || !s.options.views) return false;
    s.options.views.push({ type: "agenda", title: "Liste" });
    s._views();
    return hasView(s, "agenda");
  }

  // Haut de la zone plein écran : juste sous le menu du site. On prend le plus haut conteneur large de
  // l'agenda qui commence sous l'en-tête (le bloc de la page agenda), mesuré page remontée tout en haut.
  function agendaTop(s) {
    var top = 0;
    var el = s.element && s.element[0];
    while (el && el.parentElement && el.parentElement !== document.body) {
      el = el.parentElement;
      var r = el.getBoundingClientRect();
      if (r.top > 40 && r.width >= window.innerWidth * 0.8) top = r.top;
    }
    return Math.max(0, Math.min(Math.round(top), Math.round(window.innerHeight * 0.5)));
  }

  function setLayout(isNarrow, s) {
    var root = document.documentElement;
    root.classList.toggle("lp-agenda-narrow", isNarrow);
    if (isNarrow && full && s && !root.classList.contains("lp-agenda-full")) {
      window.scrollTo(0, 0);
      root.style.setProperty("--lp-agenda-top", agendaTop(s) + "px");
    }
    root.classList.toggle("lp-agenda-full", isNarrow && full);
    var btn = document.getElementById("lp-agenda-toggle");
    if (isNarrow && !btn && document.body) {
      btn = document.createElement("button");
      btn.id = "lp-agenda-toggle";
      btn.type = "button";
      btn.addEventListener("click", function () {
        full = !full;
        setLayout(narrow(), widget());
      });
      document.body.appendChild(btn);
    }
    if (btn) {
      btn.style.display = isNarrow ? "" : "none";
      btn.textContent = full ? "Filtres" : "Agenda";
    }
    // Kendo recalcule ses dimensions après le changement de taille du conteneur.
    if (s && typeof s.resize === "function") {
      try { s.resize(true); } catch (e) {}
    }
  }

  function adapt() {
    addCss();
    var s = widget();
    // Widget pas encore prêt (vues en cours de création) : on réessaie plus tard.
    if (!s || !s.view() || !s.views) return false;
    var current = s.view().name;
    var isNarrow = narrow();
    setLayout(isNarrow, s);
    if (isNarrow) {
      if (current === "agenda" || (current === "day" && s.__lpFallback)) return true;
      if (!s.__lpOriginal) {
        s.__lpOriginal = { name: current, index: Object.keys(s.views).indexOf(current) };
      }
      // Ouverture sur aujourd'hui (une seule fois : ensuite, l'utilisateur navigue librement).
      if (!s.__lpToday) {
        s.__lpToday = true;
        try { s.date(new Date()); } catch (e) {}
      }
      try {
        if (ensureAgendaView(s)) {
          s.view("agenda");
        } else {
          s.__lpFallback = true;
          s.view("day");
        }
      } catch (e) {
        try { s.__lpFallback = true; s.view("day"); } catch (e2) {}
      }
    } else if (s.__lpOriginal) {
      var original = s.__lpOriginal;
      s.__lpOriginal = null;
      s.__lpFallback = false;
      try { s.view(original.name); } catch (e) {}
      if (s.view().name !== original.name && original.index >= 0) {
        try { s.view(Object.keys(s.views)[original.index]); } catch (e) {}
      }
    }
    return true;
  }

  addCss();
  document.addEventListener("DOMContentLoaded", addCss);
  window.addEventListener("load", addCss);
  var tries = 0;
  var timer = setInterval(function () { if (adapt() || ++tries > 120) clearInterval(timer); }, 500);
  var resizeTimer = null;
  window.addEventListener("resize", function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(adapt, 300);
  });
})();
