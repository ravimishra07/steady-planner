// _local.js — feature-local helpers for features/home (no Kotlin file of its own).
//
//  - R: android/feature/home/src/main/res/values/strings.xml (strings + plurals, English).
//  - stringResource / pluralStringResource: Android format args (%1$s, %1$d) via formatAndroid; plurals use
//    the English one/other rule.
//  - formatDatePattern(date, pattern): DateTimeFormatter.ofPattern(pattern, Locale.getDefault()) for the
//    pattern letters the home strings use (MMMM, MMM, EEEE, EEE, d, yyyy). Names come from Intl in the
//    browser locale.
//  - javaShortTime(minuteOfDay): java.text.DateFormat.getTimeInstance(SHORT) in the browser locale.
//  - Channel: kotlinx Channel(BUFFERED) + receiveAsFlow() for one collector (HomeViewModel.focusRequests).
//  - outer(): Compose `Modifier.padding` placed before a background/click is outer space → CSS margin.
//  - rememberMutable / rememberBox: `remember { mutableStateOf(..) }` for the home render passes. Values live in
//    the destination's remember memo; a write bumps `recomposeTick()` (HomeRoute observes it with the ViewModel
//    state). Like Compose, state whose composable was not composed in a pass is forgotten (reset to init) at
//    endComposition().
//  - bringIntoView(el): BringIntoViewRequester.bringIntoView() — minimal scroll of the nearest scroll container.
import { formatAndroid } from '../../data/common.js';
import { remember } from '../../platform/nav.js';
import { parseDate } from '../../domain/time.js';

export const R = Object.freeze({
  string: Object.freeze({
    home_empty_title: 'No plan yet',
    home_empty_body: 'Pick your exam and hours first.',
    home_setup_plan: 'Set up my plan',
    home_today: 'Today',
    home_month_pattern: 'MMMM yyyy',
    home_selected_day_pattern: 'EEEE, d MMM',
    home_revision_date_pattern: 'EEE d MMM',
    home_weekday_sunday: 'S',
    home_weekday_monday: 'M',
    home_weekday_tuesday: 'T',
    home_weekday_wednesday: 'W',
    home_weekday_thursday: 'T',
    home_weekday_friday: 'F',
    home_weekday_saturday: 'S',
    home_start_study: 'Start studying',
    home_add_study: 'Add study',
    home_start_now: 'Start now',
    home_add_to_plan: 'Add to plan',
    home_how_long: 'Set duration',
    home_duration_min: '%1$d min',
    home_duration_hours: '%1$dh',
    home_duration_hours_minutes: '%1$dh %2$dm',
    home_duration_topics: '%1$s · %2$s',
    home_sprint_length: 'Sprint length',
    home_ends_at: 'Ends at',
    home_revision_due: 'Revision due',
    home_cancel: 'Cancel',
    home_ok: 'OK',
    home_gap_free: '%1$s free',
    home_gap_break: '%1$s break',
    home_start_short: 'Start',
    home_continue_short: 'Continue',
    home_reschedule: 'Reschedule',
    home_choose_another_time: 'Choose another time',
    home_subject_title: '%1$s · %2$s',
    home_time_range: '%1$s – %2$s',
    home_reschedule_next_slot: 'Move to next available slot',
    home_reschedule_tomorrow: 'Move to tomorrow, same time',
    home_reschedule_choose_time: 'Choose a different time',
    home_skip_block: 'Skip it',
    home_add_something: 'Add something',
    home_now_marker: 'Now',
    home_picker_path: '%1$s · %2$s',
    home_estimated_hours: '%1$dh est.',
    home_topic_count_one: '1 topic',
    home_completed_summary_topics: '%1$s studied · %2$s',
    home_completed_summary: '%1$s studied',
    home_details_duration: '%1$s planned',
    home_expand_details: 'Show study details',
    home_collapse_details: 'Hide study details',
    home_missed_semantics: 'Needs a new time',
    home_completed_semantics: 'Completed',
    home_missed_blocks_body: 'Missed blocks are dropped from the past and their work goes back into the next days.',
    home_missed_blocks_yes: 'Yes',
    home_missed_blocks_not_now: 'Not now',
    home_view_day_log: 'See what you studied this day',
    home_fresh_start_title: 'Start fresh from today',
    home_fresh_start_body: 'Earlier days are behind you. Their work goes back into the queue and today\'s plan starts clean.',
    home_fresh_start_action: 'Start fresh',
    home_fit_banner: 'About %1$d h (estimate) doesn\'t fit before %2$s.',
    home_fit_banner_calibrated: 'About %1$d h at your pace doesn\'t fit before %2$s.',
    home_fit_adjust: 'Adjust plan',
    home_empty_today_title: 'Nothing planned yet',
    home_plan_my_day: 'Plan my day',
    home_studying_now: 'Studying · %1$s left',
    home_revision_tag: 'Revision',
    home_revised_count: 'Revised ×%1$d',
    home_summary_nothing: 'Nothing scheduled',
    home_summary_complete: 'Day complete',
    home_summary_left: '%1$s left',
    home_edit_plan: 'Edit plan',
    home_summary_logged: '%1$s of %2$s',
    home_summary_fixed: '%1$s fixed',
    home_summary_now: 'now',
    home_fixed_tag: 'Fixed',
    home_fixed_school: 'School / college',
    home_fixed_coaching: 'Coaching class',
    home_fixed_lecture: 'Online lectures',
    home_fixed_tuition: 'Tuition',
    home_fixed_work: 'Job',
    home_fixed_meal: 'Meal break',
    home_fixed_commute: 'Commute',
    home_fixed_other: 'Fixed time',
    home_recovery_title: 'The day changed',
    home_recovery_next: 'Next: %1$s',
    home_start_next_now: 'Start next now',
    home_replan_rest_today: 'Replan the rest of today',
    home_recovery_busy_now: 'You are busy right now. Replan to find the next safe window.',
    home_error_duration: 'Choose a duration longer than zero.',
    home_error_outside_day: 'Choose a time within your awake hours.',
    home_error_study_overlap: 'That time overlaps another study session.',
    home_error_fixed_overlap: 'That time overlaps a fixed commitment.',
    home_error_no_available_time: 'No safe slot was found in the next seven days.',
    home_accessibility_date_pattern: 'EEEE, d MMMM yyyy',
    home_calendar_expand: 'Show month calendar',
    home_calendar_collapse: 'Hide month calendar',
    home_calendar_state_expanded: '%1$s, expanded',
    home_calendar_state_collapsed: '%1$s, collapsed',
    home_calendar_select_date: 'Select %1$s',
    home_calendar_status_done: 'Study complete',
    home_calendar_status_partial: 'Study partly complete',
    home_calendar_status_today: 'Today',
    home_calendar_status_planned: 'Study planned',
    home_calendar_status_no_progress: 'No completed study',
    home_subject_physics: 'Physics',
    home_subject_physics_short: 'P',
    home_subject_chemistry: 'Chemistry',
    home_subject_chemistry_short: 'C',
    home_subject_botany: 'Botany',
    home_subject_botany_short: 'B',
    home_subject_zoology: 'Zoology',
    home_subject_zoology_short: 'Z',
    home_tag_read: 'Read',
    home_tag_practice: 'Practice',
    home_tag_revise: 'Revise',
    home_picker_search_hint: 'Search topics…',
    home_picker_browse_subject: 'Browse by subject',
    home_picker_suggestions: 'Suggestions',
    home_picker_no_match: 'No topics match. Try another word.',
    home_picker_back: 'Back',
    home_picker_close: 'Close',
    home_picker_choose: 'Choose',
    home_picker_browse_deeper: 'Browse inside %1$s',
    home_activity_type: 'What will you do?',
    home_activity_learn: 'Learn',
    home_activity_practice: 'Practice',
    home_activity_revision: 'Revise',
    home_activity_mock: 'Mock',
    home_coaching_taught_title: 'Taught in class today',
    home_coaching_batch_at: 'Batch at %1$s, lecture %2$d',
    home_coaching_batch_unset: 'Tell us where your batch is to track backlog.',
    home_coaching_next_lecture: '+1 lecture (%1$d)',
    home_coaching_other_chapter: 'Different chapter',
    home_coaching_pick_chapter: 'Pick chapter',
    home_coaching_lecture_number: 'Lecture',
    home_coaching_lecture_earlier: 'One lecture earlier',
    home_coaching_lecture_later: 'One lecture later',
    home_coaching_save: 'Save',
    home_coaching_cancel: 'Cancel',
    home_coaching_backlog_title: 'Coaching backlog',
    home_coaching_backlog_none: 'Level with your batch.',
    home_coaching_backlog_row: '%1$s: %2$d lectures, %3$d DPPs behind',
    home_coaching_clear_by: 'Clear by %1$s at %2$d/%3$d live/backlog',
    home_coaching_no_clear_date: 'No backlog time in the plan. Change the split in Settings → Plan.',
  }),
  plurals: Object.freeze({
    home_topics_count: { one: '%1$d topic', other: '%1$d topics' },
    home_picker_subject_summary: { one: '%1$d topic · %2$dh est.', other: '%1$d topics · %2$dh est.' },
    home_picker_subtopic_summary: { one: '%1$d subtopic · %2$s', other: '%1$d subtopics · %2$s' },
    home_missed_blocks_title: {
      one: 'You missed %1$d block. Rebalance the next days?',
      other: 'You missed %1$d blocks. Rebalance the next days?',
    },
    home_recovery_body: {
      one: '%1$d session needs a new time. %2$s is still free today.',
      other: '%1$d sessions need a new time. %2$s is still free today.',
    },
  }),
});

/** stringResource(id, ...args) — `id` is the template from R.string. */
export const stringResource = (fmt, ...args) => formatAndroid(fmt, ...args);
/** pluralStringResource(id, quantity, ...args) / resources.getQuantityString — English: one | other. */
export const pluralStringResource = (plural, quantity, ...args) =>
  formatAndroid(quantity === 1 ? plural.one : plural.other, ...args);

// ---- java.time pattern formatting -------------------------------------------------------------
function utcNoon(date) {
  const d = parseDate(date);
  const [y, m, dd] = d.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, dd, 12));
}
const part = (date, opts) => new Intl.DateTimeFormat(undefined, { ...opts, timeZone: 'UTC' }).format(utcNoon(date));

/** DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date) for MMMM MMM EEEE EEE d yyyy. */
export function formatDatePattern(date, pattern) {
  return pattern.replace(/MMMM|MMM|EEEE|EEE|yyyy|d/g, (tok) => {
    switch (tok) {
      case 'MMMM': return part(date, { month: 'long' });
      case 'MMM': return part(date, { month: 'short' });
      case 'EEEE': return part(date, { weekday: 'long' });
      case 'EEE': return part(date, { weekday: 'short' });
      case 'yyyy': return part(date, { year: 'numeric' });
      case 'd': return String(Number(date.slice(8, 10)));
      default: return tok;
    }
  });
}

/** java.text.DateFormat.getTimeInstance(DateFormat.SHORT).format(hh:mm today) */
export function javaShortTime(minuteOfDay) {
  const d = new Date(2000, 0, 1, Math.floor(minuteOfDay / 60), minuteOfDay % 60);
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(d);
}

// ---- Channel(BUFFERED).receiveAsFlow() ----------------------------------------------------------
export class Channel {
  constructor() { this._buffer = []; this._collector = null; }
  trySend(value) {
    if (this._collector) this._collector(value);
    else this._buffer.push(value);
    return true;
  }
  /** receiveAsFlow().collect(fn): one collector at a time; buffered values drain first. Returns stop(). */
  collect(fn) {
    this._collector = fn;
    const pending = this._buffer.splice(0);
    pending.forEach((v) => fn(v));
    return () => { if (this._collector === fn) this._collector = null; };
  }
}

/** Compose outer padding (Modifier.padding before background/click) → CSS margin object. */
export const outer = ({ top, bottom, start, end, horizontal, vertical } = {}) => {
  const css = {};
  if (top ?? vertical) css.marginTop = top ?? vertical;
  if (bottom ?? vertical) css.marginBottom = bottom ?? vertical;
  if (start ?? horizontal) css.marginLeft = start ?? horizontal;
  if (end ?? horizontal) css.marginRight = end ?? horizontal;
  return css;
};

// ---- remember { mutableStateOf() } with Compose forget-on-leave semantics ---------------------------
function registry() {
  return remember('home:registry', () => ({ used: new Set(), boxes: new Map() })).value;
}
/** Flow bumped on every rememberMutable write; HomeRoute combines it with the ViewModel state. */
export const recomposeTick = () => remember('home:recompose', 0);

/** Non-reactive remembered holder: `{ value }`. */
export function rememberBox(key, init) {
  const reg = registry();
  reg.used.add(key);
  let box = reg.boxes.get(key);
  if (!box) {
    box = { init, value: typeof init === 'function' ? init() : init };
    reg.boxes.set(key, box);
  }
  return box;
}
/** Reactive remembered state: reading `.value` returns the current value, writing re-renders Home. */
export function rememberMutable(key, init) {
  const box = rememberBox(key, init);
  const tick = recomposeTick();
  return {
    get value() { return box.value; },
    set value(v) { if (box.value === v) return; box.value = v; tick.value = tick.value + 1; },
  };
}
export function beginComposition() { registry().used = new Set(); }
/** State whose composable left the composition this pass starts over next time, like Compose. */
export function endComposition() {
  const reg = registry();
  for (const [key, box] of reg.boxes) {
    if (!reg.used.has(key)) reg.boxes.delete(key);
  }
}

/** BringIntoViewRequester.bringIntoView(): scroll the nearest scrolling ancestor just enough to show el. */
export function bringIntoView(el) {
  if (!el?.isConnected) return;
  let sc = el.parentElement;
  while (sc && !(sc.scrollHeight > sc.clientHeight && /(auto|scroll)/.test(getComputedStyle(sc).overflowY))) sc = sc.parentElement;
  if (!sc) return;
  const r = el.getBoundingClientRect();
  const c = sc.getBoundingClientRect();
  if (r.top < c.top) sc.scrollTop += r.top - c.top;
  else if (r.bottom > c.bottom) sc.scrollTop += Math.min(r.bottom - c.bottom, r.top - c.top);
}
