// _local.js — feature-local helpers for features/settings. Not a Kotlin file.
//  - R: res/values/strings.xml of :feature:settings (+ plurals).
//  - ScrollColumn: Modifier order `modifier.fillMaxSize().verticalScroll().padding(horizontal)` — the route
//    modifier (system-bar insets) sits outside the scroll viewport, the horizontal padding inside it.
//  - Date/time/number formatting stand-ins for java.time / java.text with the default locale.
import { h } from '../../platform/h.js';
import { formatAndroid } from '../../data/common.js';

export const R = {
  string: {
    more_title: 'Settings',
    more_your_plan: 'Your plan',
    more_no_plan: 'No plan yet',
    more_plan_card_subtitle: 'Finish by %1$s · %2$sh on weekdays',
    more_coverage_percent: '%1$d%%',
    more_covered: 'covered',
    more_section_plan_short: 'Plan',
    more_section_app_short: 'App',
    more_hours_pair: '%1$sh · %2$sh',
    more_appearance: 'Appearance',
    more_exam_date: 'NEET exam date',
    more_exam_date_expected: '%1$s (expected, not yet announced)',
    more_finish_by_date: 'Finish syllabus by',
    more_hours_breaks: 'Hours & focus',
    more_fixed_hours: 'Fixed hours',
    more_redo_confirm_title: 'Review your setup again?',
    more_redo_confirm_body: 'Your completed study history will stay. Saving will replace only future automatic sessions.',
    more_redo_confirm_yes: 'Review setup',
    more_redo_confirm_no: 'Cancel',
    more_section_about: 'About',
    more_privacy: 'Privacy',
    more_privacy_sub: 'What is stored and where',
    more_terms: 'Terms of use',
    more_terms_sub: 'The short version',
    more_about: 'About NEET Planner',
    more_about_version: 'Version %1$s',
    more_footer: 'NEET Planner · your study data stays on this phone',
    settings_exam_neet: 'NEET UG',

    settings_detail_title: 'Settings',
    settings_appearance: 'Appearance',
    settings_theme_color: 'Accent color',
    settings_theme_title: 'Theme',
    settings_theme_description: 'Choose an accent and background. Changes apply right away.',
    settings_theme_system: 'System',
    settings_theme_light: 'Light',
    settings_theme_dark: 'Dark',
    settings_palette_rose: 'Rose',
    settings_palette_blue: 'Blue',
    settings_palette_purple: 'Purple',
    settings_palette_green: 'Green',
    settings_palette_amber: 'Amber',
    settings_background_grey: 'Grey',
    settings_background_slate: 'Slate',
    settings_plan: 'Plan',
    settings_plan_question: 'Do you have a date you want to finish the syllabus by?',
    settings_plan_yes: 'Yes',
    settings_plan_not_yet: 'Not yet',
    settings_plan_deadline_detail: 'Work is spread over the days up to your finish-by date. Saving replans your future planner-made blocks only; your own and completed blocks stay.',
    settings_plan_steady_detail: 'Hours only. Saving replans your future planner-made blocks only; your own and completed blocks stay. Your finish-by date is kept in case you switch back.',
    settings_plan_exam_date: 'NEET exam date',
    settings_plan_exam_expected: 'expected, not yet announced',
    settings_plan_finish_by: 'Finish syllabus by',
    settings_plan_change: 'Change',
    settings_cancel: 'Cancel',
    settings_hours: 'Your hours',
    settings_weekdays: 'Weekdays',
    settings_weekends: 'Weekends',
    settings_hours_value: '%1$s h',
    settings_decrease_hours: 'Decrease %1$s study time',
    settings_increase_hours: 'Increase %1$s study time',
    settings_study_spot: 'Study spot',
    settings_study_spot_hint: 'Library, terrace, desk',
    settings_focus: 'Focus',
    settings_focus_length: 'Session length',
    settings_focus_end_alert: 'End-of-session sound & vibration',
    settings_focus_minutes: '%1$d min',
    settings_focus_custom: 'Custom',
    settings_focus_custom_title: 'Custom session length',
    settings_focus_custom_field: 'Minutes (%1$d–%2$d)',
    settings_data: 'Your data',
    settings_backup_detail: 'A backup is one file with your plan, progress, sessions, revisions, mocks and settings. It stays wherever you save it.',
    settings_export_backup: 'Export backup',
    settings_import_backup: 'Import backup',
    settings_import_confirm_title: 'Replace current data?',
    settings_import_confirm_body: 'Everything on this phone is replaced by the backup. The app restarts when it is done.',
    settings_import_yes: 'Replace',
    settings_importing: 'Importing…',
    settings_backup_exported: 'Backup saved.',
    settings_backup_export_failed: 'Could not save the backup. Try another location.',
    settings_backup_too_new: 'This backup is from a newer version of the app. Update the app, then import it.',
    settings_backup_incompatible: 'This backup is from an older version whose data format differs. Nothing was changed.',
    settings_backup_import_failed: 'This file is not a readable backup. Nothing was changed.',
    settings_reminders: 'Reminders',
    settings_reminder_daily: 'Daily plan reminder',
    settings_reminder_daily_detail: "One note a day with today's blocks. Nothing else.",
    settings_reminder_time: 'Reminder time',
    settings_reminder_denied: 'Notifications are off for this app, so the reminder stays off. Allow notifications in system settings, then turn it on here.',
    settings_loading: 'Loading your settings…',
    settings_load_failed: 'Your settings could not be loaded.',
    settings_retry: 'Try again',
    settings_save: 'Save changes',
    settings_saving: 'Saving…',
    settings_saved: 'Saved',
    settings_save_failed: 'Couldn’t save. Tap to dismiss, then try again.',
    settings_clear_plan: 'Clear plan and restart',
    settings_clear_confirm: 'Erase your plan, syllabus ticks, and focus sessions?',
    settings_clear_yes: 'Clear everything',
    settings_clearing: 'Clearing…',
    settings_clear_no: 'Cancel',
    settings_clear_failed_title: 'Couldn’t clear everything',
    settings_clear_failed_body: 'Some local data may remain. Try clearing the plan again.',
    settings_discard_title: 'Discard changes?',
    settings_discard_body: 'Your unsaved settings will be lost.',
    settings_discard_yes: 'Discard',
    settings_discard_no: 'Keep editing',
    settings_ok: 'OK',
    settings_back: 'Back',

    policy_privacy_title: 'Privacy',
    policy_privacy_body: "Your plan, syllabus progress, study sessions, revision, mocks and mistake tags stay in this app's private storage on your phone. They are never sent to us or anyone else, and they are excluded from Android backup and device transfer. Clearing app data or uninstalling deletes them.\n\nExport backup writes a file only where you choose. The app does not upload it.\n\nIf \"Share anonymous usage data\" is on (Settings → Privacy, on by default), the app sends usage events and crash reports to Google Firebase (Analytics and Crashlytics) over an encrypted connection. Events hold only categories and counts, never your name, chapter titles, scores or anything you typed. Firebase also records an app-instance ID, device model, Android version and an approximate region from your IP address. The Advertising ID is not collected and there are no ads. Turning the switch off stops this collection.\n\nPro is bought through Google Play, which handles payment. The app only keeps whether this Google account owns Pro.\n\nWe do not sell or share your data. Full policy: see the store listing.",
    policy_terms_title: 'Terms',
    policy_terms_body: 'NEET Planner is a study planning tool. Hours and coverage are estimates, not guarantees. Use your own judgment for exam prep.',
    policy_about_title: 'About NEET Planner',
    policy_about_body: 'NEET Planner helps you plan NEET preparation day by day. Version %1$s. Built offline-first for serious students.',
    more_backlog_split: 'Live / backlog split',
    more_backlog_split_value: '%1$d/%2$d',
    more_backlog_split_body: "Share of each study day for today's classes and DPPs (live) versus coaching backlog. The plan updates from today.",
    more_backlog_split_save: 'Save',

    paywall_title: 'Unlock Pro',
    paywall_title_owned: 'You have Pro',
    paywall_subtitle: 'One payment. Yours for good, on every phone signed in to the same Google account.',
    paywall_feature_tests: 'Mock score trend chart',
    paywall_feature_mistakes: 'Mistake log and most-lost chapters',
    paywall_feature_insights: 'Insights for 30 days, 3 months and all time, plus the finish forecast',
    paywall_feature_widget: 'Next-block home-screen widget with Start',
    paywall_feature_stage: 'Your own custom chapter stage',
    paywall_free_note: 'Your plan, syllabus, Focus, revision and backup stay free forever.',
    paywall_buy: 'Buy Pro · %1$s',
    paywall_price_fallback: '₹59 one-time',
    paywall_restore: 'Restore purchase',
    paywall_working: 'Talking to Google Play…',
    paywall_close: 'Close',
    pro_status_pending: 'Payment pending. Pro unlocks as soon as Google Play confirms it.',
    pro_status_purchased: 'Thanks! Pro is unlocked.',
    pro_status_restored: 'Pro restored.',
    pro_status_nothing: 'No Pro purchase found for this Google account.',
    pro_status_unavailable: 'Google Play is not available right now. Check your connection and try again.',
    pro_status_error: 'Something went wrong with Google Play. Nothing was charged.',
    more_section_pro: 'Pro',
    more_pro: 'Pro',
    more_pro_active: 'Unlocked',
    more_pro_unlocked_free: 'Everything is unlocked',
    more_pro_buy: 'Unlock · %1$s',
    more_restore: 'Restore purchase',
    more_restore_sub: 'Already bought Pro on another phone',
    more_debug_force_pro: 'Force Pro (debug build only)',
    settings_privacy: 'Privacy',
    settings_share_usage: 'Share anonymous usage data',
    settings_share_usage_detail: 'App events and crash reports via Google Firebase help fix bugs. No names, no study notes. Your plan and progress stay on this phone.',
    more_log_out: 'Log out',
    more_log_out_title: 'Log out?',
    more_log_out_body: 'Erases all data on this phone.',
  },
  plurals: {
    more_blocks: { one: '%1$d block', other: '%1$d blocks' },
  },
};

/** stringResource(id, ...args) */
export const stringResource = (id, ...args) => (args.length ? formatAndroid(R.string[id], ...args) : R.string[id].replace(/%%/g, '%'));
/** pluralStringResource(id, count, ...args) — English quantity rules. */
export const pluralStringResource = (id, count, ...args) =>
  formatAndroid(R.plurals[id][count === 1 ? 'one' : 'other'], ...args);

/**
 * ScrollColumn({ modifier, css, children }): outer box carries the route modifier (insets) and fills the
 * destination; the inner element scrolls and carries `css` (the padding applied after verticalScroll).
 */
export function ScrollColumn({ modifier, css, outerCss, scrollKey = 'settings-scroll', children }) {
  ensureStyle();
  const inner = h('div', { class: 'col scroll-y fill sl-settings-scroll', 'data-scroll-key': scrollKey });
  Object.assign(inner.style, css || {});
  inner.append(...[].concat(children).flat(Infinity).filter(Boolean));
  const outer = h('div', { class: 'col fill' });
  Object.assign(outer.style, modifier?.css || {}, outerCss || {});
  outer.style.minHeight = '0';
  outer.append(inner);
  return outer;
}

/**
 * A verticalScroll Column measures children with unbounded height: they never shrink. CSS flex items with
 * overflow (scroll-x rows, scroll hosts) would otherwise collapse; observe() hosts are display:contents.
 */
let styleInstalled = false;
function ensureStyle() {
  if (styleInstalled || typeof document === 'undefined') return;
  styleInstalled = true;
  const style = document.createElement('style');
  style.textContent = '.sl-settings-scroll > *, .sl-settings-scroll [data-render-root][style*="contents"] > * { flex-shrink: 0; }';
  document.head.appendChild(style);
}

/** Non-scrolling equivalent: `modifier.fillMaxSize().padding(horizontal)`. */
export function FillColumn({ modifier, css, children }) {
  const el = h('div', { class: 'col fill' });
  Object.assign(el.style, modifier?.css || {}, css || {});
  el.append(...[].concat(children).flat(Infinity).filter(Boolean));
  return el;
}

/** DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) for a 'YYYY-MM-DD' date. */
export function formatDMMMyyyy(date) {
  const [y, m, d] = date.split('-').map(Number);
  const month = new Date(y, m - 1, 1).toLocaleString(undefined, { month: 'short' });
  return `${d} ${month} ${y}`;
}

/** android.text.format.DateFormat.is24HourFormat(context) — the default locale's hour cycle. */
export function is24HourFormat() {
  try {
    const hc = new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions().hourCycle;
    return hc === 'h23' || hc === 'h24';
  } catch { return false; }
}

/** LocalTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) */
export function formatShortTime(minuteOfDay) {
  const d = new Date(2000, 0, 1, Math.floor(minuteOfDay / 60), minuteOfDay % 60);
  return d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

/** NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value) */
export const formatOneDecimal = (value) => new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value);

/** 'YYYY-MM-DD' ↔ UTC-midnight millis (atStartOfDay(ZoneOffset.UTC)). */
export const dateToUtcMillis = (date) => { const [y, m, d] = date.split('-').map(Number); return Date.UTC(y, m - 1, d); };
export const utcMillisToDate = (ms) => new Date(ms).toISOString().slice(0, 10);

/** Hidden <input type=file> standing in for ActivityResultContracts.OpenDocument(mimes). */
export function openDocument(mimes, onResult) {
  const input = h('input', { type: 'file' });
  input.accept = ['.json', ...mimes].join(',');
  input.style.display = 'none';
  input.addEventListener('change', () => {
    const file = input.files?.[0] ?? null;
    input.remove();
    onResult(file);
  });
  document.body.appendChild(input);
  input.click();
}
