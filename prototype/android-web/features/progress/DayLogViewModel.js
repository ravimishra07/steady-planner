// Port of feature/progress/DayLogViewModel.kt — loads one day's sessions; dayLog builds the list.
import { MutableStateFlow } from '../../platform/flow.js';
import { dayLog } from '../../domain/DayLog.js';
import { DayLogUiState } from './DayLogUiState.js';

export class DayLogViewModel {
  constructor(date, examPackRepository, attemptRepository, studySessionRepository, planRepository) {
    Object.assign(this, { date, examPackRepository, attemptRepository, studySessionRepository, planRepository });
    this._state = new MutableStateFlow(DayLogUiState({ date }));
    this.state = this._state.asStateFlow();
    this.refresh();
  }

  get uiState() { return this.state; }

  async refresh() {
    try {
      const attempt = await this.attemptRepository.activeAttempt();
      if (attempt == null) {
        this._state.update((it) => it.copy({ loading: false, log: null, loadFailed: false }));
        return;
      }
      const pack = await this.examPackRepository.examPackFor(attempt.examId, attempt.id);
      const sessions = await this.studySessionRepository.forDate(attempt.id, this.date);
      const customTitles = new Map((await this.planRepository.planBetween(attempt.id, this.date, this.date))
        .filter((block) => block.customTitle != null).map((block) => [block.id, block.customTitle]));
      const log = dayLog(this.date, sessions, pack, customTitles);
      this._state.update((it) => it.copy({ loading: false, log, loadFailed: false }));
    } catch (_) {
      this._state.update((it) => it.copy({ loading: false, loadFailed: true }));
    }
  }

  /** Deletes a session after the row's confirm; the day re-reads so the total follows. */
  async delete(sessionId) {
    try {
      await this.studySessionRepository.deleteSession(sessionId);
    } catch (_) {
      // The row stays; the next refresh shows the truth.
    }
    await this.refresh();
  }
}
