/*
 * DeboPoly (app Android) : agenda Lexis Poly lisible sur téléphone.
 *
 * Injecté par l'app (addDocumentStartJavaScript, domaines autorisés seulement), actif uniquement sur les pages
 * /Agenda/. L'agenda Lexis Poly est un Kendo UI Scheduler. En portrait étroit :
 * - vue « agenda » STANDARD de Kendo (liste des rendez-vous, regroupés par jour), ouverte sur aujourd'hui ;
 * - l'agenda occupe l'écran SOUS le menu du site (qui reste visible), par-dessus le panneau de filtres ;
 *   un bouton « Filtres » rend la page normale, « Agenda » y revient.
 * Liste = semaine du lundi au dimanche (« Semaine du … au … » dans la barre), flèches = semaine précédente /
 * suivante ; bouton « Retour ».
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
    // Période affichée toujours visible dans la barre de l'agenda (Kendo la masque sur écran étroit).
    "html.lp-agenda-narrow .k-scheduler-toolbar .k-nav-current{display:inline-flex!important;}" +
    "html.lp-agenda-narrow .k-scheduler-toolbar .k-nav-current .k-lg-date-format{display:none!important;}" +
    "html.lp-agenda-narrow .k-scheduler-toolbar .k-nav-current .k-sm-date-format{display:inline!important;" +
    "font-weight:600;font-size:15px;}" +
    "#lp-agenda-back{position:fixed;left:12px;bottom:16px;z-index:2147483001;border:0;border-radius:20px;" +
    "padding:10px 16px;font:600 14px sans-serif;background:#fff;color:#1f3a5f;box-shadow:0 2px 8px rgba(0,0,0,.3);}" +
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

  function addDays(d, n) {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  }

  function monday(d) {
    return addDays(d, -((d.getDay() + 6) % 7));
  }

  function fmt(d, pattern) {
    try {
      if (window.kendo && window.kendo.toString) return window.kendo.toString(d, pattern);
    } catch (e) {}
    return d.toLocaleDateString("fr-FR");
  }

  // « Semaine du 23 au 29 novembre 2026 » / « du 30 novembre au 6 décembre 2026 » / années différentes.
  function weekTitle(s) {
    var v = s.view();
    if (!v || v.name !== "agenda" || !narrow()) return;
    var a = monday(v.startDate()), b = addDays(a, 6);
    var text;
    if (a.getFullYear() !== b.getFullYear()) text = fmt(a, "d MMMM yyyy") + " au " + fmt(b, "d MMMM yyyy");
    else if (a.getMonth() !== b.getMonth()) text = fmt(a, "d MMMM") + " au " + fmt(b, "d MMMM yyyy");
    else text = a.getDate() + " au " + fmt(b, "d MMMM yyyy");
    var spans = s.element[0].querySelectorAll(".k-nav-current .k-sm-date-format, .k-nav-current .k-lg-date-format");
    for (var i = 0; i < spans.length; i++) spans[i].textContent = "Semaine du " + text;
  }

  // La vue liste de Kendo va jusqu'au lundi suivant 00:00 inclus : les rendez-vous « journée entière » de ce
  // lundi-là apparaîtraient dans la semaine. On masque les lignes qui commencent après le dimanche.
  function hideNextMonday(s) {
    var v = s.view();
    if (!v || v.name !== "agenda" || typeof s.occurrenceByUid !== "function") return;
    var end = addDays(monday(v.startDate()), 7);
    var tasks = s.element[0].querySelectorAll(".k-scheduler-content .k-task[data-uid]");
    for (var i = 0; i < tasks.length; i++) {
      var ev = null;
      try { ev = s.occurrenceByUid(tasks[i].getAttribute("data-uid")); } catch (e) {}
      var row = tasks[i].closest("tr");
      if (ev && ev.start && row && ev.start >= end) row.style.display = "none";
    }
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
    // « Retour » : quitte l'agenda vers la page précédente du site (le menu est masqué en plein écran).
    var back = document.getElementById("lp-agenda-back");
    if (isNarrow && !back && document.body) {
      back = document.createElement("button");
      back.id = "lp-agenda-back";
      back.type = "button";
      back.textContent = "\u2190 Retour";
      back.addEventListener("click", function () { history.back(); });
      document.body.appendChild(back);
    }
    if (back) back.style.display = isNarrow ? "" : "none";
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
    if (!s.__lpNav) {
      s.__lpNav = true;
      // En vue liste : toujours une semaine du lundi au dimanche. Flèches = semaine précédente / suivante,
      // « Aujourd'hui » et le mini-calendrier = semaine de la date choisie. Événement standard `navigate`.
      s.bind("navigate", function (e) {
        if (!e || !s.view() || s.view().name !== "agenda") return;
        var target;
        var start = monday(s.view().startDate());
        if (e.action === "next") target = addDays(start, 7);
        else if (e.action === "previous") target = addDays(start, -7);
        else if (e.action === "today") target = monday(new Date());
        else if (e.action === "changeDate" && e.date) target = monday(e.date);
        else return;
        e.preventDefault();
        setTimeout(function () { s.date(target); }, 0);
      });
      // Titre « Semaine du … au … » dans la barre de l'agenda, après chaque affichage.
      s.bind("dataBound", function () {
        weekTitle(s);
        hideNextMonday(s);
      });
    }
    var current = s.view().name;
    var isNarrow = narrow();
    setLayout(isNarrow, s);
    if (isNarrow) {
      if (current === "agenda" || (current === "day" && s.__lpFallback)) {
        weekTitle(s);
        return true;
      }
      if (!s.__lpOriginal) {
        s.__lpOriginal = { name: current, index: Object.keys(s.views).indexOf(current) };
      }
      // Ouverture sur aujourd'hui (une seule fois : ensuite, l'utilisateur navigue librement).
      if (!s.__lpToday) {
        s.__lpToday = true;
        try { s.date(monday(new Date())); } catch (e) {}
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
