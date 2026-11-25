/*
 * DeboPoly (app Android) : agenda Lexis Poly lisible sur téléphone.
 *
 * Injecté par l'app (addDocumentStartJavaScript, domaines autorisés seulement), actif uniquement sur les pages
 * /Agenda/. L'agenda Lexis Poly est un Kendo UI Scheduler. En portrait étroit, il passe en « liste du jour » :
 * les rendez-vous d'UNE journée, ouverte sur aujourd'hui, date écrite en toutes lettres, flèches jour par jour,
 * message si la journée est vide. Le reste de la page (menu du site) ne bouge pas. En paysage ou en version
 * ordinateur, la vue d'origine revient. Tout se passe dans le navigateur : aucune requête ajoutée ni modifiée.
 */
(function () {
  if (window.__lpAgenda) return;
  window.__lpAgenda = true;
  if (!/\/agenda\//i.test(location.pathname)) return;

  var NARROW = 700;
  var VIEW_CLASS = "LpDayListView";
  var EMPTY_TEXT = "Aucun rendez-vous ce jour.";

  var CSS =
    "html.lp-agenda-narrow .k-scheduler{width:100%!important;max-width:100%!important;box-sizing:border-box;}" +
    "html.lp-agenda-narrow .k-scheduler-toolbar{flex-wrap:wrap!important;height:auto!important;row-gap:4px;}" +
    // Date du jour toujours visible dans la barre de l'agenda (Kendo la masque sur écran étroit).
    "html.lp-agenda-narrow .k-scheduler-toolbar .k-nav-current{display:inline-flex!important;}" +
    "html.lp-agenda-narrow .k-scheduler-toolbar .k-nav-current .k-lg-date-format{display:none!important;}" +
    "html.lp-agenda-narrow .k-scheduler-toolbar .k-nav-current .k-sm-date-format{display:inline!important;" +
    "font-weight:600;font-size:16px;text-transform:capitalize;}" +
    // Une seule journée : la colonne « Date » est redondante.
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-datecolumn{display:none!important;}" +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-table td," +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-table th{" +
    "font-size:15px!important;padding:10px 8px!important;line-height:1.35;white-space:normal!important;}" +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-timecolumn{width:96px!important;}" +
    "html.lp-agenda-narrow .k-scheduler-agendaview .k-scheduler-groupcolumn{width:30%!important;}" +
    // Sélecteur de vue (liste déroulante sur mobile) : ne connaît pas la liste du jour, il apparaîtrait vide.
    "html.lp-agenda-narrow .k-scheduler-toolbar .k-views-dropdown{display:none!important;}" +
    ".lp-agenda-empty{padding:24px 12px;text-align:center;font-size:15px;color:#666;}";

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

  function jq() {
    return (window.kendo && window.kendo.jQuery) || window.jQuery || null;
  }

  function widget() {
    var $ = jq();
    if (!$) return null;
    var els = document.querySelectorAll(".k-scheduler");
    for (var i = 0; i < els.length; i++) {
      var w = $(els[i]).data("kendoScheduler");
      if (w) return w;
    }
    return null;
  }

  /** Vue « liste » de Kendo limitée à une journée (au lieu de 7), titre = date du jour en toutes lettres. */
  function defineDayList() {
    var ui = window.kendo && window.kendo.ui;
    if (!ui || !ui.AgendaView) return false;
    if (ui[VIEW_CLASS]) return true;
    var $ = jq();
    var base = ui.AgendaView;
    ui[VIEW_CLASS] = base.extend({
      options: $.extend({}, base.fn.options, {
        name: "lpDayList",
        title: "Jour",
        selectedDateFormat: "{0:D}",
        selectedShortDateFormat: "{0:D}",
        selectedMobileDateFormat: "{0:D}",
      }),
      // Fin de la journée affichée (23:59:59,999) : Kendo inclut les rendez-vous dont le jour de fin est
      // <= endDate ; minuit du lendemain y ferait entrer les rendez-vous du lendemain.
      endDate: function () {
        return new Date(window.kendo.date.addDays(this.startDate(), 1).getTime() - 1);
      },
      // La vue liste de Kendo fixe aussi sa borne interne à J+7 : on la ramène à la journée.
      _renderLayout: function (date) {
        base.fn._renderLayout.call(this, date);
        this._endDate = this.endDate();
      },
      // Les rendez-vous sont regroupés par jour : on ne garde que le groupe du jour affiché.
      _tasks: function (events) {
        var groups = base.fn._tasks.call(this, events);
        var day = window.kendo.date.getDate(this.startDate()).getTime();
        return groups.filter(function (g) {
          return !g.value || window.kendo.date.getDate(g.value).getTime() === day;
        });
      },
    });
    return true;
  }

  function isDayList(v) {
    var ui = window.kendo && window.kendo.ui;
    return !!(v && ui && ui[VIEW_CLASS] && v instanceof ui[VIEW_CLASS]);
  }

  function dayListKey(s) {
    var keys = Object.keys(s.views || {});
    for (var i = 0; i < keys.length; i++) {
      if (keys[i].indexOf(VIEW_CLASS) >= 0) return keys[i];
    }
    return null;
  }

  // Ajoute la « liste du jour » aux vues du calendrier (API interne Kendo `_views`, côté navigateur).
  function ensureDayList(s) {
    var key = dayListKey(s);
    if (key) return key;
    if (!defineDayList() || typeof s._views !== "function" || !s.options.views) return null;
    s.options.views.push({ type: "kendo.ui." + VIEW_CLASS });
    s._views();
    return dayListKey(s);
  }

  // Si l'agenda partage une ligne avec un autre bloc du site (filtres, menu latéral), ce bloc passe au-dessus
  // en portrait au lieu d'écraser l'agenda. Rien n'est masqué ; tout est rétabli en paysage.
  var touched = [];

  function unsqueeze(s, enable) {
    touched.forEach(function (t) { t.el.style.cssText = t.css; });
    touched = [];
    if (!enable || !s.element) return;
    var child = s.element[0];
    for (var depth = 0; depth < 12 && child && child.parentElement && child !== document.body; depth++) {
      var parent = child.parentElement;
      var ps = getComputedStyle(parent), cs = getComputedStyle(child);
      if (/flex/.test(ps.display) && !/column/.test(ps.flexDirection)) {
        touched.push({ el: parent, css: parent.style.cssText }, { el: child, css: child.style.cssText });
        parent.style.flexWrap = "wrap";
        child.style.flex = "1 1 100%";
        child.style.maxWidth = "100%";
        child.style.minWidth = "0";
      } else if (cs.float !== "none" || /table-cell/.test(cs.display)) {
        touched.push({ el: child, css: child.style.cssText });
        child.style.float = "none";
        child.style.display = "block";
        child.style.width = "100%";
      } else if (child.offsetWidth > 0 && child.offsetWidth < parent.clientWidth * 0.8) {
        touched.push({ el: child, css: child.style.cssText });
        child.style.width = "100%";
        child.style.maxWidth = "100%";
        child.style.marginLeft = "0";
      }
      child = parent;
    }
  }

  function showEmptyMessage(s) {
    var root = s.element && s.element[0];
    if (!root) return;
    var old = root.querySelector(".lp-agenda-empty");
    if (old) old.parentNode.removeChild(old);
    var v = s.view();
    if (!isDayList(v) || root.querySelector(".k-scheduler-content .k-task")) return;
    var content = root.querySelector(".k-scheduler-content") || root;
    var msg = document.createElement("div");
    msg.className = "lp-agenda-empty";
    msg.textContent = EMPTY_TEXT;
    content.appendChild(msg);
  }

  function adapt() {
    addCss();
    var s = widget();
    // Widget pas encore prêt (vues en cours de création) : on réessaie plus tard.
    if (!s || !s.view() || !s.views) return false;
    if (!s.__lpBound) {
      s.__lpBound = true;
      s.bind("dataBound", function () { showEmptyMessage(s); });
    }
    var isNarrow = narrow();
    document.documentElement.classList.toggle("lp-agenda-narrow", isNarrow);
    if (isNarrow !== s.__lpNarrow) {
      s.__lpNarrow = isNarrow;
      unsqueeze(s, isNarrow);
      try { s.resize(true); } catch (e) {}
    }
    var current = s.view().name;

    if (isNarrow) {
      if (isDayList(s.view()) || (current === "day" && s.__lpFallback)) {
        showEmptyMessage(s);
        return true;
      }
      if (!s.__lpOriginal) {
        s.__lpOriginal = { name: current, index: Object.keys(s.views).indexOf(current) };
      }
      // Toujours ouvrir sur aujourd'hui (une seule fois : ensuite, l'utilisateur navigue librement).
      if (!s.__lpToday) {
        s.__lpToday = true;
        try { s.date(new Date()); } catch (e) {}
      }
      try {
        var key = ensureDayList(s);
        if (key) {
          s.view(key);
        } else {
          s.__lpFallback = true;
          s.view("day");
        }
      } catch (e) {
        try { s.__lpFallback = true; s.view("day"); } catch (e2) {}
      }
      showEmptyMessage(s);
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
