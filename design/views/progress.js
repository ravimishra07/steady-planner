import { loadState, DEFAULT_STATE, cushion, todayBlocks, doneMinutes, todayBudget } from '../data.js';
import { syllabusProgress } from '../syllabus-progress.js';
import { getState as focusState } from '../focus-timer.js';

export function mountProgress(root) {
  const s = loadState() || { ...DEFAULT_STATE };
  const c = cushion(s);
  const syl = syllabusProgress();
  const doneMin = doneMinutes(todayBlocks(), s.blocksDone || {});
  const budget = todayBudget(s);
  const todayPct = budget ? Math.min(100, Math.round((doneMin / 60 / budget) * 100)) : 0;
  const sessions = focusState().completedToday || 0;

  root.className = 'view view-progress';
  root.innerHTML = `
    <header class="screen-head">
      <h1>Progress</h1>
      <p class="screen-sub">${s.days} days to go</p>
    </header>

    <div class="view-scroll progress-scroll">
      <div class="card stat-hero">
        <div class="stat-big ${c.short ? 'is-short' : 'is-ok'}">${Math.abs(c.gap)}<em>hrs ${c.short ? 'short' : 'spare'}</em></div>
        <div class="gauge-track">
          <span class="g-have" style="width:${c.short ? c.coverage : 100}%"></span>
          ${c.short ? `<span class="g-short" style="width:${100 - c.coverage}%"></span>` : ''}
        </div>
        <p class="stat-note">${c.have} of ${c.need} hrs available before the exam</p>
      </div>

      <p class="sec-label">Today</p>
      <div class="card">
        <div class="row row-stack">
          <div class="row-head"><b>${(doneMin / 60).toFixed(1)} of ${budget} hrs</b><span class="metric-val">${todayPct}%</span></div>
          <div class="bar"><i style="width:${todayPct}%"></i></div>
          <p class="row-note">${sessions} focus session${sessions === 1 ? '' : 's'} completed</p>
        </div>
      </div>

      <p class="sec-label">Syllabus &middot; ${syl.hoursDone} of ${syl.hoursTotal} hrs</p>
      <div class="card">
        ${syl.sections
          .map(
            (sec) => `
          <div class="row row-stack">
            <div class="row-head"><b>${sec.name}</b><span class="metric-val">${sec.pct}%</span></div>
            <div class="bar"><i style="width:${sec.pct}%"></i></div>
            <p class="row-note">${sec.topicsDone} of ${sec.topicsTotal} topics &middot; ${sec.hoursDone}/${sec.hoursTotal} hrs &middot; ${sec.questions} questions</p>
          </div>`
          )
          .join('')}
      </div>

      <p class="foot-note">History starts from the day you begin ticking topics &mdash; nothing before that is stored.</p>
    </div>`;

  return () => {};
}
