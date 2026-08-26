import {
  getState, subscribe, start, pause, resume, stop, complete, reset, formatClock,
} from '../focus-timer.js';
import { loadState, todayBlocks, blockIsDone, TAG_LABELS } from '../data.js';

const R = 96;
const CIRC = 2 * Math.PI * R;

const ICON_PAUSE =
  '<svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><rect x="5" y="4" width="3.5" height="12" rx="1.2"/><rect x="11.5" y="4" width="3.5" height="12" rx="1.2"/></svg>';
const ICON_PLAY =
  '<svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><path d="M6.5 4.2v11.6a1 1 0 0 0 1.53.85l9.2-5.8a1 1 0 0 0 0-1.7l-9.2-5.8a1 1 0 0 0-1.53.85Z"/></svg>';
const ICON_CLOSE =
  '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M5.5 5.5l9 9M14.5 5.5l-9 9" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>';

/** Next unchecked, non-break block — what a fresh session defaults to. */
function nextBlock() {
  const s = loadState();
  const doneMap = (s && s.blocksDone) || {};
  return todayBlocks().find((b) => !b.break && !blockIsDone(doneMap, b.id)) || null;
}

export function mountFocus(root, { navigate }) {
  root.className = 'view view-focus';
  root.innerHTML = `
    <div class="top">
      <span class="lbl" id="f-lbl">Focus</span>
      <button type="button" class="glass-btn" id="close-focus" aria-label="Close">${ICON_CLOSE}</button>
    </div>

    <div class="dial">
      <svg width="220" height="220" viewBox="0 0 220 220" aria-hidden="true">
        <circle cx="110" cy="110" r="${R}" fill="none" stroke="var(--sam-elevated)" stroke-width="10"/>
        <circle id="f-arc" cx="110" cy="110" r="${R}" fill="none" stroke="var(--sam-success)"
                stroke-width="10" stroke-linecap="round" stroke-dasharray="${CIRC.toFixed(1)}"
                stroke-dashoffset="${CIRC.toFixed(1)}" transform="rotate(-90 110 110)"/>
      </svg>
      <div class="dialtxt">
        <div class="clock" id="f-clock" role="timer" aria-live="off">00:00</div>
        <div class="of" id="f-of"></div>
      </div>
    </div>

    <div class="task" id="f-task"></div>

    <div class="acts" id="f-acts"></div>`;

  const $ = (s) => root.querySelector(s);
  const arc = $('#f-arc');

  function paintTask(st) {
    const b = st.block || nextBlock();
    if (!b) {
      $('#f-task').innerHTML =
        '<b>Nothing left today</b><p>Every block is checked off. Start one anyway if you want extra time.</p>';
      return;
    }
    $('#f-task').innerHTML =
      `<span class="tag ${b.tag === 'rev' ? 'revt' : b.tag}">${TAG_LABELS[b.tag] || 'Study'}</span>` +
      `<b>${b.title}</b><p>${b.sub || ''}</p>`;
  }

  function paintActs(st) {
    const acts = $('#f-acts');
    if (st.status === 'running' || st.status === 'paused') {
      acts.innerHTML =
        `<button type="button" class="stop" id="f-stop">Stop</button>` +
        `<button type="button" class="pause" id="f-toggle" aria-label="${st.status === 'running' ? 'Pause' : 'Resume'}">` +
        `${st.status === 'running' ? ICON_PAUSE : ICON_PLAY}</button>`;
      $('#f-stop').addEventListener('click', () => {
        if (confirm('Stop this session? It will not be counted.')) stop();
      });
      $('#f-toggle').addEventListener('click', () => (st.status === 'running' ? pause() : resume()));
      return;
    }
    if (st.status === 'done') {
      acts.innerHTML =
        `<button type="button" class="cta" id="f-again">Start another</button>` +
        `<button type="button" class="ghost-btn" id="f-back">Back to today</button>`;
      $('#f-again').addEventListener('click', () => {
        reset();
        const b = nextBlock();
        start(getState().durationSec, b);
      });
      $('#f-back').addEventListener('click', () => navigate('today'));
      return;
    }
    // idle
    acts.innerHTML = `<button type="button" class="cta" id="f-start">Start ${Math.round(getState().durationSec / 60)}-minute session</button>`;
    $('#f-start').addEventListener('click', () => start(getState().durationSec, nextBlock()));
  }

  function paint(st) {
    const total = st.durationSec || 1;
    const elapsed = Math.min(total, total - st.remainingSec);
    const pct = st.status === 'idle' ? 0 : elapsed / total;

    arc.setAttribute('stroke-dashoffset', String(CIRC * (1 - pct)));
    arc.setAttribute(
      'stroke',
      st.status === 'paused' ? 'var(--sam-warning)' : st.status === 'done' ? 'var(--sam-brand)' : 'var(--sam-success)'
    );

    $('#f-clock').textContent = st.status === 'done' ? 'Done' : formatClock(st.remainingSec);
    $('#f-of').textContent =
      st.status === 'done'
        ? `${Math.round(total / 60)} min logged`
        : `of ${Math.round(total / 60)} min`;
    $('#f-lbl').textContent =
      st.status === 'running' ? 'Focus · running'
      : st.status === 'paused' ? 'Focus · paused'
      : st.status === 'done' ? 'Focus · complete'
      : 'Focus';

    root.dataset.status = st.status;
  }

  let last = getState();
  paint(last);
  paintTask(last);
  paintActs(last);

  // Repaint the clock every second; the store is wall-clock based so this
  // only reads, it never accumulates drift.
  const tick = setInterval(() => {
    const st = getState();
    if (st.status === 'running' && st.remainingSec <= 0) {
      complete();
      return;
    }
    if (st.status === 'running') paint(st);
  }, 250);

  const off = subscribe((st) => {
    const changedPhase = st.status !== last.status;
    last = st;
    paint(st);
    if (changedPhase) {
      paintTask(st);
      paintActs(st);
    }
  });

  $('#close-focus').addEventListener('click', () => navigate('today'));

  return () => {
    clearInterval(tick);
    off();
  };
}
