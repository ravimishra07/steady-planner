import { loadState, DEFAULT_STATE, cushion } from '../data.js';

const BACK = '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5L7 10l5.5 5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const TICK = '<svg width="15" height="15" viewBox="0 0 20 20" fill="none"><path d="M4.5 10.5l3.5 3.5 7.5-8" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';

/** What the missed days cost, in the order they were scheduled. */
const SLIPPED = [
  ['Geometry — Circles', 6],
  ['Mensuration — Solids', 7],
  ['Revision: Ratio & Proportion', 2],
  ['Current Affairs × 4 days', 2],
];

export function mountRebalance(root, { navigate }) {
  const s = loadState() || { ...DEFAULT_STATE };
  const c = cushion(s);
  const lost = SLIPPED.reduce((n, r) => n + r[1], 0);
  const perDay = +(lost / s.days).toFixed(1);
  const perDayMins = Math.round((lost / s.days) * 60);
  const pushDays = Math.ceil(lost / ((s.wd + s.we) / 2));

  const OPTIONS = [
    {
      id: 'spread',
      metric: `+${perDay}<em>h</em>`,
      title: 'Spread it across every day',
      sub: `${perDayMins} extra minutes a day. Nothing gets dropped.`,
      cta: 'Spread it across my days',
      after: c.gap + lost,
    },
    {
      id: 'drop',
      metric: '&minus;2',
      title: 'Drop Solids and one revision pass',
      sub: 'Costs about 1.5 marks.',
      cta: 'Drop those two',
      after: c.gap + lost - 9,
    },
    {
      id: 'push',
      metric: `${pushDays}<em>d</em>`,
      title: 'Push the target date back',
      sub: 'Keeps everything, moves the finish line.',
      cta: `Move the date ${pushDays} days`,
      after: c.gap,
    },
  ];

  let picked = null;

  root.className = 'view view-rebalance';
  root.innerHTML = `
    <div class="topbar">
      <button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>
      <span class="nav-title">Rebalance</span>
    </div>

    <div class="view-scroll reb-scroll">
      <p class="eyebrow danger">${s.missedDays || 4} days missed</p>
      <h1>Here is what slipped</h1>
      <p class="lede">Nothing is lost. It has to go somewhere, and you choose where.</p>

      <div class="slip">
        ${SLIPPED.map(([n, h]) => `<div class="r"><b>${n}</b><em>${h} hrs</em></div>`).join('')}
        <div class="r tot"><b>Total slipped</b><em>${lost} hrs</em></div>
      </div>

      <div class="linkdown" aria-hidden="true">
        <svg width="18" height="30" viewBox="0 0 18 30" fill="none">
          <path d="M9 0v22M2.5 16L9 22.5 15.5 16" stroke="currentColor" stroke-width="1.5"
                stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>

      <p class="pick-label">Pick one</p>
      <div class="list">
        ${OPTIONS.map((o) => `
          <button type="button" class="fixcard reb-opt" data-opt="${o.id}" aria-pressed="false">
            <span class="metric">${o.metric}</span>
            <span class="ftxt"><b>${o.title}</b><span>${o.sub}</span></span>
            <span class="pick"></span>
          </button>`).join('')}
      </div>

      <div class="landing" id="landing" hidden></div>
    </div>

    <div class="view-foot"><button type="button" class="cta" id="apply-reb" disabled>Pick an option</button></div>`;

  const apply = root.querySelector('#apply-reb');
  const landing = root.querySelector('#landing');
  const opts = [...root.querySelectorAll('.fixcard.reb-opt')];

  root.querySelector('.back-chev').addEventListener('click', () => navigate('today'));

  opts.forEach((btn) => {
    btn.addEventListener('click', () => {
      picked = OPTIONS.find((o) => o.id === btn.dataset.opt);
      opts.forEach((b) => {
        const on = b === btn;
        b.classList.toggle('sel', on);
        b.setAttribute('aria-pressed', String(on));
        b.querySelector('.pick').innerHTML = on ? TICK : '';
      });
      apply.disabled = false;
      apply.textContent = picked.cta;

      const short = picked.after > 0;
      landing.hidden = false;
      landing.className = 'landing ' + (short ? 'is-short' : 'is-ok');
      landing.innerHTML =
        `<span class="n">${Math.abs(picked.after)}</span>` +
        `<p>hours ${short ? 'short' : 'spare'} after this — was ${Math.abs(c.gap)}. ` +
        `Your cushion updates the moment you apply it.</p>`;
    });
  });

  apply.addEventListener('click', () => {
    if (apply.disabled || !picked) return;
    apply.textContent = 'Applied';
    apply.disabled = true;
    setTimeout(() => navigate('today'), 600);
  });

  return () => {};
}
