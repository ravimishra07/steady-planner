import { loadState, saveState, DEFAULT_STATE, examFor, cushion } from '../data.js';
import { clearAll as clearFocus } from '../focus-timer.js';

const BACK =
  '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5L7 10l5.5 5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>';

export function mountAccount(root, { navigate }) {
  const s = loadState() || { ...DEFAULT_STATE };
  const exam = examFor(s.exam);
  const c = cushion(s);

  root.className = 'view view-account';
  root.innerHTML = `
    <div class="navbar">
      <button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>
      <span class="nav-title">Account</span>
      <span class="nav-spacer"></span>
    </div>

    <div class="view-scroll settings-scroll">
      <div class="card">
        <div class="row row-stack">
          <div class="row-head"><b>What should we call you?</b></div>
          <input type="text" class="text-field" id="name" value="${escapeAttr(s.name || '')}" placeholder="Your name" autocomplete="given-name">
        </div>
      </div>

      <p class="sec-label">Your plan</p>
      <div class="card">
        <div class="row"><span class="ftxt"><b>Exam</b></span><span class="metric-val">${exam.name}</span></div>
        <div class="row"><span class="ftxt"><b>Days left</b></span><span class="metric-val">${s.days}</span></div>
        <div class="row"><span class="ftxt"><b>Hours available</b></span><span class="metric-val">${c.have}</span></div>
        <div class="row"><span class="ftxt"><b>Syllabus needs</b></span><span class="metric-val">${c.need}</span></div>
      </div>

      <p class="sec-label">Storage</p>
      <div class="card">
        <div class="row row-stack">
          <div class="row-head"><b>This device only</b></div>
          <p class="row-note">There is no account and no server. Everything you enter is saved in this browser. Clearing your browser data, or signing out below, erases it permanently.</p>
        </div>
      </div>

      <div class="more-list" style="margin-top:var(--sam-space-lg)">
        <button type="button" class="more-row danger" id="signout">
          <span class="ftxt"><b>Sign out &amp; erase</b><span>Wipes the plan stored on this device</span></span>
          <span class="chev">&#8250;</span>
        </button>
      </div>
    </div>`;

  root.querySelector('.back-chev').addEventListener('click', () => navigate('more'));

  root.querySelector('#name').addEventListener('change', (e) => {
    saveState({ ...(loadState() || DEFAULT_STATE), name: e.target.value.trim() });
  });

  root.querySelector('#signout').addEventListener('click', () => {
    if (!confirm('Sign out and erase this plan? Nothing is backed up — this cannot be undone.')) return;
    try {
      localStorage.removeItem('plan');
      localStorage.removeItem('syllabus_ui');
      sessionStorage.removeItem('onboard_draft');
    } catch (_) {}
    clearFocus();
    navigate('splash', { replace: true });
  });

  return () => {};
}

function escapeAttr(v) {
  return String(v).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
}
