/* sam-theme.js — applies the theme on <html> BEFORE first paint.
   Must stay a classic (non-module) script loaded synchronously in <head>,
   otherwise the page flashes the wrong theme on load.

   Preference is one of 'light' | 'dark' | 'system' and lives in
   localStorage under `sam_theme`. `?theme=light|dark` still works and wins
   for that page load only, so existing prototype links keep behaving.  */
(function () {
  var KEY = 'sam_theme';
  var BG = { dark: '#0A0A0F', light: '#FFFFFF' };
  var mql = window.matchMedia ? window.matchMedia('(prefers-color-scheme: light)') : null;

  function systemTheme() {
    return mql && mql.matches ? 'light' : 'dark';
  }

  function readPref() {
    try {
      var q = new URLSearchParams(location.search).get('theme');
      if (q === 'light' || q === 'dark') return q;
    } catch (_) {}
    try {
      var v = localStorage.getItem(KEY);
      if (v === 'light' || v === 'dark' || v === 'system') return v;
    } catch (_) {}
    return 'system';
  }

  function resolve(pref) {
    return pref === 'system' ? systemTheme() : pref;
  }

  function apply(pref) {
    var theme = resolve(pref);
    var root = document.documentElement;
    root.classList.remove('sam-light', 'sam-dark');
    root.classList.add(theme === 'light' ? 'sam-light' : 'sam-dark');
    root.setAttribute('data-theme', theme);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', BG[theme]);
    return theme;
  }

  var current = readPref();
  apply(current);

  // Follow the OS only while the user has not pinned a theme.
  if (mql && mql.addEventListener) {
    mql.addEventListener('change', function () {
      if (current === 'system') apply(current);
    });
  }

  window.SamTheme = {
    /** 'light' | 'dark' | 'system' — what the user chose. */
    get: function () {
      return current;
    },
    /** 'light' | 'dark' — what is actually on screen right now. */
    resolved: function () {
      return resolve(current);
    },
    set: function (pref) {
      if (pref !== 'light' && pref !== 'dark' && pref !== 'system') return;
      current = pref;
      try {
        localStorage.setItem(KEY, pref);
      } catch (_) {}
      return apply(pref);
    },
    clear: function () {
      current = 'system';
      try {
        localStorage.removeItem(KEY);
      } catch (_) {}
      return apply(current);
    },
  };
})();
