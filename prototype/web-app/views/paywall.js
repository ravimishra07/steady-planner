import { loadState, DEFAULT_STATE, cushion, examFor } from '../data.js';

const BACK =
  '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5L7 10l5.5 5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const TICK =
  '<svg width="15" height="15" viewBox="0 0 20 20" fill="none"><path d="M4.5 10.5l3.5 3.5 7.5-8" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const INCLUDED = [
  'Daily plan rebuilt from your real hours',
  'One-tap rebalance when you miss days',
  'Revision scheduled automatically, spaced out',
  'Full syllabus tree with source breakdowns',
  'Works fully offline. No account needed.',
];

const PLANS = [
  { id: 'exam', title: 'Till your exam', sub: 'One payment, covers the full attempt', price: '₹399', best: true },
  { id: 'month', title: 'Monthly', sub: 'Cancel any time', price: '₹99' },
];

export function mountPaywall(root, { navigate }) {
  const s = loadState() || { ...DEFAULT_STATE };
  const c = cushion(s);
  const exam = examFor(s.exam);
  let picked = 'exam';

  root.className = 'view view-paywall';
  root.innerHTML = `
    <div class="navbar">
      <button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>
      <span class="nav-spacer"></span>
      <button type="button" class="link-btn" id="restore">Restore</button>
    </div>

    <div class="view-scroll settings-scroll">
      <p class="eyebrow">Your plan is ready</p>
      <h1 class="paywall-h1">${
        c.short
          ? `You are ${c.gap} hours short.<br>Here is the way through.`
          : `You have ${Math.abs(c.gap)} hours spare.<br>Protect them.`
      }</h1>

      <div class="card summary-card">
        <div class="row"><span class="ftxt"><b>Exam</b></span><span class="metric-val">${exam.name}</span></div>
        <div class="row"><span class="ftxt"><b>Days left</b></span><span class="metric-val">${s.days}</span></div>
        <div class="row"><span class="ftxt"><b>Syllabus needs</b></span><span class="metric-val">${c.need} hrs</span></div>
        <div class="row"><span class="ftxt"><b>You have</b></span><span class="metric-val ${c.short ? 'is-short' : ''}">${c.have} hrs</span></div>
        <div class="gauge-track">
          <span class="g-have" style="width:${c.short ? c.coverage : 100}%"></span>
          ${c.short ? `<span class="g-short" style="width:${100 - c.coverage}%"></span>` : ''}
        </div>
      </div>

      <div class="plan-list">
        ${PLANS.map(
          (p) => `
          <button type="button" class="plan-row${p.id === picked ? ' sel' : ''}" data-plan="${p.id}" aria-pressed="${p.id === picked}">
            <span class="pick" aria-hidden="true"></span>
            <span class="ftxt"><b>${p.title}</b><span>${p.sub}</span></span>
            ${p.best ? '<span class="best">Best</span>' : ''}
            <span class="price">${p.price}</span>
          </button>`
        ).join('')}
      </div>

      <div class="inc">
        ${INCLUDED.map((x) => `<div>${TICK}<span>${x}</span></div>`).join('')}
      </div>
    </div>

    <div class="view-foot">
      <button type="button" class="cta" id="buy">Unlock my plan</button>
      <button type="button" class="ghost-btn" id="later">Not now</button>
    </div>`;

  const back = () => navigate('today');
  root.querySelector('.back-chev').addEventListener('click', back);
  root.querySelector('#later').addEventListener('click', back);

  root.querySelectorAll('[data-plan]').forEach((btn) => {
    btn.addEventListener('click', () => {
      picked = btn.dataset.plan;
      root.querySelectorAll('[data-plan]').forEach((b) => {
        const on = b === btn;
        b.classList.toggle('sel', on);
        b.setAttribute('aria-pressed', String(on));
      });
    });
  });

  root.querySelector('#restore').addEventListener('click', () =>
    alert('No purchases to restore — billing is not wired up in this prototype.')
  );

  root.querySelector('#buy').addEventListener('click', () => {
    alert('Payments are not connected yet. This prototype stores everything on your device.');
  });

  return () => {};
}
