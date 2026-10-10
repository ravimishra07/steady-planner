// Port of feature/progress/DayLogUiState.kt
import { dataClass } from '../../domain/kt.js';

/** "What did I study" for one day (PRODUCTION_V1_PLAN §5.6). */
export const DayLogUiState = dataClass({
  date: undefined,
  loading: true,
  log: null,
  loadFailed: false,
});
