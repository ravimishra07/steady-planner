import { EXAMS, WORK, saveState, DEFAULT_STATE, cushion } from '../data.js';

const TICK = '<svg width="16" height="16" viewBox="0 0 20 20" fill="none"><path d="M4.5 10.5l3.5 3.5 7.5-8" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const BACK = '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5L7 10l5.5 5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const STEPS = ['exam', 'date', 'shape', 'hours', 'cushion'];
const STEP_IX = Object.fromEntries(STEPS.map((s, i) => [s, i]));

function dots(step) {
  const ix = STEP_IX[step];
  return STEPS.map((_, i) => `<i class="${i <= ix ? 'on' : ''}"></i>`).join('');
}

function loadDraft() {
  try {
    const raw = sessionStorage.getItem('onboard_draft');
    return raw
      ? { ...DEFAULT_STATE, ...JSON.parse(raw) }
      : { ...DEFAULT_STATE, exam: 'cgl', work: 'col', wd: 4, we: 7, days: 118, place: 'Terrace' };
  } catch (_) {
    return { ...DEFAULT_STATE, exam: 'cgl', work: 'col', wd: 4, we: 7, days: 118, place: 'Terrace' };
  }
}

export function mountOnboarding(root, { navigate }, step) {
  root.className = 'view view-onboard screen sam-ios';
  const ix = STEP_IX[step];
  const prev = ix > 0 ? `onboarding/${STEPS[ix - 1]}` : null;
  const draft = loadDraft();

  function saveDraft() {
    try { sessionStorage.setItem('onboard_draft', JSON.stringify(draft)); } catch (_) {}
  }

  function shell(bar, body, footLabel, onContinue) {
    root.innerHTML = `
      <div class="onboard-bar">
        ${bar}
        <div class="dots">${dots(step)}</div>
        <span class="bar-sp" aria-hidden="true"></span>
      </div>
      <div class="onboard-body view-scroll">${body}</div>
      <div class="view-foot"><button type="button" class="cta" id="ob-cta">${footLabel}</button></div>`;
    const backBtn = root.querySelector('.back-chev');
    if (backBtn && prev) backBtn.addEventListener('click', () => navigate(prev));
    root.querySelector('#ob-cta').addEventListener('click', onContinue);
  }

  if (step === 'exam') {
    const list = EXAMS.slice(0, 4).map((e) => {
      const sel = e.id === draft.exam ? ' sel' : '';
      return `<button type="button" class="exam${sel}" data-id="${e.id}"><span><h3>${e.name}</h3></span><span class="tick">${sel ? TICK : ''}</span></button>`;
    }).join('');
    shell(
      '<span class="bar-sp" aria-hidden="true"></span>',
      `<h1>Which exam?</h1><div class="list">${list}</div>`,
      'Continue',
      () => navigate('onboarding/date'),
    );
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
  } else if (step === 'date') {
    shell(
      `<button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>`,
      `<h1>Exam date</h1>
      <div class="onboard-stack">
        <div class="countdown"><span class="n">${draft.days}</span><span class="l">days left</span></div>
        <div class="field"><label>Date</label><div class="val">19 Dec 2026</div></div>
        <button type="button" class="ghost">Not announced yet</button>
      </div>`,
      'Continue',
      () => navigate('onboarding/shape'),
    );
  } else if (step === 'shape') {
    const list = WORK.map((w) => {
      const sel = w.id === draft.work ? ' sel' : '';
      return `<button type="button" class="opt${sel}" data-id="${w.id}"><span><b>${w.t}</b></span><span class="tick">${sel ? TICK : ''}</span></button>`;
    }).join('');
    shell(
      `<button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>`,
      `<h1>Your day shape</h1><div class="list">${list}</div>`,
      'Continue',
      () => navigate('onboarding/hours'),
    );
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
  } else if (step === 'hours') {
    shell(
      `<button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>`,
      `<h1>Hours per day</h1>
      <div class="onboard-stack">
        <div class="slide"><div class="top"><b>Weekdays</b><i>${draft.wd}h</i></div><div class="track-line"><i style="width:${Math.round(draft.wd / 17 * 100)}%"></i></div></div>
        <div class="slide"><div class="top"><b>Weekends</b><i>${draft.we}h</i></div><div class="track-line"><i style="width:${Math.round(draft.we / 17 * 100)}%"></i></div></div>
        <div class="field"><label>Study spot</label><div class="val">${draft.place || 'Terrace'}</div></div>
      </div>`,
      'Build plan',
      () => navigate('onboarding/cushion'),
    );
  } else {
    const c = cushion(draft);
    const gap = c.gap;
    shell(
      `<button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>`,
      `<div class="onboard-stack">
        <div class="verdict">
          <p class="sec" style="font-size:var(--sam-fs-sm);font-weight:var(--sam-fw-semibold);letter-spacing:.08em;text-transform:uppercase;color:var(--sam-text-muted)">Your plan</p>
          <div class="big">${gap}</div>
          <div class="bigunit">hours short</div>
          <p class="lede">At ${draft.wd} hrs on weekdays you cover about ${c.coverage}% of the syllabus.</p>
        </div>
        <div class="gaugecard">
          <div class="gcap"><span>Against ${c.need} hrs needed</span><b>${c.have} / ${c.need}</b></div>
          <div class="track">
            <div class="have" style="width:${c.coverage}%">${c.have}h</div>
            <div class="short" style="width:${100 - c.coverage}%">${gap}h</div>
          </div>
        </div>
        <div>
          <p class="sec" style="margin-bottom:14px;font-size:var(--sam-fs-sm);font-weight:var(--sam-fw-semibold);letter-spacing:.08em;text-transform:uppercase;color:var(--sam-text-muted)">Close the gap</p>
          <div class="list">
            <button type="button" class="fixcard"><span class="metric">+${c.extraPerDay}h</span><span class="ftxt"><b>Add hours every day</b><span>${(draft.wd + c.extraPerDay).toFixed(1)} hrs/day for ${draft.days} days</span></span><span class="chev">&#8250;</span></button>
            <button type="button" class="fixcard"><span class="metric">&minus;${c.topicsToDrop}</span><span class="ftxt"><b>Drop lowest-yield topics</b><span>~${Math.round(gap * 0.09)} marks last year</span></span><span class="chev">&#8250;</span></button>
          </div>
        </div>
      </div>`,
      'Start day 1',
      () => {
        draft.date = new Date(Date.now() + draft.days * 86400000).toISOString().slice(0, 10);
        draft.blocksDone = { 0: true, 2: true, 5: true };
        saveState(draft);
        try { sessionStorage.removeItem('onboard_draft'); } catch (_) {}
        navigate('today', { replace: true });
      },
    );
  }

  return () => {};
}
