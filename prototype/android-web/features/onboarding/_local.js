// _local.js — onboarding-only stand-ins for things the shared layers do not provide. Not a Kotlin file.
//
//  SavedStateHandle        androidx SavedStateHandle for the onboarding back-stack entry. Backed by
//                          sessionStorage (`sl.savedState.onboarding.<key>`): a page reload is the web's
//                          process death + restore, so the draft survives reload in the same tab, and a
//                          fresh tab (= fresh launch) starts clean. Cleared by OnboardingViewModel.onCleared.
//  withLocal / rememberLocal  Compose `remember { mutableStateOf() }` scoped to a composable's presence:
//                          state lives while the composable is drawn in consecutive render passes and is
//                          reset once it leaves the screen (nav.remember lives for the whole destination).
//                          Writes bump `tick` so the route re-renders.
//  rememberLocalKey        a key that is stable while a composable stays on screen and new each time it
//                          re-enters — used for rememberDatePickerState / rememberTimePickerState, whose
//                          state lives in nav.remember.
//  insetBorder(el, b)      Compose Modifier.border / Surface(border) draws inside the bounds without moving
//                          content; components.Surface uses a CSS border (shifts content by its width).
//                          This paints the stroke as an inset box-shadow on the visual element instead.
//  OutlinedTextFieldWithLabelPadding  M3 OutlinedTextField(label = …) pads its top by 8dp (half the minimized
//                          label line) so the notched label is inside its bounds; components' field does not.
//  DATE_PICKER_NO_HEADER   M3 DatePicker(title = null, headline = null, showModeToggle = false) draws no header
//                          and no divider; components.DatePicker always draws both. Class that hides them.
import { MutableStateFlow } from '../../platform/flow.js';
import { OutlinedTextField } from '../../design/components.js';

// ---- SavedStateHandle ------------------------------------------------------------------------
const SS_PREFIX = 'sl.savedState.onboarding.';
const ss = (() => { try { return window.sessionStorage; } catch { return null; } })();
export class SavedStateHandle {
  get(key) { try { const v = ss?.getItem(SS_PREFIX + key); return v == null ? null : JSON.parse(v); } catch { return null; } }
  set(key, value) { try { ss?.setItem(SS_PREFIX + key, JSON.stringify(value)); } catch {} }
  remove(key) { try { ss?.removeItem(SS_PREFIX + key); } catch {} }
}

// ---- Presence-scoped local state ---------------------------------------------------------------
export function createLocalStore() {
  return { map: new Map(), pass: 0, gen: 0, tick: new MutableStateFlow(0) };
}
let current = null;
/** Runs one render pass of the screen with `store` as the target of rememberLocal. */
export function withLocal(store, fn) {
  const prev = current;
  current = store;
  store.pass++;
  try { return fn(); } finally { current = prev; }
}
function entry(key, init) {
  const store = current;
  if (!store) {
    const v = { value: typeof init === 'function' ? init() : init, gen: 0 };
    return { store: null, e: v };
  }
  let e = store.map.get(key);
  if (!e || e.lastPass < store.pass - 1) {
    e = { value: typeof init === 'function' ? init() : init, gen: ++store.gen, lastPass: store.pass };
    store.map.set(key, e);
  }
  e.lastPass = store.pass;
  return { store, e };
}
/** → { value (get/set), set(v, force) }. Setting a different value (or force) re-renders. */
export function rememberLocal(key, init) {
  const { store, e } = entry(key, init);
  const set = (v, force = false) => {
    if (Object.is(e.value, v) && !force) return;
    e.value = v;
    if (store) store.tick.value = store.tick.value + 1;
  };
  return { get value() { return e.value; }, set value(v) { set(v); }, set };
}
export function rememberLocalKey(key) {
  const { e } = entry('key:' + key, null);
  return `ob:${key}:${e.gen}`;
}

// ---- Inset border --------------------------------------------------------------------------------
/** b = BorderStroke(width, color) | null. el = a composable root (touch-target wrapper or the surface). */
export function insetBorder(el, b) {
  const visual = el.classList?.contains('touch-target') ? el.firstElementChild : el;
  if (b && visual) visual.style.boxShadow = `inset 0 0 0 ${b.width} ${b.color}`;
  return el;
}

// ---- DatePicker without header -------------------------------------------------------------------
export const DATE_PICKER_NO_HEADER = 'ob-date-picker-no-header';
if (typeof document !== 'undefined' && !document.getElementById('ob-local-style')) {
  const st = document.createElement('style');
  st.id = 'ob-local-style';
  // components.DatePicker now omits header + divider itself when title and headline are null; this only hides
  // them if a caller still passes a title/headline.
  st.textContent = `.${DATE_PICKER_NO_HEADER} > .m3-date-picker-header, .${DATE_PICKER_NO_HEADER} > .m3-date-picker-divider { display: none !important; }`;
  document.head.appendChild(st);
}

// ---- OutlinedTextField top padding -------------------------------------------------------------
//  M3 OutlinedTextField with a label pads its top by half the minimized label line height (8dp) so the
//  notched label sits inside its bounds; components.OutlinedTextField omits it. Wraps the field with it.
export function OutlinedTextFieldWithLabelPadding(p) {
  const field = OutlinedTextField({ ...p, class: 'fill-w', css: { ...(p.fieldCss || {}) } });
  const wrap = document.createElement('div');
  // components.OutlinedTextField now applies the 8dp label padding itself; the wrapper only carries class/css.
  if (p.class) wrap.className = p.class;
  Object.assign(wrap.style, p.css || {});
  wrap.appendChild(field);
  return wrap;
}
