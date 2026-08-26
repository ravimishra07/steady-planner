import { loadState } from '../data.js';
import { examFor } from '../data.js';

const CHEV = '<span class="chev" aria-hidden="true">&#8250;</span>';

const ICONS = {
  account: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="8.2" r="3.6"/><path d="M5 20.4c.9-3.7 3.6-5.6 7-5.6s6.1 1.9 7 5.6"/></svg>',
  settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="3.2"/><path d="M12 3.5v2M12 18.5v2M3.5 12h2M18.5 12h2M6 6l1.4 1.4M16.6 16.6 18 18M18 6l-1.4 1.4M7.4 16.6 6 18"/></svg>',
  rebalance: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 9h11a4 4 0 0 1 0 8h-3"/><path d="M7.5 5.5 4 9l3.5 3.5"/></svg>',
  upgrade: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="m12 3.5 2.6 5.4 5.9.8-4.3 4.1 1.1 5.9L12 16.9 6.7 19.7l1.1-5.9L3.5 9.7l5.9-.8Z"/></svg>',
  restart: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M20 12a8 8 0 1 1-2.6-5.9"/><path d="M20 4v4.5h-4.5"/></svg>',
  privacy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 3.5 5 6.3v5.2c0 4 2.9 7.5 7 9 4.1-1.5 7-5 7-9V6.3Z"/></svg>',
  terms: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M6.5 3.5h8L19 8v12.5H6.5Z"/><path d="M14 3.5V8h5M9.5 12.5h5M9.5 16h5"/></svg>',
  about: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.8v.4"/></svg>',
};

const row = (go, icon, title, sub, extra = '') =>
  `<button type="button" class="more-row${extra}" data-go="${go}">
     <span class="more-icon" aria-hidden="true">${icon}</span>
     <span class="ftxt"><b>${title}</b><span>${sub}</span></span>${CHEV}
   </button>`;

export function mountMore(root, { navigate }) {
  const s = loadState();
  const exam = s ? examFor(s.exam) : null;

  root.className = 'view view-more';
  root.innerHTML = `
    <header class="screen-head">
      <h1>More</h1>
    </header>

    <div class="view-scroll more-scroll">
      <button type="button" class="account-card" data-go="account">
        <span class="avatar" aria-hidden="true">${(s && s.name ? s.name : 'You').slice(0, 1).toUpperCase()}</span>
        <span class="ftxt">
          <b>${s && s.name ? s.name : 'Your plan'}</b>
          <span>${exam ? exam.name + ' · ' + s.days + ' days left' : 'No plan yet'}</span>
        </span>${CHEV}
      </button>

      <p class="sec-label">Plan</p>
      <div class="more-list">
        ${row('rebalance', ICONS.rebalance, 'Rebalance', 'Recover when you fall behind')}
        ${row('paywall', ICONS.upgrade, 'Upgrade', 'Unlock the full plan')}
        ${row('onboarding/exam', ICONS.restart, 'Redo onboarding', 'Change exam, date or hours')}
      </div>

      <p class="sec-label">App</p>
      <div class="more-list">
        ${row('settings', ICONS.settings, 'Settings', 'Theme, hours, focus length, data')}
        ${row('account', ICONS.account, 'Account', 'Your details and sign out')}
      </div>

      <p class="sec-label">About</p>
      <div class="more-list">
        ${row('policy/privacy', ICONS.privacy, 'Privacy', 'What is stored and where')}
        ${row('policy/terms', ICONS.terms, 'Terms of use', 'The short version')}
        ${row('policy/about', ICONS.about, 'About Steadyline', 'Version and credits')}
      </div>

      <p class="foot-note">Steadyline &middot; offline prototype</p>
    </div>`;

  root.querySelectorAll('[data-go]').forEach((btn) => {
    btn.addEventListener('click', () => navigate(btn.dataset.go));
  });

  return () => {};
}
