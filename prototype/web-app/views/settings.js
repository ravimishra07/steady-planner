import { loadState, saveState, DEFAULT_STATE } from '../data.js';
import { getTheme, setTheme, THEME_OPTIONS, resolvedTheme } from '../theme.js';
import { getState as focusState, setDuration } from '../focus-timer.js';

const BACK =
  '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5L7 10l5.5 5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const FOCUS_LENGTHS = [25, 50, 90];

export function mountSettings(root, { navigate }) {
  const s = loadState() || { ...DEFAULT_STATE };
  root.className = 'view view-settings';

  // `attr` is deliberately not `data-theme` — that name is taken by the
  // <html data-theme> the pre-paint script sets, and would collide.
  const seg = (attr, label, options, active) =>
    `<div class="seg" role="radiogroup" aria-label="${label}">` +
    options
      .map(
        (o) =>
          `<button type="button" class="seg-opt${o.id === active ? ' on' : ''}" role="radio" aria-checked="${o.id === active}" data-${attr}="${o.id}">${o.label}</button>`
      )
      .join('') +
    '</div>';

  root.innerHTML = `
    <div class="navbar">
      <button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>
      <span class="nav-title">Settings</span>
      <span class="nav-spacer"></span>
    </div>

    <div class="view-scroll settings-scroll">
      <p class="sec-label">Appearance</p>
      <div class="card">
        <div class="row row-stack">
          <div class="row-head"><b>Theme</b><span id="theme-now">Currently ${resolvedTheme()}</span></div>
          ${seg('themeopt', 'Theme', THEME_OPTIONS, getTheme())}
        </div>
      </div>

      <p class="sec-label">Your hours</p>
      <div class="card">
        <div class="row row-stack">
          <div class="row-head"><b>Weekdays</b><span class="metric-val" id="wd-val">${s.wd} hrs</span></div>
          <input type="range" id="wd" min="1" max="14" step="0.5" value="${s.wd}" aria-label="Weekday hours">
        </div>
        <div class="row row-stack">
          <div class="row-head"><b>Weekends</b><span class="metric-val" id="we-val">${s.we} hrs</span></div>
          <input type="range" id="we" min="1" max="16" step="0.5" value="${s.we}" aria-label="Weekend hours">
        </div>
        <div class="row row-stack">
          <div class="row-head"><b>Where you study</b></div>
          <input type="text" class="text-field" id="place" value="${escapeAttr(s.place || '')}" placeholder="Library, terrace, corner desk">
        </div>
      </div>

      <p class="sec-label">Focus</p>
      <div class="card">
        <div class="row row-stack">
          <div class="row-head"><b>Default session length</b></div>
          ${seg(
            'focuslen',
            'Session length',
            FOCUS_LENGTHS.map((m) => ({ id: String(m), label: m + ' min' })),
            String(Math.round(focusState().durationSec / 60))
          )}
          <p class="row-note" id="focuslen-note"></p>
        </div>
      </div>

      <p class="sec-label">Data</p>
      <div class="card">
        <button type="button" class="row row-tap" id="export">
          <span class="ftxt"><b>Export my plan</b><span>Downloads a JSON backup to this device</span></span>
          <span class="chev">&#8250;</span>
        </button>
        <button type="button" class="row row-tap danger" id="clear">
          <span class="ftxt"><b>Clear plan &amp; restart</b><span>Erases everything stored on this device</span></span>
          <span class="chev">&#8250;</span>
        </button>
      </div>

      <p class="foot-note">Everything lives on this device only. No account, no server.</p>
    </div>`;

  const $ = (sel) => root.querySelector(sel);

  root.querySelector('.back-chev').addEventListener('click', () => navigate('more'));

  // ── theme ──
  root.querySelectorAll('[data-themeopt]').forEach((btn) => {
    btn.addEventListener('click', () => {
      setTheme(btn.dataset.themeopt);
      root.querySelectorAll('[data-themeopt]').forEach((b) => {
        const on = b === btn;
        b.classList.toggle('on', on);
        b.setAttribute('aria-checked', String(on));
      });
      $('#theme-now').textContent = 'Currently ' + resolvedTheme();
    });
  });

  // ── hours ──
  const commit = (patch) => saveState({ ...(loadState() || DEFAULT_STATE), ...patch });

  $('#wd').addEventListener('input', (e) => {
    $('#wd-val').textContent = e.target.value + ' hrs';
    commit({ wd: +e.target.value });
  });
  $('#we').addEventListener('input', (e) => {
    $('#we-val').textContent = e.target.value + ' hrs';
    commit({ we: +e.target.value });
  });
  $('#place').addEventListener('change', (e) => commit({ place: e.target.value.trim() }));

  // ── focus length ──
  const lenNote = $('#focuslen-note');
  const refreshLenNote = () => {
    const st = focusState();
    const busy = st.status === 'running' || st.status === 'paused';
    lenNote.textContent = busy ? 'Finish or stop the current session to change this.' : '';
    root.querySelectorAll('[data-focuslen]').forEach((b) => {
      b.disabled = busy;
    });
  };
  root.querySelectorAll('[data-focuslen]').forEach((btn) => {
    btn.addEventListener('click', () => {
      setDuration(+btn.dataset.focuslen * 60);
      root.querySelectorAll('[data-focuslen]').forEach((b) => {
        const on = b === btn;
        b.classList.toggle('on', on);
        b.setAttribute('aria-checked', String(on));
      });
    });
  });
  refreshLenNote();

  // ── data ──
  $('#export').addEventListener('click', () => {
    const payload = {
      exportedAt: new Date().toISOString(),
      plan: loadState(),
      focus: focusState(),
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'steadyline-plan.json';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  });

  $('#clear').addEventListener('click', () => {
    if (!confirm('Erase your plan and all progress on this device? This cannot be undone.')) return;
    try {
      localStorage.removeItem('plan');
      localStorage.removeItem('syllabus_ui');
      localStorage.removeItem('focus');
      sessionStorage.removeItem('onboard_draft');
    } catch (_) {}
    navigate('onboarding/exam');
  });

  return () => {};
}

function escapeAttr(v) {
  return String(v).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
}
