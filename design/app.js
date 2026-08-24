/**
 * app.js — app shell with hash routing.
 * Existing prototype pages (home.html, canvas-view.html, *.dc.html) are unchanged.
 */
import { hasPlan, seedDemoPlan } from './data.js';
import { NAV_TABS } from './sam-nav.js';
import { mountSplash } from './views/splash.js';
import { mountToday } from './views/today.js';
import { mountSyllabus } from './views/syllabus.js';
import { mountSyllabusRoomy } from './views/syllabus-roomy.js';
import { mountSyllabusMid } from './views/syllabus-mid.js';
import { mountOnboarding } from './views/onboarding.js';
import { mountFocus } from './views/focus.js';
import { mountRebalance } from './views/rebalance.js';
import { mountProgress } from './views/progress.js';
import { mountMore } from './views/more.js';
import { mountSettings } from './views/settings.js';
import { mountAccount } from './views/account.js';
import { mountPolicy } from './views/policy.js';
import { mountPaywall } from './views/paywall.js';

const viewEl = document.getElementById('view');
const navDock = document.getElementById('nav-dock');

let teardown = null;

const ROUTES = {
  splash: { mount: mountSplash, nav: false, title: 'Steadyline' },

  today: { mount: mountToday, tab: 'today', nav: true, title: 'Today' },
  syllabus: { mount: mountSyllabus, tab: 'syllabus', nav: true, title: 'Syllabus' },
  'syllabus-b': { mount: mountSyllabusRoomy, tab: 'syllabus', nav: true, title: 'Syllabus B' },
  'syllabus-c': { mount: mountSyllabusMid, tab: 'syllabus', nav: true, title: 'Syllabus C' },
  focus: { mount: mountFocus, tab: 'focus', nav: true, title: 'Focus' },
  progress: { mount: mountProgress, tab: 'progress', nav: true, title: 'Progress' },
  more: { mount: mountMore, tab: 'more', nav: true, title: 'More' },

  rebalance: { mount: mountRebalance, nav: false, title: 'Rebalance' },
  paywall: { mount: mountPaywall, nav: false, title: 'Upgrade' },
  settings: { mount: mountSettings, nav: false, title: 'Settings' },
  account: { mount: mountAccount, nav: false, title: 'Account' },

  'policy/privacy': { mount: (el, ctx) => mountPolicy(el, ctx, 'privacy'), nav: false, title: 'Privacy' },
  'policy/terms': { mount: (el, ctx) => mountPolicy(el, ctx, 'terms'), nav: false, title: 'Terms' },
  'policy/about': { mount: (el, ctx) => mountPolicy(el, ctx, 'about'), nav: false, title: 'About' },

  'onboarding/exam': { mount: (el, ctx) => mountOnboarding(el, ctx, 'exam'), nav: false, title: 'Pick exam' },
  'onboarding/date': { mount: (el, ctx) => mountOnboarding(el, ctx, 'date'), nav: false, title: 'Exam date' },
  'onboarding/shape': { mount: (el, ctx) => mountOnboarding(el, ctx, 'shape'), nav: false, title: 'Day shape' },
  'onboarding/hours': { mount: (el, ctx) => mountOnboarding(el, ctx, 'hours'), nav: false, title: 'Hours' },
  'onboarding/cushion': { mount: (el, ctx) => mountOnboarding(el, ctx, 'cushion'), nav: false, title: 'Your plan' },
};

export function navigate(path, { replace = false } = {}) {
  const next = String(path).replace(/^\//, '');
  const hash = '#/' + next;
  if (replace) {
    // replaceState does not fire hashchange, so render explicitly either way
    if (location.hash !== hash) history.replaceState(null, '', hash);
    render();
    return;
  }
  if (location.hash === hash) render();
  else location.hash = hash;
}

function parseRoute() {
  const raw = (location.hash || '').replace(/^#\/?/, '').trim();
  if (!raw) return 'splash';
  if (ROUTES[raw]) return raw;
  if (raw.startsWith('onboarding/')) return 'onboarding/exam';
  if (raw.startsWith('policy/')) return 'policy/about';
  return hasPlan() ? 'today' : 'onboarding/exam';
}

function mountAppNav(activeId) {
  navDock.hidden = false;
  navDock.replaceChildren();
  const nav = document.createElement('nav');
  nav.className = 'main-nav';
  nav.setAttribute('aria-label', 'Main');

  NAV_TABS.forEach((tab) => {
    const el = document.createElement('button');
    el.type = 'button';
    el.className = 'nav-item' + (tab.id === activeId ? ' on' : '');
    el.innerHTML = tab.icon + '<span>' + tab.label + '</span>';
    if (tab.id === activeId) el.setAttribute('aria-current', 'page');
    el.addEventListener('click', () => navigate(tab.id));
    nav.appendChild(el);
  });

  navDock.appendChild(nav);
}

function render() {
  const route = parseRoute();

  // An unknown or partial hash resolves to a real route — keep the URL honest
  // so back/forward and a shared link land where the screen actually is.
  const raw = (location.hash || '').replace(/^#\/?/, '').trim();
  if (raw && raw !== route) {
    history.replaceState(null, '', '#/' + route);
  }

  const def = ROUTES[route];
  if (!def) {
    navigate(hasPlan() ? 'today' : 'onboarding/exam', { replace: true });
    return;
  }

  if (teardown) {
    teardown();
    teardown = null;
  }

  viewEl.replaceChildren();
  const shell = document.createElement('div');
  shell.className = 'view';
  viewEl.appendChild(shell);

  const ctx = { navigate, route };
  teardown = def.mount(shell, ctx) || null;

  if (def.nav) mountAppNav(def.tab);
  else {
    navDock.hidden = true;
    navDock.replaceChildren();
  }

  document.title = 'Steadyline · ' + (def.title || 'Exam planner');
}

/* Desktop preview: keep the frame a true 402x874 iPhone 17 and scale the
   whole thing down to fit the window. On a real phone the shell is
   position:fixed edge-to-edge and this is a no-op. */
function fitPhoneFrame() {
  const shell = document.getElementById('app-shell');
  if (!shell) return;
  const framed = window.matchMedia('(min-width: 480px) and (min-height: 600px)').matches;
  if (!framed) {
    shell.style.removeProperty('--phone-scale');
    return;
  }
  const scale = Math.min(1, (window.innerHeight - 48) / 874, (window.innerWidth - 32) / 402);
  shell.style.setProperty('--phone-scale', String(Math.max(0.3, scale)));
}

window.addEventListener('resize', fitPhoneFrame);
fitPhoneFrame();

window.addEventListener('hashchange', render);

// Demo helpers: ?seed=1 loads a demo plan, ?reset=1 wipes and restarts onboarding.
try {
  const params = new URLSearchParams(location.search);
  if (params.get('seed') === '1' && !hasPlan()) seedDemoPlan();

  if (params.get('reset') === '1') {
    localStorage.removeItem('plan');
    localStorage.removeItem('syllabus_ui');
    localStorage.removeItem('focus');
    sessionStorage.removeItem('onboard_draft');
    navigate('onboarding/exam', { replace: true });
  } else {
    navigate(parseRoute(), { replace: true });
  }
} catch (_) {
  render();
}

export { hasPlan };
