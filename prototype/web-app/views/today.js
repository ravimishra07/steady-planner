import {
  loadState,
  saveState,
  todayBudget,
  todayBlocks,
  TAG_LABELS,
  SUBJECTS,
  defaultActiveSubjects,
  doneMinutes,
  blockIsDone,
} from '../data.js';

const CHECK = '<svg width="14" height="14" viewBox="0 0 20 20" fill="none"><path d="M4.5 10.5l3.5 3.5 7.5-8" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const FILTER_ICON = '<svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M3.5 5.5h13M7.5 10h5M9.5 14.5h1" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>';

function parseMins(t) {
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
}

function formatGap(mins) {
  if (mins < 60) return `${mins} min`;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return m ? `${h}h ${m}m` : `${h}h`;
}

function weekAround(anchor) {
  const d = new Date(anchor);
  const start = new Date(d);
  start.setDate(d.getDate() - d.getDay());
  return Array.from({ length: 7 }, (_, i) => {
    const x = new Date(start);
    x.setDate(start.getDate() + i);
    return x;
  });
}

function dayStatus(offsetFromToday) {
  if (offsetFromToday < 0) return offsetFromToday === -1 ? 'partial' : 'done';
  if (offsetFromToday === 0) return 'today';
  if (offsetFromToday === 1) return 'planned';
  return 'rest';
}

function subjectMenuLabel(active) {
  if (active.length === SUBJECTS.length) return 'All subjects';
  if (active.length === 1) {
    return SUBJECTS.find((s) => s.id === active[0])?.label || 'Subjects';
  }
  return `${active.length} subjects`;
}

export function mountToday(root, { navigate }) {
  root.className = 'view view-today cal-home';

  let state = loadState();
  const allBlocks = todayBlocks();
  let selectedDate = new Date();
  selectedDate.setHours(0, 0, 0, 0);
  let menuOpen = false;

  if (state && !state.activeSubjects?.length) {
    state = { ...state, activeSubjects: defaultActiveSubjects() };
  }

  root.innerHTML = `
    <header class="cal-top" role="banner">
      <h1 class="cal-month" id="cal-month"></h1>
      <div class="cal-menu-wrap">
        <button type="button" class="glass-btn" id="subj-btn" aria-label="Filter subjects" aria-haspopup="menu" aria-expanded="false">
          ${FILTER_ICON}
        </button>
        <div class="cal-menu" id="subj-menu" hidden role="menu">
          <p class="cal-menu-head" id="subj-menu-head">Subjects</p>
        </div>
      </div>
    </header>
    <div class="week-strip" id="week-strip" role="tablist" aria-label="Week"></div>
    <div class="day-bar" id="day-bar"></div>
    <div class="timeline-wrap view-scroll" id="timeline"></div>
    <div class="today-dock">
      <button type="button" class="fab fab-round" id="fab" hidden aria-label="Add block">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 5v14M5 12h14" stroke-linecap="round"/></svg>
      </button>
    </div>`;

  const fab = root.querySelector('#fab');
  const subjBtn = root.querySelector('#subj-btn');
  const subjMenu = root.querySelector('#subj-menu');
  const subjMenuHead = root.querySelector('#subj-menu-head');

  fab.addEventListener('click', () => navigate('focus'));

  function activeSubjects() {
    return state?.activeSubjects?.length ? state.activeSubjects : defaultActiveSubjects();
  }

  function filteredBlocks() {
    const on = new Set(activeSubjects());
    return allBlocks.filter((b) => b.break || on.has(b.subject));
  }

  function closeMenu() {
    menuOpen = false;
    subjMenu.hidden = true;
    subjBtn.setAttribute('aria-expanded', 'false');
  }

  function toggleMenu() {
    menuOpen = !menuOpen;
    subjMenu.hidden = !menuOpen;
    subjBtn.setAttribute('aria-expanded', String(menuOpen));
    if (menuOpen) renderSubjectMenu();
  }

  subjBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    toggleMenu();
  });

  document.addEventListener('click', closeMenu);
  const unbindDoc = () => document.removeEventListener('click', closeMenu);

  function renderEmpty() {
    fab.hidden = true;
    root.querySelector('#cal-month').textContent = 'Schedule';
    subjBtn.hidden = true;
    root.querySelector('#week-strip').innerHTML = '';
    root.querySelector('#day-bar').innerHTML = '';
    root.querySelector('#timeline').innerHTML = `
      <div class="empty-card">
        <h2>No plan yet</h2>
        <p>Pick your exam and hours to see your week.</p>
        <button type="button" class="cta" id="onboard-cta">Set up my plan</button>
      </div>`;
    root.querySelector('#onboard-cta').addEventListener('click', () => navigate('onboarding/exam'));
  }

  function renderMonth() {
    root.querySelector('#cal-month').textContent = selectedDate.toLocaleDateString('en-IN', {
      month: 'long',
      year: 'numeric',
    });
  }

  function renderSubjectMenu() {
    const on = new Set(activeSubjects());
    subjMenuHead.textContent = subjectMenuLabel(activeSubjects());
    const items = SUBJECTS.map((s) => {
      const checked = on.has(s.id);
      return `<button type="button" class="cal-menu-item" role="menuitemcheckbox" aria-checked="${checked}" data-id="${s.id}">
        <span class="cal-menu-check">${checked ? CHECK : ''}</span>
        <span class="cal-menu-text">${s.label}</span>
      </button>`;
    }).join('');
    subjMenu.querySelectorAll('.cal-menu-item').forEach((el) => el.remove());
    subjMenu.insertAdjacentHTML('beforeend', items);

    subjMenu.querySelectorAll('.cal-menu-item').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.dataset.id;
        let next = [...activeSubjects()];
        if (next.includes(id)) next = next.filter((x) => x !== id);
        else next.push(id);
        if (!next.length) next = [id];
        state.activeSubjects = next;
        saveState(state);
        renderSubjectMenu();
        renderDayBar();
        renderTimeline();
      });
    });
  }

  function renderWeek() {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const days = weekAround(selectedDate);
    const labels = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

    root.querySelector('#week-strip').innerHTML = days.map((d, i) => {
      const isSel = d.toDateString() === selectedDate.toDateString();
      const off = Math.round((d - today) / 86400000);
      const st = dayStatus(off);
      const dots = st === 'done' ? '<i class="dot done"></i>'
        : st === 'partial' ? '<i class="dot partial"></i>'
        : st === 'today' || st === 'planned' ? '<i class="dot planned"></i>'
        : '';
      return `<button type="button" class="week-day${isSel ? ' on' : ''}" data-ts="${d.getTime()}" role="tab" aria-selected="${isSel}">
        <span class="wd">${labels[i]}</span>
        <span class="wn">${d.getDate()}</span>
        <span class="wdots">${dots}</span>
      </button>`;
    }).join('');

    root.querySelectorAll('.week-day').forEach((btn) => {
      btn.addEventListener('click', () => {
        selectedDate = new Date(Number(btn.dataset.ts));
        closeMenu();
        renderAll();
      });
    });
  }

  function renderDayBar() {
    const budget = todayBudget(state);
    const blocks = filteredBlocks();
    const doneMins = doneMinutes(blocks, state.blocksDone || {});
    const pct = budget > 0 ? Math.min(100, Math.round((doneMins / (budget * 60)) * 100)) : 0;
    const isToday = selectedDate.toDateString() === new Date().toDateString();
    const dayLabel = isToday
      ? 'Today'
      : selectedDate.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' });

    root.querySelector('#day-bar').innerHTML = `
      <span class="day-label">${dayLabel}</span>
      <span class="day-meta">${budget}h · ${pct}%</span>
      <div class="day-bar-track"><i style="width:${pct}%"></i></div>`;
  }

  function renderTimeline() {
    const done = state.blocksDone || {};
    const blocks = filteredBlocks();
    const items = [];
    let prevEnd = null;

    blocks.forEach((b, idx) => {
      if (!b.break && prevEnd !== null) {
        const gap = parseMins(b.time) - prevEnd;
        if (gap >= 45) items.push({ type: 'gap', mins: gap });
      }
      items.push({ type: 'block', block: b });
      if (!b.break) prevEnd = parseMins(b.time) + (b.mins || 0);
    });

    const html = items.map((item) => {
      if (item.type === 'gap') {
        return `<div class="tl-gap">
          <span class="tl-time"></span>
          <div class="tl-rail"><span class="tl-line dashed"></span></div>
          <div class="tl-gap-body"><p>${formatGap(item.mins)} free</p></div>
        </div>`;
      }
      const b = item.block;
      if (b.break) {
        return `<div class="tl-row tl-break">
          <span class="tl-time">${b.time}</span>
          <div class="tl-rail"><span class="tl-dot muted"></span><span class="tl-line"></span></div>
          <div class="tl-card muted"><span>Break · ${b.mins} min</span></div>
        </div>`;
      }
      const isDone = blockIsDone(done, b.id);
      const sub = SUBJECTS.find((s) => s.id === b.subject);
      const tagCls = b.tag === 'prac' ? 'prac' : b.tag === 'rev' ? 'revt' : 'read';
      return `<div class="tl-row${isDone ? ' done' : ''}" data-id="${b.id}">
        <span class="tl-time">${b.time}</span>
        <div class="tl-rail">
          <span class="tl-dot${isDone ? ' on' : ''}">${isDone ? CHECK : ''}</span>
          <span class="tl-line"></span>
        </div>
        <div class="tl-card">
          <div class="tl-card-top">
            ${sub ? `<span class="tl-sub">${sub.short}</span>` : ''}
            <span class="tag ${tagCls}">${TAG_LABELS[b.tag]}</span>
          </div>
          <b class="${isDone ? 'struck' : ''}">${b.title}</b>
          <span class="tl-meta">${b.sub}</span>
          <button type="button" class="tl-chk${isDone ? ' on' : ''}" aria-label="Mark done">${isDone ? CHECK : ''}</button>
        </div>
      </div>`;
    }).join('');

    const el = root.querySelector('#timeline');
    el.innerHTML = html || '<p class="tl-empty">No blocks for this filter.</p>';

    el.querySelectorAll('.tl-chk').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const row = btn.closest('.tl-row');
        const id = row.dataset.id;
        const numId = /^\d+$/.test(id) ? Number(id) : id;
        const next = !blockIsDone(state.blocksDone, numId);
        state.blocksDone = { ...(state.blocksDone || {}), [numId]: next };
        if (!next) {
          const copy = { ...state.blocksDone };
          delete copy[numId];
          delete copy[String(numId)];
          state.blocksDone = copy;
        }
        saveState(state);
        renderDayBar();
        renderTimeline();
      });
    });
  }

  function renderAll() {
    renderMonth();
    renderWeek();
    renderDayBar();
    renderTimeline();
    if (menuOpen) renderSubjectMenu();
  }

  function renderPlan() {
    fab.hidden = false;
    subjBtn.hidden = false;
    renderSubjectMenu();
    renderAll();
  }

  if (!state) renderEmpty();
  else renderPlan();

  return () => {
    unbindDoc();
    closeMenu();
  };
}
