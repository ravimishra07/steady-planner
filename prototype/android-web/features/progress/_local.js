// _local.js — feature-local stand-ins for things the shared layers do not provide (not a Kotlin file).
//
//  RouteHost(flow, render)   observe() host that fills the NavHost slot as a flex column (Compose fillMaxSize root).
//  noShrink(el)              children of a verticalScroll Column keep their measured height (Compose gives
//                            scroll content unbounded height; CSS flex would shrink overflow:hidden Surfaces).
//  fixedHeight(btn)          Button/TextButton with Modifier.height(x) below 48dp / 40dp: Compose honours the fixed
//                            height over minimumInteractiveComponentSize and ButtonDefaults.MinHeight; the shared
//                            touch-target wrapper does not, so both minimums are cleared on the returned element.
//  keepFieldValue(value)     controlled OutlinedTextField: when the ViewModel rejects a keystroke its state does not
//                            change, so nothing re-renders; put the accepted value back into the focused input.
//  dateToUtcMillis / utcMillisToDate   LocalDate ↔ DatePicker UTC-midnight millis (ZoneOffset.UTC).
//  formatPattern(date, 'EEE'|'d MMM'|'MMM')  DateTimeFormatter.ofPattern(…, Locale.getDefault()).
//  Stack(...nodes)           display:contents wrapper (a composable emitting several siblings).
import { h } from '../../platform/h.js';
import { observe } from '../../platform/nav.js';

export function RouteHost(flow, render) {
  return observe(flow, render, { class: 'col', style: { flex: '1 1 auto', minHeight: '0' } });
}

export function noShrink(el) {
  for (const c of el.children) c.style.flexShrink = '0';
  return el;
}

export function fixedHeight(btn) {
  btn.style.minHeight = '0';
  // .touch-target's `flex: none` outranks .weight (later in tokens.css); restore Modifier.weight(1f).
  if (btn.classList.contains('weight')) btn.style.flex = '1 1 0';
  const inner = btn.firstElementChild;
  if (inner) { inner.style.minHeight = '0'; inner.style.height = '100%'; }
  return btn;
}

export function keepFieldValue(value) {
  const input = document.activeElement;
  if (input && 'value' in input && input.value !== value) {
    const pos = Math.min(input.selectionStart ?? value.length, value.length);
    input.value = value;
    try { input.setSelectionRange(pos, pos); } catch {}
  }
}

const parts = (date) => date.split('-').map(Number);
export const dateToUtcMillis = (date) => { const [y, m, d] = parts(date); return Date.UTC(y, m - 1, d); };
export const utcMillisToDate = (ms) => new Date(ms).toISOString().slice(0, 10);

export function formatPattern(date, pattern) {
  const [y, m, d] = parts(date);
  const dt = new Date(y, m - 1, d);
  if (pattern === 'EEE') return dt.toLocaleDateString(undefined, { weekday: 'short' });
  if (pattern === 'MMM') return dt.toLocaleDateString(undefined, { month: 'short' });
  if (pattern === 'd MMM') return `${d} ${dt.toLocaleDateString(undefined, { month: 'short' })}`;
  throw new Error('pattern ' + pattern);
}

export const Stack = (...nodes) => h('div', { style: { display: 'contents' } }, ...nodes);
