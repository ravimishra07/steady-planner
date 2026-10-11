// Port of core/data/RemoteConfig.kt. Already defaults-only on Android (LocalRemoteConfig); same here.
import { DEFAULT_NEET_EXAM_DATE, resolveExamDate } from '../domain/ExamDates.js';

/**
 * interface RemoteConfig { bool(key, default); int(key, default); string(key, default); async refresh() }
 * Every key has a default; the app is correct offline and on first launch with no fetch.
 */
export class LocalRemoteConfig {
  bool(_key, def) { return def; }
  int(_key, def) { return def; }
  string(_key, def) { return def; }
  async refresh() {}
}

/** Keys live here so they cannot be typo'd at call sites. */
export const ConfigKeys = Object.freeze({
  PAYWALL_ENABLED: 'paywall_enabled',
  TIER2_ENABLED: 'tier2_enabled',
  DEFAULT_ACCENT_THEME: 'defaultAccentTheme',
  DEFAULT_BACKGROUND_THEME: 'defaultBackgroundTheme',
  THEME_CUSTOMIZATION_ENABLED: 'themeCustomizationEnabled',
  /** ISO date (yyyy-MM-dd) of the NEET exam. Default: DEFAULT_NEET_EXAM_DATE. */
  NEET_EXAM_DATE: 'neet_exam_date',
  /** True once NTA has announced the date. While false the UI says "expected, not yet announced". */
  NEET_EXAM_DATE_OFFICIAL: 'neet_exam_date_official',
});

/** RemoteConfig.neetExamDate(): the NEET exam date from config, with the compile-time default. */
export const neetExamDate = (remoteConfig) =>
  resolveExamDate(remoteConfig.string(ConfigKeys.NEET_EXAM_DATE, DEFAULT_NEET_EXAM_DATE));

/** RemoteConfig.neetExamDateOfficial(): whether neetExamDate has been officially announced. Default false. */
export const neetExamDateOfficial = (remoteConfig) => remoteConfig.bool(ConfigKeys.NEET_EXAM_DATE_OFFICIAL, false);
