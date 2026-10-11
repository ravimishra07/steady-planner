// Port of core/data/SettingsStore.kt. DataStore "settings" -> prefs('settings'), same keys.
// Flows are StateFlows derived from prefs.data; *Once()/set*() are async like the suspend funs.
import { prefs } from '../platform/store.js';
import { dataClass, coerceIn } from '../domain/kt.js';

const backgroundKey = 'background_appearance';
const legacyThemeKey = 'theme_choice';
const paletteKey = 'accent_palette';
const focusDurationKey = 'focus_duration_sec';
const focusEndAlertKey = 'focus_end_alert';
const notificationPromptKey = 'notification_prompt_shown';
const dailyReminderEnabledKey = 'daily_reminder_enabled';
const dailyReminderMinuteKey = 'daily_reminder_minute';
const shareUsageDataKey = 'share_usage_data';

const MINUTES_PER_DAY = 24 * 60;

/** The daily "Today's plan" reminder: whether it is on, and when, as minutes after midnight. */
export const DailyReminderSetting = dataClass({ enabled: false, minuteOfDay: 7 * 60 });
DailyReminderSetting.DEFAULT_MINUTE_OF_DAY = 7 * 60;

/** String.toBackgroundAppearance(): existing System users keep a stable dark baseline. */
function toBackgroundAppearance(s) {
  switch (s) {
    case 'light': return 'light';
    case 'dark': return 'dark';
    default: return 'dark';
  }
}

const has = (p, k) => p[k] !== undefined && p[k] !== null;
const appearanceOf = (p) => (has(p, backgroundKey) ? p[backgroundKey] : has(p, legacyThemeKey) ? toBackgroundAppearance(p[legacyThemeKey]) : null);
const toDailyReminder = (p) => DailyReminderSetting({
  enabled: p[dailyReminderEnabledKey] ?? false,
  minuteOfDay: p[dailyReminderMinuteKey] ?? DailyReminderSetting.DEFAULT_MINUTE_OF_DAY,
});

/** The DataStore file, shared with BackupRepository (Kotlin: internal Context.dataStore). */
export const dataStore = () => prefs('settings');

export class SettingsStore {
  constructor(_context, _dispatchers) {
    const ds = dataStore();
    this._ds = ds;
    /** Null means no personal choice yet, so Remote Config defaults may be used. */
    this.backgroundAppearance = ds.data.map(appearanceOf).distinctUntilChanged((a, b) => a === b);
    this.accentPalette = ds.data.map((p) => p[paletteKey] ?? null).distinctUntilChanged((a, b) => a === b);
    /** End-of-session sound and vibration. On by default. */
    this.focusEndAlert = ds.data.map((p) => p[focusEndAlertKey] ?? true).distinctUntilChanged((a, b) => a === b);
    /** Settings -> "Share anonymous usage data". On by default. */
    this.shareUsageData = ds.data.map((p) => p[shareUsageDataKey] ?? true).distinctUntilChanged((a, b) => a === b);
    /** The optional "Today's plan" reminder (§10.1). Off by default. */
    this.dailyReminder = ds.data.map(toDailyReminder).distinctUntilChanged((a, b) => a.equals(b));
  }

  /** One-shot read for startup. */
  async backgroundAppearanceOnce() { return appearanceOf(this._ds.all()); }
  async setBackgroundAppearance(value) { this._ds.set(backgroundKey, value); }
  async accentPaletteOnce() { return this._ds.get(paletteKey, null); }
  async setAccentPalette(value) { this._ds.set(paletteKey, value); }
  async focusDurationSec() { return this._ds.get(focusDurationKey, SettingsStore.DEFAULT_FOCUS_DURATION_SEC); }
  async setFocusDurationSec(seconds) { this._ds.set(focusDurationKey, seconds); }
  async focusEndAlertOnce() { return this._ds.get(focusEndAlertKey, true); }
  async setFocusEndAlert(enabled) { this._ds.set(focusEndAlertKey, enabled); }
  /** Whether the notification permission has been asked for at a Focus start. */
  async notificationPromptShown() { return this._ds.get(notificationPromptKey, false); }
  async setNotificationPromptShown() { this._ds.set(notificationPromptKey, true); }
  async setShareUsageData(enabled) { this._ds.set(shareUsageDataKey, enabled); }
  async dailyReminderOnce() { return toDailyReminder(this._ds.all()); }
  async setDailyReminder(setting) {
    this._ds.edit((p) => {
      p[dailyReminderEnabledKey] = setting.enabled;
      p[dailyReminderMinuteKey] = coerceIn(setting.minuteOfDay, 0, MINUTES_PER_DAY - 1);
    });
  }
}
SettingsStore.DEFAULT_FOCUS_DURATION_SEC = 50 * 60;
