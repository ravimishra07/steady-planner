// Port of feature/progress/ProgressUiState.kt
import { dataClass, lazyDefault } from '../../domain/kt.js';
import { InsightPeriod } from '../../domain/StudyAnalytics.js';

export const InsightsUiState = dataClass({
  loading: true,
  hasPlan: false,
  period: lazyDefault(() => InsightPeriod.WEEK),
  data: null,
  targetSections: [],
  showTargetManager: false,
  loadFailed: false,
  /** "Most-lost chapters" from the mistake log (§8.2), most tagged first. */
  mostLost: [],
  /** Pro, or the paywall switched off remotely. Gates the longer periods, forecast and most-lost chapters. */
  proUnlocked: false,
  /** One-shot: open the soft Pro prompt now. */
  showSoftPrompt: false,
});

const FREE_PERIOD_DAYS = 7;

/** Day and 7 days are free; longer Insights periods are Pro (§11.2). InsightPeriod.isProPeriod */
export const isProPeriod = (period) => (period.days ?? Number.MAX_SAFE_INTEGER) > FREE_PERIOD_DAYS;

export const LostChapterUi = dataClass({ title: undefined, count: undefined });

export const TargetSectionUi = dataClass({ key: undefined, name: undefined, excluded: undefined });

export const ProgressUiState = InsightsUiState;
