// Port of feature/progress/MockFormViewModel.kt — add or edit one mock (§8.1) with its lost-marks chapters (§8.2).
import { MutableStateFlow } from '../../platform/flow.js';
import { dataClass, lazyDefault, toIntOrNull, sortedBy } from '../../domain/kt.js';
import { today as localToday, minDate, nowMs } from '../../domain/time.js';
import { MockSubject, MockTest, MockTestType, MockMistake, MistakeType, mockTotal, NEET_MAX_TOTAL } from '../../domain/MockTest.js';
import { randomUUID } from '../../data/common.js';
import { SubjectChoice, ChapterChoice } from './LogSessionUiState.js';

/** String?.toScore(): Int? */
export const toScore = (s) => (s == null ? null : toIntOrNull(s.trim()));

export const MockFormUiState = dataClass(
  {
    loading: true,
    isEdit: false,
    unavailable: false,
    name: '',
    type: lazyDefault(() => MockTestType.FULL_MOCK),
    date: lazyDefault(() => localToday()),
    /** Map<MockSubject, String> */
    subjectScores: new Map(),
    totalText: '',
    maxTotalText: String(NEET_MAX_TOTAL),
    attemptedText: '',
    correctText: '',
    wrongText: '',
    mistakes: [],
    subjects: [],
    saving: false,
    saveFailed: false,
    finished: false,
    /** Pro, or the paywall switched off remotely. Gates tagging lost-marks chapters (§8.2). */
    proUnlocked: false,
  },
  {
    /** True once any subject score is typed: the total is then their sum and not editable. */
    get totalIsAuto() { return MockSubject.entries.some((it) => toScore(this.subjectScores.get(it)) != null); },
    get total() { return mockTotal(MockSubject.entries.map((it) => toScore(this.subjectScores.get(it))), toScore(this.totalText)); },
    get maxTotal() { const v = toScore(this.maxTotalText); return v != null && v > 0 ? v : null; },
    get canSave() { return !this.loading && !this.saving && !this.unavailable && this.total != null && this.maxTotal != null; },
    chapterTitle(id) {
      for (const s of this.subjects) { const c = s.chapters.find((it) => it.id === id); if (c) return c.title; }
      return id;
    },
  },
);

const MAX_NAME_LENGTH = 60;
const MAX_DIGITS = 4;
/** Digits with an optional leading minus: NEET marks negatively, so a section can be below zero. */
const scoreInput = (s) => {
  const negative = s.trim().startsWith('-');
  const digits = s.replace(/\D/g, '').slice(0, MAX_DIGITS);
  return negative ? `-${digits}` : digits;
};
const countInput = (s) => s.replace(/\D/g, '').slice(0, MAX_DIGITS);

export class MockFormViewModel {
  constructor(mockId, examPackRepository, attemptRepository, mockTestRepository, proRepository) {
    Object.assign(this, { mockId, examPackRepository, attemptRepository, mockTestRepository, proRepository });
    this._state = new MutableStateFlow(MockFormUiState({ isEdit: mockId != null }));
    this.state = this._state.asStateFlow();
    this.attemptId = null;
    this.load();
    this._unsubPro = proRepository.unlocked.subscribe((unlocked) => this._state.update((it) => it.copy({ proUnlocked: unlocked })));
  }

  get uiState() { return this.state; }

  onCleared() { this._unsubPro?.(); }

  async load() {
    try {
      const attempt = await this.attemptRepository.activeAttempt();
      if (attempt == null) {
        this._state.update((it) => it.copy({ loading: false, unavailable: true }));
        return;
      }
      this.attemptId = attempt.id;
      const pack = await this.examPackRepository.examPackFor(attempt.examId, attempt.id);
      const subjects = sortedBy(pack?.subjects ?? [], (it) => it.order).map((subject) =>
        SubjectChoice({ id: subject.id, name: subject.name, chapters: sortedBy(subject.nodes, (it) => it.order).map((it) => ChapterChoice({ id: it.id, title: it.title })) }));
      const mock = this.mockId != null ? await this.mockTestRepository.byId(this.mockId) : null;
      if (this.mockId != null && mock == null) {
        this._state.update((it) => it.copy({ loading: false, unavailable: true }));
        return;
      }
      const mistakes = mock != null ? await this.mockTestRepository.mistakesFor(mock.id) : [];
      this._state.update((current) => {
        if (mock == null) return current.copy({ loading: false, subjects });
        const scores = new Map(MockSubject.entries.map((s) => [s, mock.score(s)]).filter(([, v]) => v != null).map(([s, v]) => [s, String(v)]));
        return current.copy({
          loading: false,
          subjects,
          name: mock.name,
          type: mock.type,
          date: mock.date,
          subjectScores: scores,
          totalText: scores.size === 0 ? String(mock.total) : '',
          maxTotalText: String(mock.maxTotal),
          attemptedText: mock.attempted != null ? String(mock.attempted) : '',
          correctText: mock.correct != null ? String(mock.correct) : '',
          wrongText: mock.wrong != null ? String(mock.wrong) : '',
          mistakes,
        });
      });
    } catch (_) {
      this._state.update((it) => it.copy({ loading: false, unavailable: true }));
    }
  }

  setName(value) { this._state.update((it) => it.copy({ name: value.slice(0, MAX_NAME_LENGTH) })); }
  setType(value) { this._state.update((it) => it.copy({ type: value })); }
  setDate(value) { this._state.update((it) => it.copy({ date: minDate(value, localToday()) })); }
  setSubjectScore(subject, value) {
    this._state.update((it) => it.copy({ subjectScores: new Map([...it.subjectScores, [subject, scoreInput(value)]]) }));
  }
  setTotal(value) { this._state.update((it) => it.copy({ totalText: scoreInput(value) })); }
  setMaxTotal(value) { this._state.update((it) => it.copy({ maxTotalText: countInput(value) })); }
  setAttempted(value) { this._state.update((it) => it.copy({ attemptedText: countInput(value) })); }
  setCorrect(value) { this._state.update((it) => it.copy({ correctText: countInput(value) })); }
  setWrong(value) { this._state.update((it) => it.copy({ wrongText: countInput(value) })); }

  tagChapter(chapterId) {
    this._state.update((it) => (it.mistakes.some((m) => m.chapterId === chapterId)
      ? it
      : it.copy({ mistakes: [...it.mistakes, MockMistake({ mockId: this.mockId ?? '', chapterId, type: MistakeType.CONCEPT })] })));
  }

  setMistakeType(chapterId, type) {
    this._state.update((it) => it.copy({ mistakes: it.mistakes.map((m) => (m.chapterId === chapterId ? m.copy({ type }) : m)) }));
  }

  removeMistake(chapterId) {
    this._state.update((it) => it.copy({ mistakes: it.mistakes.filter((m) => m.chapterId !== chapterId) }));
  }

  save() {
    const current = this._state.value;
    const attempt = this.attemptId; if (attempt == null) return;
    const total = current.total; if (total == null) return;
    const maxTotal = current.maxTotal; if (maxTotal == null) return;
    if (!current.canSave) return;
    this._state.update((it) => it.copy({ saving: true, saveFailed: false }));
    (async () => {
      try {
        const id = this.mockId ?? `mock_${randomUUID()}`;
        const auto = current.totalIsAuto;
        const score = (s) => (auto ? toScore(current.subjectScores.get(s)) : null);
        const mock = MockTest({
          id,
          attemptId: attempt,
          date: current.date,
          name: current.name.trim(),
          type: current.type,
          physics: score(MockSubject.PHYSICS),
          chemistry: score(MockSubject.CHEMISTRY),
          botany: score(MockSubject.BOTANY),
          zoology: score(MockSubject.ZOOLOGY),
          total,
          maxTotal,
          attempted: toScore(current.attemptedText),
          correct: toScore(current.correctText),
          wrong: toScore(current.wrongText),
        });
        await this.mockTestRepository.save(mock, current.mistakes.map((it) => it.copy({ mockId: id })), nowMs());
        this._state.update((it) => it.copy({ saving: false, finished: true }));
      } catch (_) {
        this._state.update((it) => it.copy({ saving: false, saveFailed: true }));
      }
    })();
  }

  delete() {
    const id = this.mockId; if (id == null) return;
    this._state.update((it) => it.copy({ saving: true, saveFailed: false }));
    (async () => {
      try {
        await this.mockTestRepository.delete(id);
        this._state.update((it) => it.copy({ saving: false, finished: true }));
      } catch (_) {
        this._state.update((it) => it.copy({ saving: false, saveFailed: true }));
      }
    })();
  }
}
