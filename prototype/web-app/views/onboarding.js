import { EXAMS, WORK, saveState, DEFAULT_STATE, cushion } from '../data.js';

const TICK = '<svg width="16" height="16" viewBox="0 0 20 20" fill="none"><path d="M4.5 10.5l3.5 3.5 7.5-8" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const BACK = '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5L7 10l5.5 5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const STEPS = ['exam', 'date', 'shape', 'hours', 'cushion'];
const STEP_IX = Object.fromEntries(STEPS.map((s, i) => [s, i]));
/** Progress bar only on the four setup steps — cushion is the payoff. */
const PROGRESS = ['exam', 'date', 'shape', 'hours'];

const SYLLABUS_READY = new Set(['cgl']);

function progressBar(step) {
  const ix = PROGRESS.indexOf(step);
  if (ix < 0) return '<span class="bar-flex" aria-hidden="true"></span>';
  return `<div class="dots" aria-hidden="true">${PROGRESS.map((_, i) => `<i class="${i <= ix ? 'on' : ''}"></i>`).join('')}</div>`;
}

function loadDraft() {
  const base = { ...DEFAULT_STATE, exam: 'cgl', work: 'col', wd: 4, we: 7, days: 118, place: 'Terrace' };
  try {
    const raw = sessionStorage.getItem('onboard_draft');
    return raw ? { ...base, ...JSON.parse(raw) } : base;
  } catch (_) {
    return base;
  }
}

function examDateLabel(days) {
  const d = new Date(Date.now() + days * 86400000);
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function mountOnboarding(root, { navigate }, step) {
  root.className = 'view view-onboard screen sam-ios';
  const ix = STEP_IX[step];
  const prev = ix > 0 ? `onboarding/${STEPS[ix - 1]}` : null;
  const draft = loadDraft();
  const saveDraft = () => {
    try { sessionStorage.setItem('onboard_draft', JSON.stringify(draft)); } catch (_) {}
  };

  function shell({ title, body, cta, lede }, onContinue) {
    root.innerHTML = `
      <div class="onboard-bar">
        ${prev ? `<button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>`
               : '<span class="bar-sp" aria-hidden="true"></span>'}
        ${progressBar(step)}
        <span class="bar-sp" aria-hidden="true"></span>
      </div>
      <div class="onboard-body">
        <h1>${title}</h1>
        ${lede ? `<p class="lede">${lede}</p>` : ''}
        ${body}
      </div>
      <div class="view-foot">
        <button type="button" class="cta" id="ob-cta">${cta}</button>
      </div>`;
    const back = root.querySelector('.back-chev');
    if (back && prev) back.addEventListener('click', () => navigate(prev));
    root.querySelector('#ob-cta').addEventListener('click', onContinue);
  }

  // ── 1 · exam ──────────────────────────────────────────────
  if (step === 'exam') {
    const ready = EXAMS.filter((e) => SYLLABUS_READY.has(e.id));
    const list = ready.map((e) => {
      const sel = e.id === draft.exam;
      return `<button type="button" class="exam${sel ? ' sel' : ''}" data-id="${e.id}">
        <span class="exam-label"><h3>${e.name}</h3></span>
        <span class="tick">${sel ? TICK : ''}</span>
      </button>`;
    }).join('');

    shell({
      title: 'Which exam?',
      body: `<div class="list">${list}</div>`,
      cta: 'Continue',
    }, () => navigate('onboarding/date'));

    root.querySelectorAll('.exam').forEach((btn) => {
      btn.addEventListener('click', () => {
        draft.exam = btn.dataset.id;
        saveDraft();
        root.querySelectorAll('.exam').forEach((b) => {
          const on = b === btn;
          b.classList.toggle('sel', on);
          b.querySelector('.tick').innerHTML = on ? TICK : '';
        });
      });
    });

  // ── 2 · date ──────────────────────────────────────────────
  } else if (step === 'date') {
    const weeks = Math.floor(draft.days / 7);
    const rem = draft.days % 7;
    const segs = 12;
    const filled = Math.max(1, Math.round((draft.days / 180) * segs));

    shell({
      title: "When's the exam?",
      body: `<div class="onboard-stack">
        <div class="countdown">
          <div class="cd-top">
            <span class="n">${draft.days}</span>
            <span class="l">days left<br>${weeks}w ${rem}d</span>
          </div>
          <div class="cd-scale">${Array.from({ length: segs }, (_, i) => `<i class="${i < filled ? 'on' : ''}"></i>`).join('')}</div>
          <div class="cd-legend"><span>Today</span><span>${examDateLabel(draft.days)}</span></div>
        </div>
        <div class="field field-tap">
          <div class="fieldrow"><span class="val">${examDateLabel(draft.days)}</span><span class="chev">&#8250;</span></div>
        </div>
        <button type="button" class="ghost" id="no-date">Date not announced</button>
      </div>`,
      cta: 'Continue',
    }, () => navigate('onboarding/shape'));

    root.querySelector('#no-date').addEventListener('click', () => {
      draft.days = 150;
      saveDraft();
      navigate('onboarding/date', { replace: true });
    });

  // ── 3 · shape ─────────────────────────────────────────────
  } else if (step === 'shape') {
    const list = WORK.map((w) => {
      const sel = w.id === draft.work ? ' sel' : '';
      return `<button type="button" class="opt${sel}" data-id="${w.id}">
        <span>
          <b>${w.t}</b>
          <span class="hrs"><span>${w.wd}h weekday</span><span>${w.we}h weekend</span></span>
        </span>
        <span class="tick">${sel ? TICK : ''}</span>
      </button>`;
    }).join('');

    shell({
      title: 'Your schedule',
      body: `<div class="list">${list}</div>`,
      cta: 'Continue',
    }, () => navigate('onboarding/hours'));

    root.querySelectorAll('.opt').forEach((btn) => {
      btn.addEventListener('click', () => {
        draft.work = btn.dataset.id;
        const w = WORK.find((x) => x.id === draft.work);
        if (w) { draft.wd = w.wd; draft.we = w.we; }
        saveDraft();
        root.querySelectorAll('.opt').forEach((b) => {
          b.classList.toggle('sel', b === btn);
          b.querySelector('.tick').innerHTML = b === btn ? TICK : '';
        });
      });
    });

  // ── 4 · hours ─────────────────────────────────────────────
  } else if (step === 'hours') {
    shell({
      title: 'Hours per day',
      body: `<div class="onboard-stack">
        <div class="slide">
          <div class="top"><b>Weekdays</b><i id="wd-v">${draft.wd} hrs</i></div>
          <input type="range" id="wd" min="1" max="14" step="0.5" value="${draft.wd}" aria-label="Weekday hours">
          <div class="ticks"><span>1h</span><span>7h</span><span>14h</span></div>
        </div>
        <div class="slide">
          <div class="top"><b>Weekends</b><i id="we-v">${draft.we} hrs</i></div>
          <input type="range" id="we" min="1" max="16" step="0.5" value="${draft.we}" aria-label="Weekend hours">
          <div class="ticks"><span>1h</span><span>8h</span><span>16h</span></div>
        </div>
        <div class="total"><b id="tot">0</b><span>hours total</span></div>
        <div class="field">
          <label>Study spot</label>
          <input type="text" class="text-field" id="place" value="${(draft.place || '').replace(/"/g, '&quot;')}" placeholder="Library, terrace, desk">
        </div>
      </div>`,
      cta: 'Build my plan',
    }, () => navigate('onboarding/cushion'));

    const wd = root.querySelector('#wd');
    const we = root.querySelector('#we');
    const tot = root.querySelector('#tot');
    const paint = () => {
      root.querySelector('#wd-v').textContent = wd.value + ' hrs';
      root.querySelector('#we-v').textContent = we.value + ' hrs';
      draft.wd = +wd.value;
      draft.we = +we.value;
      const weeks = Math.floor(draft.days / 7);
      const rem = draft.days % 7;
      tot.textContent = Math.round(weeks * (5 * draft.wd + 2 * draft.we) + rem * draft.wd);
      saveDraft();
    };
    wd.addEventListener('input', paint);
    we.addEventListener('input', paint);
    root.querySelector('#place').addEventListener('input', (e) => {
      draft.place = e.target.value;
      saveDraft();
    });
    paint();

  // ── 5 · cushion ───────────────────────────────────────────
  } else {
    const c = cushion(draft);
    const short = c.short;

    shell({
      title: 'Your plan',
      lede: `${c.coverage}% of syllabus before ${examDateLabel(draft.days)}.`,
      body: `<div class="verdict ${short ? 'is-short' : 'is-ok'}">
          <span class="n">${Math.abs(c.gap)}</span>
          <span class="u">hours<br>${short ? 'short' : 'spare'}</span>
        </div>
        <div class="gauge">
          <div class="cap"><span class="k">Hours</span><span class="v">${c.have} <em>/ ${c.need}</em></span></div>
          <div class="bar">
            <span class="have" style="width:${short ? c.coverage : 100}%"><b>${c.have}h</b></span>
            ${short ? `<span class="miss" style="width:${100 - c.coverage}%"><b>${c.gap}h</b></span>` : ''}
          </div>
        </div>
        ${short ? `<p class="sec">Close the gap</p>
        <div class="list">
          <button type="button" class="fixcard">
            <span class="metric">+${c.extraPerDay}h</span>
            <span class="ftxt"><b>More hours daily</b><span>${(draft.wd + c.extraPerDay).toFixed(1)}h per day</span></span>
            <span class="chev">&#8250;</span>
          </button>
          <button type="button" class="fixcard">
            <span class="metric">&minus;${c.topicsToDrop}</span>
            <span class="ftxt"><b>Drop topics</b><span>Lowest-yield first</span></span>
            <span class="chev">&#8250;</span>
          </button>
          <button type="button" class="fixcard">
            <span class="metric">${c.daysToPush}d</span>
            <span class="ftxt"><b>Later date</b><span>If you can move it</span></span>
            <span class="chev">&#8250;</span>
          </button>
        </div>` : `<p class="sec">Buffer</p>
        <div class="list">
          <button type="button" class="fixcard">
            <span class="metric">2&times;</span>
            <span class="ftxt"><b>Revision passes</b><span>Fit in your timeline</span></span>
            <span class="chev">&#8250;</span>
          </button>
          <button type="button" class="fixcard">
            <span class="metric">${c.bufferDays}d</span>
            <span class="ftxt"><b>Buffer days</b><span>Built in</span></span>
            <span class="chev">&#8250;</span>
          </button>
        </div>`}`,
      cta: 'Start day 1',
    }, () => {
      draft.date = new Date(Date.now() + draft.days * 86400000).toISOString().slice(0, 10);
      draft.blocksDone = { 0: true, 2: true, 5: true };
      saveState(draft);
      try { sessionStorage.removeItem('onboard_draft'); } catch (_) {}
      navigate('today', { replace: true });
    });
  }

  return () => {};
}
