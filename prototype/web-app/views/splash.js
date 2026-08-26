import { hasPlan } from '../data.js';

const HOLD_MS = 1100;

export function mountSplash(root, { navigate }) {
  root.className = 'view view-splash';
  root.innerHTML = `
    <button type="button" class="splash-tap" aria-label="Continue">
      <div class="splash-mark" aria-hidden="true">
        <svg width="72" height="72" viewBox="0 0 72 72" fill="none">
          <rect x="3" y="3" width="66" height="66" rx="20" fill="var(--sam-brand)"/>
          <path d="M20 44.5 30 33l7.5 7.5L52 24" stroke="var(--sam-on-brand)" stroke-width="4.5"
                stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="52" cy="24" r="4.5" fill="var(--sam-on-brand)"/>
        </svg>
      </div>
      <h1 class="splash-name">Steadyline</h1>
      <p class="splash-tag">A plan built backward from your exam date.</p>
    </button>
    <div class="splash-foot"><span class="splash-dots" aria-hidden="true"><i></i><i></i><i></i></span></div>`;

  const go = () => navigate(hasPlan() ? 'today' : 'onboarding/exam', { replace: true });

  const t = setTimeout(go, HOLD_MS);
  root.querySelector('.splash-tap').addEventListener('click', () => {
    clearTimeout(t);
    go();
  });

  return () => clearTimeout(t);
}
