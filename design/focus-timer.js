/* focus-timer.js — the one source of truth for a focus session.
   Wall-clock based, so it stays accurate across route changes, tab
   backgrounding and reloads. Persisted under localStorage key `focus`. */

const KEY = 'focus';

/** @typedef {'idle'|'running'|'paused'|'done'} Status */

const EMPTY = {
  status: /** @type {Status} */ ('idle'),
  durationSec: 50 * 60,
  remainingSec: 50 * 60,
  endsAt: null, // epoch ms — only meaningful while running
  block: null, // { id, title, sub, tag }
  completedToday: 0,
};

let state = load();
const listeners = new Set();

function load() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...EMPTY };
    const s = { ...EMPTY, ...JSON.parse(raw) };
    // A session that was running while the app was closed keeps counting.
    if (s.status === 'running' && s.endsAt) {
      const left = Math.round((s.endsAt - Date.now()) / 1000);
      if (left <= 0) return { ...s, status: 'done', remainingSec: 0, endsAt: null };
      return { ...s, remainingSec: left };
    }
    return s;
  } catch (_) {
    return { ...EMPTY };
  }
}

function persist() {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch (_) {}
}

function set(patch) {
  state = { ...state, ...patch };
  persist();
  listeners.forEach((fn) => fn(getState()));
}

/** Current state, with `remainingSec` recomputed from the clock. */
export function getState() {
  if (state.status === 'running' && state.endsAt) {
    const left = Math.round((state.endsAt - Date.now()) / 1000);
    if (left <= 0) return { ...state, status: 'done', remainingSec: 0 };
    return { ...state, remainingSec: left };
  }
  return { ...state };
}

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function start(durationSec, block) {
  set({
    status: 'running',
    durationSec,
    remainingSec: durationSec,
    endsAt: Date.now() + durationSec * 1000,
    block: block || null,
  });
}

export function pause() {
  if (state.status !== 'running') return;
  const left = Math.max(0, Math.round((state.endsAt - Date.now()) / 1000));
  set({ status: 'paused', remainingSec: left, endsAt: null });
}

export function resume() {
  if (state.status !== 'paused') return;
  set({ status: 'running', endsAt: Date.now() + state.remainingSec * 1000 });
}

/** Stop and discard — the session does not count as completed. */
export function stop() {
  set({ status: 'idle', remainingSec: state.durationSec, endsAt: null });
}

/** Mark the running session finished and bank it. */
export function complete() {
  set({
    status: 'done',
    remainingSec: 0,
    endsAt: null,
    completedToday: (state.completedToday || 0) + 1,
  });
}

/** Clear a finished session so the screen offers a fresh one. */
export function reset() {
  set({ status: 'idle', remainingSec: state.durationSec, endsAt: null, block: null });
}

export function setDuration(durationSec) {
  if (state.status === 'running' || state.status === 'paused') return;
  set({ durationSec, remainingSec: durationSec });
}

export function clearAll() {
  state = { ...EMPTY };
  persist();
  listeners.forEach((fn) => fn(getState()));
}

export function formatClock(sec) {
  const s = Math.max(0, Math.round(sec));
  const m = String(Math.floor(s / 60)).padStart(2, '0');
  return m + ':' + String(s % 60).padStart(2, '0');
}
