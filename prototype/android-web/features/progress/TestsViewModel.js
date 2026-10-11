// Port of feature/progress/TestsViewModel.kt — the "Tests" screen (§8.1): every logged mock, newest first, and the trend.
import { MutableStateFlow } from '../../platform/flow.js';
import { dataClass } from '../../domain/kt.js';
import { mockTrend } from '../../domain/Metrics.js';

export const TestsUiState = dataClass({
  loading: true,
  loadFailed: false,
  hasPlan: true,
  mocks: [],
  trend: [],
  /** Subject lines drawn on the trend chart next to the total. Set<MockSubject> */
  visibleSubjects: new Set(),
  /** Pro, or the paywall switched off remotely. Gates the trend chart. */
  proUnlocked: false,
});

export class TestsViewModel {
  constructor(attemptRepository, mockTestRepository, proRepository) {
    Object.assign(this, { attemptRepository, mockTestRepository, proRepository });
    this._state = new MutableStateFlow(TestsUiState());
    this.state = this._state.asStateFlow();
    this._unsubPro = proRepository.unlocked.subscribe((unlocked) => this._state.update((it) => it.copy({ proUnlocked: unlocked })));
  }

  get uiState() { return this.state; }

  onCleared() { this._unsubPro?.(); }

  async refresh() {
    try {
      const attempt = await this.attemptRepository.activeAttempt();
      if (attempt == null) {
        this._state.update((it) => it.copy({ loading: false, hasPlan: false }));
        return;
      }
      const mocks = await this.mockTestRepository.allFor(attempt.id);
      this._state.update((it) => it.copy({ loading: false, loadFailed: false, hasPlan: true, mocks, trend: mockTrend(mocks) }));
    } catch (_) {
      this._state.update((it) => it.copy({ loading: false, loadFailed: true }));
    }
  }

  toggleSubject(subject) {
    this._state.update((it) => {
      const next = new Set(it.visibleSubjects);
      if (next.has(subject)) next.delete(subject); else next.add(subject);
      return it.copy({ visibleSubjects: next });
    });
  }
}
