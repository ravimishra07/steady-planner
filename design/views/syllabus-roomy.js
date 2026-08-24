/* Roomier variant of views/syllabus.js — same logic and same tick state,
   more vertical space. Kept as a separate file so the original is untouched
   while the two are compared. */
import { CGL, topicHours } from '../syllabus-cgl.js';

const KEY = 'syllabus_ui';

/* Short labels for the tab strip — the full names are too long for a phone. */
const SHORT = {
  'Quantitative Aptitude': 'Quant',
  'General Intelligence & Reasoning': 'Reasoning',
  'General Awareness': 'GA',
  'English Comprehension': 'English',
};

const MINUS = '<svg viewBox="0 0 20 20" fill="none"><path d="M5.5 10h9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>';
const PLUS  = '<svg viewBox="0 0 20 20" fill="none"><path d="M5.5 10h9M10 5.5v9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>';
const CHECK = '<svg viewBox="0 0 20 20" fill="none"><path d="M5 10.5l3.4 3.4L15 7.2" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';

/* ---------- state ---------- */
function load() {
  try {
    const raw = localStorage.getItem(KEY);
    const s = raw ? JSON.parse(raw) : {};
    return { done: s.done || {}, open: s.open || {}, tier: s.tier || 't1', section: s.section ?? 0 };
  } catch (_) {
    return { done: {}, open: {}, tier: 't1', section: 0 };
  }
}
function save(s) {
  try { localStorage.setItem(KEY, JSON.stringify(s)); } catch (_) {}
}

/* ---------- hours ----------
   A topic owns its hours. Expanding splits them across its children in
   half-hour steps, and the parts always add back up to the parent. */
function splitHours(total, n) {
  if (!n) return [];
  const base = Math.floor((total / n) * 2) / 2;
  const parts = Array(n).fill(base);
  let rem = Math.round((total - base * n) * 2) / 2;
  for (let i = 0; rem > 0.001 && i < n * 8; i++) {
    parts[i % n] += 0.5;
    rem -= 0.5;
  }
  return parts;
}
const fmtHours = (h) => (Number.isInteger(h) ? h : h.toFixed(1)) + 'h';

/* ---------- tree helpers ---------- */
function leafKeys(node, key) {
  if (!node.c || !node.c.length) return [key];
  return node.c.flatMap((c, i) => leafKeys(c, `${key}_${i}`));
}

export function mountSyllabusRoomy(root, { navigate }) {
  const state = load();
  const sections = state.tier === 't2' ? CGL.tier2 : CGL.tier1;
  if (state.section >= sections.length) state.section = 0;

  root.className = 'view view-syllabus-b';
  root.innerHTML = `
    <header class="syl-head">
      <h1>Syllabus</h1>
      <p class="syl-sub" id="syl-sub"></p>
    </header>
    <div class="subject-tabs" id="tabs" role="tablist"></div>
    <div class="view-scroll syl-scroll"><div class="tree" id="tree"></div></div>`;

  const tabsEl = root.querySelector('#tabs');
  const treeEl = root.querySelector('#tree');
  const subEl = root.querySelector('#syl-sub');

  /* ---------- header summary ---------- */
  function paintSummary() {
    const sec = sections[state.section];
    let doneH = 0;
    const totalH = sec.t.reduce((n, t) => n + topicHours(t), 0);
    sec.t.forEach((topic, ti) => {
      const ls = leafKeys(topic, `${state.tier}_${state.section}_${ti}`);
      const n = ls.filter((k) => state.done[k]).length;
      doneH += topicHours(topic) * (ls.length ? n / ls.length : 0);
    });
    subEl.textContent = `${Math.round(doneH)} of ${totalH} hrs done · ${sec.t.length} topics`;
  }

  /* ---------- tabs ---------- */
  function paintTabs() {
    tabsEl.innerHTML = sections
      .map((s, i) => `<button type="button" role="tab" class="subject-tab${i === state.section ? ' on' : ''}" data-sec="${i}" aria-selected="${i === state.section}">${SHORT[s.n] || s.n}</button>`)
      .join('');
    tabsEl.querySelectorAll('[data-sec]').forEach((b) => {
      b.addEventListener('click', () => {
        state.section = +b.dataset.sec;
        save(state);
        paintTabs();
        paintTree();
        paintSummary();
        root.querySelector('.syl-scroll').scrollTop = 0;
      });
    });
  }

  /* ---------- one node, recursive ---------- */
  function nodeHTML(node, key, hours, depth) {
    const kids = node.c || [];
    const hasKids = kids.length > 0;
    const ls = leafKeys(node, key);
    const doneCount = ls.filter((k) => state.done[k]).length;
    const st = doneCount === 0 ? 'none' : doneCount === ls.length ? 'all' : 'part';
    const open = hasKids && !!state.open[key];
    const shares = hasKids ? splitHours(hours, kids.length) : [];

    return `
      <div class="node d${depth}${open ? ' open' : ''}${depth === 0 ? ' topcard' : ''}" data-key="${key}">
        <div class="row">
          ${hasKids
            ? `<button type="button" class="toggle" data-toggle="${key}" aria-expanded="${open}" aria-label="${open ? 'Collapse' : 'Expand'}">${open ? MINUS : PLUS}</button>`
            : '<span class="leafdot" aria-hidden="true"></span>'}
          <button type="button" class="tickbox ${st}" data-tick="${key}" aria-pressed="${st === 'all'}" aria-label="Mark done">${st === 'all' ? CHECK : ''}</button>
          <button type="button" class="label" data-toggle="${hasKids ? key : ''}">
            <span class="nm ${st}">${node.n}</span>
          </button>
          <span class="hrs">${fmtHours(hours)}</span>
        </div>
        ${hasKids ? `<div class="kids">${kids.map((c, i) => nodeHTML(c, `${key}_${i}`, shares[i], depth + 1)).join('')}</div>` : ''}
      </div>`;
  }

  function paintTree() {
    const sec = sections[state.section];
    treeEl.innerHTML = sec.t
      .map((topic, ti) => nodeHTML(topic, `${state.tier}_${state.section}_${ti}`, topicHours(topic), 0))
      .join('');
    wire();
  }

  function wire() {
    treeEl.querySelectorAll('[data-toggle]').forEach((b) => {
      const key = b.dataset.toggle;
      if (!key) return;
      b.addEventListener('click', () => {
        state.open[key] = !state.open[key];
        save(state);
        paintTree();
      });
    });
    treeEl.querySelectorAll('[data-tick]').forEach((b) => {
      b.addEventListener('click', () => {
        const key = b.dataset.tick;
        const node = nodeAt(key);
        const ls = leafKeys(node, key);
        const allDone = ls.every((k) => state.done[k]);
        ls.forEach((k) => {
          if (allDone) delete state.done[k];
          else state.done[k] = true;
        });
        save(state);
        paintTree();
        paintSummary();
      });
    });
  }

  /* key -> node, e.g. "t1_1_0_2" */
  function nodeAt(key) {
    const parts = key.split('_');
    let node = sections[+parts[1]].t[+parts[2]];
    for (let i = 3; i < parts.length; i++) node = node.c[+parts[i]];
    return node;
  }

  paintTabs();
  paintTree();
  paintSummary();

  return () => {};
}
