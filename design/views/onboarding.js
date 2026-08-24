import { EXAMS, WORK, saveState, DEFAULT_STATE, cushion } from '../data.js';

const TICK = '<svg width="16" height="16" viewBox="0 0 20 20" fill="none"><path d="M4.5 10.5l3.5 3.5 7.5-8" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const BACK = '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5L7 10l5.5 5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const STEPS = ['exam', 'date', 'shape', 'hours', 'cushion'];
const STEP_IX = Object.fromEntries(STEPS.map((s, i) => [s, i]));

/** Syllabus hours are only real for the exam that has a tree. */
const SYLLABUS_KNOWN = { cgl: { hrs: 634, topics: 49 } };

const dots = (step) =>
  STEPS.map((_, i) => `<i class="${i <= STEP_IX[step] ? 'on' : ''}"></i>`).join('');

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

  function shell({ eyebrow, title, lede, body, cta, note }, onContinue) {
    root.innerHTML = `
      <div class="onboard-bar">
        ${prev ? `<button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>`
               : '<span class="bar-sp" aria-hidden="true"></span>'}
        <div class="dots">${dots(step)}</div>
        <span class="bar-sp" aria-hidden="true"></span>
      </div>
      <div class="onboard-body">
        <p class="eyebrow">${eyebrow}</p>
        <h1>${title}</h1>
        ${lede ? `<p class="lede">${lede}</p>` : ''}
        ${body}
      </div>
      <div class="view-foot">
        <button type="button" class="cta" id="ob-cta">${cta}</button>
        ${note ? `<p class="foot-note">${note}</p>` : ''}
      </div>`;
    const back = root.querySelector('.back-chev');
    if (back && prev) back.addEventListener('click', () => navigate(prev));
    root.querySelector('#ob-cta').addEventListener('click', onContinue);
  }

  // ── 1 · exam ──────────────────────────────────────────────
  if (step === 'exam') {
    const list = EXAMS.map((e) => {
      const sel = e.id === draft.exam ? ' sel' : '';
      const k = SYLLABUS_KNOWN[e.id];
      return `<button type="button" class="exam${sel}" data-id="${e.id}">
        <span>
          <h3>${e.name}</h3>
          <span class="meta">${e.meta}</span>
          ${k ? `<span class="load"><span class="bar"><i style="width:100%"></i></span><em>${k.hrs} hrs &middot; ${k.topics} topics</em></span>` : ''}
        </span>
        <span class="tick">${sel ? TICK : ''}</span>
      </button>`;
    }).join('');

    shell({
      eyebrow: 'Step 1 of 4',
      title: 'Which exam?',
      lede: 'Pick the one you are actually sitting. You can add a second later.',
      body: `<div class="list">${list}</div>`,
      cta: 'Continue',
      note: 'Only SSC CGL has a full syllabus tree so far.',
    }, () => navigate('onboarding/date'));

    root.querySelectorAll('.exam').forEach((btn) => {
      btn.addEventListener('click', () => {
        draft.exam = btn.dataset.id;
        saveDraft();
        root.querySelectorAll('.exam').forEach((b) => {
          b.classList.toggle('sel', b === btn);
          b.querySelector('.tick').innerHTML = b === btn ? TICK : '';
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
      eyebrow: 'Step 2 of 4',
      title: 'Exam date',
      lede: 'Everything gets planned backward from this day.',
      body: `<div class="onboard-stack">
        <div class="countdown">
          <div class="cd-top">
            <span class="n">${draft.days}</span>
            <span class="l">days left<br>${weeks} weeks and ${rem} day${rem === 1 ? '' : 's'}</span>
          </div>
          <div class="cd-scale">${Array.from({ length: segs }, (_, i) => `<i class="${i < filled ? 'on' : ''}"></i>`).join('')}</div>
          <div class="cd-legend"><span>Today</span><span>${examDateLabel(draft.days)}</span></div>
        </div>
        <div class="field">
          <label>Exam date</label>
          <div class="fieldrow"><span class="val">${examDateLabel(draft.days)}</span><span class="chev">&#8250;</span></div>
        </div>
        <button type="button" class="ghost" id="no-date">Date not announced yet &rarr;</button>
      </div>`,
      cta: 'Continue',
      note: 'Change it any time. The plan rebuilds itself.',
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
          <span class="why">${w.s}</span>
          <span class="hrs"><span>${w.wd}h weekday</span><span>${w.we}h weekend</span></span>
        </span>
        <span class="tick">${sel ? TICK : ''}</span>
      </button>`;
    }).join('');

    shell({
      eyebrow: 'Step 3 of 4',
      title: 'Your day shape',
      lede: 'A working aspirant needs a different plan, not just a shorter one.',
      body: `<div class="list">${list}</div>`,
      cta: 'Continue',
      note: 'These are starting numbers — you tune them next.',
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
      eyebrow: 'Step 4 of 4',
      title: 'Hours per day',
      lede: 'Be honest. The next screen tells you whether it is enough.',
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
        <div class="total"><b id="tot">0</b><span>hours before the exam<br>at this pace</span></div>
        <div class="field">
          <label>Study spot</label>
          <input type="text" class="text-field" id="place" value="${(draft.place || '').replace(/"/g, '&quot;')}" placeholder="Library, terrace, corner desk">
        </div>
      </div>`,
      cta: 'Build my plan',
      note: 'Naming the place makes you far likelier to show up.',
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
      eyebrow: 'Your plan',
      title: '',
      lede: '',
      body: `<div class="verdict ${short ? 'is-short' : 'is-ok'}">
          <span class="n">${Math.abs(c.gap)}</span>
          <span class="u">hours<br>${short ? 'short' : 'spare'}</span>
        </div>
        <p class="lede">At ${draft.wd} hrs on weekdays you cover ${c.coverage}% of the syllabus before ${examDateLabel(draft.days)}.</p>
        <div class="gauge">
          <div class="cap"><span class="k">Hours available</span><span class="v">${c.have} <em>/ ${c.need}</em></span></div>
          <div class="bar">
            <span class="have" style="width:${short ? c.coverage : 100}%"><b>${c.have}h</b></span>
            ${short ? `<span class="miss" style="width:${100 - c.coverage}%"><b>${c.gap}h</b></span>` : ''}
          </div>
        </div>
        <p class="sec">${short ? 'Close the gap' : 'What the buffer buys you'}</p>
        <div class="list">
          ${short ? `
          <button type="button" class="fixcard">
            <span class="metric">+${c.extraPerDay}h</span>
            <span class="ftxt"><b>Add hours to every day</b><span>${(draft.wd + c.extraPerDay).toFixed(1)} hrs a day, weekdays too</span></span>
            <span class="chev">&#8250;</span>
          </button>
          <button type="button" class="fixcard">
            <span class="metric">&minus;${c.topicsToDrop}</span>
            <span class="ftxt"><b>Drop lowest-yield topics</b><span>Saves about ${c.topicsToDrop * 14} hrs</span></span>
            <span class="chev">&#8250;</span>
          </button>
          <button type="button" class="fixcard">
            <span class="metric">${c.daysToPush}d</span>
            <span class="ftxt"><b>Move the target date</b><span>Only if it is yours to move</span></span>
            <span class="chev">&#8250;</span>
          </button>` : `
          <button type="button" class="fixcard">
            <span class="metric">2&times;</span>
            <span class="ftxt"><b>Full revision passes fit</b><span>Inside your timeline</span></span>
            <span class="chev">&#8250;</span>
          </button>
          <button type="button" class="fixcard">
            <span class="metric">${c.bufferDays}d</span>
            <span class="ftxt"><b>Buffer days</b><span>Built in, not borrowed</span></span>
            <span class="chev">&#8250;</span>
          </button>`}
        </div>`,
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
