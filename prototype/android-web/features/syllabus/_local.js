// _local.js — feature-local helpers for features/syllabus (no Kotlin counterpart).
//
//   R            res/values/strings.xml of :feature:syllabus (strings + plurals, English).
//   str(fmt,…)   String.format for the specifiers these strings use: %1$s %1$d %2$02d %1$.1f %%.
//   plural(p, n, …args)  pluralStringResource (en: one when n == 1, else other).
//   dp(name)     a tokens.css length (e.g. 'size-syllabusRailOffset') as a number, for SVG/canvas maths.
//   animateContentSize(el, key)  Modifier.animateContentSize(): animates height changes between re-renders
//                (spring approximated by a 300ms standard-easing tween).
//   combinedClickable(el, {onClick, onLongClick})  Modifier.combinedClickable: ripple + tap + long-press
//                (ViewConfiguration long-press timeout 400ms).
//   InsetlessScaffold({ containerColor, topBar, bottomBar, content, class, css })  M3 Scaffold whose
//                window insets were already consumed by the caller (Kotlin: padding(p).consumeWindowInsets(p)).
//                design/components.js Scaffold always adds status/gesture bar insets, which would double them.
//   rememberFor(key, identity, init)  `remember {}` inside a dialog that leaves composition when closed:
//                callers store a NEW object in their open-state flow per open; the state is recreated whenever
//                that identity changes, so every open starts fresh.
import { h } from '../../platform/h.js';
import { attachRipple, contentColorFor, slot } from '../../design/components.js';
import { remember } from '../../platform/nav.js';
import { MutableStateFlow } from '../../platform/flow.js';

export const R = {
  string: {
    organise_change_value: '%1$s → %2$s',
    syllabus_title: 'Syllabus',
    syllabus_percent_value: '%1$d%%',
    syllabus_hours_minutes: '%1$dh %2$02dm est.',
    syllabus_hours_short: '%1$dh est.',
    syllabus_expand: 'Expand',
    syllabus_collapse: 'Collapse',
    syllabus_mark_done: 'Mark done',
    syllabus_see_details: 'See Details',
    syllabus_hide_details: 'Hide Details',
    syllabus_start_topic: 'Start',
    syllabus_continue_topic: 'Continue: %1$s',
    syllabus_filter: 'Filter syllabus',
    syllabus_edit: 'Edit syllabus',
    syllabus_close_filters: 'Close filters',
    syllabus_reset_filters: 'Reset',
    syllabus_filter_due_compact: 'Not covered %1$d',
    syllabus_class_11: 'Class 11',
    syllabus_class_12: 'Class 12',
    syllabus_class_number: 'Class %1$d',
    syllabus_empty_title: 'Your syllabus is empty',
    syllabus_empty_body: 'Add at least one chapter before the app creates study work.',
    syllabus_add_first_chapter: 'Add a chapter',
    syllabus_parked: 'Parked',
    syllabus_yield_high: 'High yield',
    syllabus_yield_medium: 'Med yield',
    syllabus_yield_low: 'Low yield',
    syllabus_yield_no_data: 'No PYQ data',
    syllabus_yield_with_rate: '%1$s · ~%2$s Qs/yr (%3$s)',
    syllabus_yield_close: 'Close',
    syllabus_yield_no_data_body: 'Fewer than two years of question counts were found for this chapter, so it has no yield label.',
    syllabus_yield_rate_detail: 'About %1$s questions a year in NEET %2$s, averaged over %3$d years.',
    syllabus_yield_sources: 'Counts from: %1$s',
    syllabus_yield_method: 'For each year, the median across sources that listed this chapter; the figure is the average over the years. A guide from past papers, not a prediction.',
    organise_back: 'Back',
    organise_title: 'Organise',
    organise_plan: 'Plan',
    organise_syllabus: 'Syllabus',
    organise_discard: 'Discard',
    organise_preview: 'Preview plan',
    organise_add_chapter: 'Add chapter',
    organise_chapter_name: 'Chapter name',
    organise_estimated_hours: 'Estimated hours',
    organise_decrease_hours: 'Decrease estimated study time',
    organise_increase_hours: 'Increase estimated study time',
    organise_hours_hint: 'Hours are your planning estimate, not exam data.',
    organise_add: 'Add',
    organise_cancel: 'Cancel',
    organise_what_changes: 'What this changes',
    organise_chapters_in_play: 'Chapters in play',
    organise_hours_left: 'Hours of syllabus left (estimate)',
    organise_apply: 'Apply changes',
    organise_keep_editing: 'Keep editing',
    organise_save_failed: 'The changes could not be saved. Your previous plan is still available.',
    organise_today: 'Today',
    organise_hours_planned: '%1$.1fh planned',
    organise_session_meta: '%1$s · %2$dm · %3$s',
    organise_task_learn: 'Learn',
    organise_task_practice: 'Practice',
    organise_task_revise: 'Revise',
    organise_no_sessions_today: 'Nothing is planned for today.',
    organise_next_seven: 'Estimated next 7 days',
    organise_hours_short: '%1$.1fh',
    organise_hours_estimate: '%1$.1fh est.',
    organise_projection_note: 'The plan is rebuilt from your real free windows after you approve these changes.',
    organise_order: 'Order',
    organise_order_default: 'Default',
    organise_order_shortest: 'Shortest first',
    organise_order_high_yield: 'High-yield first',
    organise_order_hint: 'Order changes the real seven-day preview. Nothing is applied until you confirm.',
    organise_chapters: 'Chapters',
    organise_in_play: '%1$d in play',
    organise_delete_chapter: 'Delete chapter',
    organise_no_chapters: 'No chapters here yet. Add the first one to create a plan.',
    organise_my_syllabus: 'My syllabus',
    syllabus_stage_learned: 'Learned',
    syllabus_stage_ncert: 'NCERT read',
    syllabus_stage_practice: 'Practice/DPP',
    syllabus_stage_pyq: 'PYQ',
    syllabus_stage_revised: 'Revised',
    syllabus_stage_custom: 'Custom stage',
    syllabus_stage_revised_count: '%1$s ×%2$d',
    syllabus_stage_labels_title: 'Chapter stages',
    syllabus_stage_labels_body: 'Rename a stage, untick it to hide it, or name your own stage in the last row. Only Learned counts toward coverage.',
    syllabus_stage_labels_save: 'Save',
    syllabus_stage_labels_cancel: 'Cancel',
    syllabus_chapter_menu: 'Chapter options',
    syllabus_chapter_rename: 'Rename chapter',
    syllabus_chapter_reset_name: 'Reset to original name',
    syllabus_chapter_original_name: 'Original name: %1$s',
    syllabus_chapter_park: 'Park chapter',
    syllabus_chapter_unpark: 'Unpark',
    syllabus_chapter_coaching: 'Coaching lectures & DPPs',
    coaching_lectures: 'Lectures',
    coaching_dpps: 'DPPs',
    coaching_fewer_lectures: 'One lecture fewer',
    coaching_more_lectures: 'One lecture more',
    coaching_fewer_dpps: 'One DPP fewer',
    coaching_more_dpps: 'One DPP more',
    coaching_lectures_done_up_to: 'Done up to lecture %1$d of %2$d',
    coaching_dpps_done_up_to: 'Done up to DPP %1$d of %2$d',
    coaching_row_summary: 'Lectures %1$d/%2$d · DPPs %3$d/%4$d',
    syllabus_stage_custom_pro: 'Add your own stage · Pro',
  },
  plurals: {
    syllabus_show_chapters: { one: 'Show %1$d chapter', other: 'Show %1$d chapters' },
    syllabus_subject_summary: { one: '%1$d/%2$d chapter · %3$dh est. · %4$d Q', other: '%1$d/%2$d chapters · %3$dh est. · %4$d Q' },
    organise_changes: { one: '%1$d change', other: '%1$d changes' },
    organise_timeline_body: {
      one: '%1$d chapter in play · about %2$.1f hours of syllabus work (estimate)',
      other: '%1$d chapters in play · about %2$.1f hours of syllabus work (estimate)',
    },
  },
};

/** String.format (Locale default = en-US here). */
export function str(fmt, ...args) {
  let next = 0;
  return fmt.replace(/%(?:(\d+)\$)?(0?\d*)(?:\.(\d+))?([sdf%])/g, (_, pos, width, prec, conv) => {
    if (conv === '%') return '%';
    const v = args[pos ? Number(pos) - 1 : next++];
    let out;
    if (conv === 'd') out = String(Math.trunc(Number(v)));
    else if (conv === 'f') out = Number(v).toFixed(prec != null ? Number(prec) : 6);
    else out = String(v ?? 'null');
    if (width) {
      const w = Number(width);
      const pad = width.startsWith('0') ? '0' : ' ';
      while (out.length < w) out = pad + out;
    }
    return out;
  });
}

export function plural(p, quantity, ...args) {
  return str(quantity === 1 ? p.one : p.other, ...args);
}

const tokenCache = new Map();
/** A tokens.css length as px number. */
export function dp(name) {
  if (tokenCache.has(name)) return tokenCache.get(name);
  const v = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--' + name)) || 0;
  tokenCache.set(name, v);
  return v;
}

const lastHeights = new Map();
/** Modifier.animateContentSize(): call on a freshly rendered element; animates from the height the same key had last render. */
export function animateContentSize(el, key) {
  requestAnimationFrame(() => {
    if (!el.isConnected) return;
    const now = el.offsetHeight;
    const before = lastHeights.get(key);
    lastHeights.set(key, now);
    if (before == null || before === now) return;
    const prevOverflow = el.style.overflow;
    el.style.overflow = 'hidden';
    const root = getComputedStyle(document.documentElement);
    const a = el.animate([{ height: before + 'px' }, { height: now + 'px' }], {
      duration: parseFloat(root.getPropertyValue('--motion-medium2')) || 0,
      easing: root.getPropertyValue('--ease-standard').trim() || 'ease',
    });
    a.onfinish = a.oncancel = () => { el.style.overflow = prevOverflow; };
  });
  return el;
}

const LONG_PRESS_TIMEOUT_MS = 400; // android.view.ViewConfiguration long-press timeout (platform constant)
/** Modifier.combinedClickable(onClick, onLongClick). */
export function combinedClickable(el, { onClick, onLongClick }) {
  attachRipple(el, {});
  el.setAttribute('role', 'button');
  el.tabIndex = 0;
  let timer = null, longFired = false;
  const cancel = () => { clearTimeout(timer); timer = null; };
  el.addEventListener('pointerdown', (e) => {
    if (e.button !== 0) return;
    longFired = false;
    if (onLongClick) timer = setTimeout(() => { timer = null; longFired = true; onLongClick(); }, LONG_PRESS_TIMEOUT_MS);
  });
  el.addEventListener('pointerup', cancel);
  el.addEventListener('pointerleave', cancel);
  el.addEventListener('pointercancel', cancel);
  el.addEventListener('contextmenu', (e) => { if (onLongClick) e.preventDefault(); });
  el.addEventListener('click', (e) => {
    e.stopPropagation();
    if (longFired) { longFired = false; return; }
    onClick?.();
  });
  el.addEventListener('keydown', (e) => {
    if ((e.key === 'Enter' || e.key === ' ') && e.target === el) { e.preventDefault(); onClick?.(); }
  });
  return el;
}

/** Scaffold with insets already consumed: topBar, content (weight), bottomBar stacked in a column. */
export function InsetlessScaffold(p) {
  const container = p.containerColor || 'var(--c-background)';
  const root = h('div', { class: ['m3-scaffold', p.class] });
  Object.assign(root.style, {
    position: 'relative', display: 'flex', flexDirection: 'column', height: '100%', boxSizing: 'border-box',
    background: container, color: contentColorFor(container) || 'var(--c-onBackground)',
  });
  if (p.css) for (const k in p.css) root.style[k] = p.css[k];
  const top = slot(p.topBar), bottom = slot(p.bottomBar);
  if (top.length) root.append(h('div', { style: { flex: 'none' } }, ...top));
  const body = h('div', { style: { flex: '1 1 auto', minHeight: '0', position: 'relative', display: 'flex', flexDirection: 'column' } });
  body.append(...slot(p.content));
  root.append(body);
  if (bottom.length) root.append(h('div', { style: { flex: 'none', position: 'relative', zIndex: '1' } }, ...bottom));
  return root;
}

export function rememberFor(key, identity, init) {
  const holder = remember('dialogState:' + key, () => ({ identity: null, flow: null }));
  const st = holder.value;
  if (st.identity !== identity || !st.flow) {
    st.identity = identity;
    st.flow = new MutableStateFlow(typeof init === 'function' ? init() : init);
  }
  return st.flow;
}
