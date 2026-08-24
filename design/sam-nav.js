/* sam-nav.js — shared 5-tab bottom nav for main app screens */

const ICONS = {
  today: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M4.5 10.5 12 4l7.5 6.5V19a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 19v-8.5Z"/></svg>',
  syllabus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><rect x="4" y="4" width="16" height="16" rx="3"/><path d="M8 9h8M8 12.5h8M8 16h5"/></svg>',
  focus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M12 8v4.5l3 2"/></svg>',
  progress: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M4 18V6l8 5 8-5v12"/><path d="M8 14v4M12 11v7M16 13v5"/></svg>',
  more: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M5 6.5h14M5 12h14M5 17.5h9"/></svg>',
};

export const NAV_TABS = [
  { id: 'today', label: 'Today', href: 'home.html', icon: ICONS.today },
  { id: 'syllabus', label: 'Syllabus', href: 'syllabus.html', icon: ICONS.syllabus },
  { id: 'focus', label: 'Focus', href: 'Focus.dc.html', icon: ICONS.focus },
  { id: 'progress', label: 'Progress', href: '#/progress', icon: ICONS.progress },
  { id: 'more', label: 'More', href: '#/more', icon: ICONS.more },
];

/**
 * Mount bottom nav into container.
 * @param {HTMLElement} root
 * @param {'today'|'syllabus'|'focus'|'progress'|'more'} activeId
 * @param {{ themeQuery?: string }} [opts]
 */
export function mountNav(root, activeId, opts = {}) {
  const themeQ = opts.themeQuery || '';
  const nav = document.createElement('nav');
  nav.className = 'main-nav';
  nav.setAttribute('aria-label', 'Main');

  NAV_TABS.forEach((tab) => {
    const el = document.createElement(tab.stub ? 'button' : 'a');
    el.className = 'nav-item' + (tab.id === activeId ? ' on' : '');
    if (tab.stub) {
      el.type = 'button';
      el.setAttribute('aria-disabled', 'true');
      el.title = 'Coming soon';
    } else {
      el.href = tab.href + themeQ;
    }
    el.innerHTML = tab.icon + '<span>' + tab.label + '</span>';
    if (tab.id === activeId) el.setAttribute('aria-current', 'page');
    nav.appendChild(el);
  });

  root.replaceChildren(nav);
  return nav;
}

export function themeQueryFromLocation() {
  try {
    const theme = new URLSearchParams(location.search).get('theme');
    return theme ? '?theme=' + theme : '';
  } catch (_) {
    return '';
  }
}
