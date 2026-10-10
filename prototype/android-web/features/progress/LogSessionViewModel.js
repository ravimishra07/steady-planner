// Port of feature/progress/LogSessionViewModel.kt
// Logs a session studied away from the timer, or edits/deletes a past one when sessionId is set.
// Nothing here computes a metric: the session is written and every screen re-reads it.
import { MutableStateFlow } from '../../platform/flow.js';
import { sortedBy, coerceIn, coerceAtLeast } from '../../domain/kt.js';
import { today as localToday, minDate, nowMs, systemZoneId } from '../../domain/time.js';
import { chapterOf, findSubjectOf } from '../../domain/ExamPack.js';
import { currentMinuteOfDay } from '../../domain/StudySession.js';
import {
  toEntry, editedWith, manualStudySession,
  MANUAL_SESSION_STEP_MINUTES, MANUAL_SESSION_MIN_MINUTES, MANUAL_SESSION_MAX_MINUTES,
} from '../../domain/ManualSession.js';
import { randomUUID } from '../../data/common.js';
import { LogSessionUiState, SubjectChoice, ChapterChoice } from './LogSessionUiState.js';

const DEFAULT_LOOKBACK_MINUTES = 60;
const PAST_DAY_START = 18 * 60;

export class LogSessionViewModel {
  constructor(sessionId, initialDate, examPackRepository, attemptRepository, studySessionRepository) {
    Object.assign(this, { sessionId, initialDate, examPackRepository, attemptRepository, studySessionRepository });
    this._state = new MutableStateFlow(LogSessionUiState({ isEdit: sessionId != null }));
    this.state = this._state.asStateFlow();
    this.attemptId = null;
    this.original = null;
    this.originalChapterId = null;
    this.load();
  }

  get uiState() { return this.state; }

  async load() {
    try {
      const attempt = await this.attemptRepository.activeAttempt();
      if (attempt == null) {
        this._state.update((it) => it.copy({ loading: false, unavailable: true }));
        return;
      }
      this.attemptId = attempt.id;
      const pack = await this.examPackRepository.examPackFor(attempt.examId, attempt.id);
      const subjects = sortedBy(pack?.subjects ?? [], (it) => it.order).map((subject) => SubjectChoice({
        id: subject.id,
        name: subject.name,
        chapters: sortedBy(subject.nodes, (it) => it.order).map((it) => ChapterChoice({ id: it.id, title: it.title })),
      }));
      const session = this.sessionId != null ? await this.studySessionRepository.byId(this.sessionId) : null;
      if (this.sessionId != null && session == null) {
        this._state.update((it) => it.copy({ loading: false, unavailable: true }));
        return;
      }
      this.original = session;
      if (session != null) {
        const entry = toEntry(session);
        const chapter = entry.nodeId != null && pack ? chapterOf(pack, entry.nodeId) : null;
        this.originalChapterId = chapter?.id ?? entry.nodeId;
        this._state.update((it) => it.copy({
          loading: false,
          subjects,
          subjectId: entry.subjectId ?? (entry.nodeId != null && pack ? findSubjectOf(pack, entry.nodeId)?.id ?? null : null),
          chapterId: chapter?.id ?? entry.nodeId,
          activityType: entry.activityType,
          durationMinutes: entry.durationMinutes,
          date: entry.date,
          startMinuteOfDay: entry.startMinuteOfDay,
        }));
      } else {
        const today = localToday();
        const date = minDate(this.initialDate ?? today, today);
        // Default start: an hour before now today, early evening on a past day.
        const start = date === today ? coerceAtLeast(currentMinuteOfDay() - DEFAULT_LOOKBACK_MINUTES, 0) : PAST_DAY_START;
        this._state.update((it) => it.copy({
          loading: false,
          subjects,
          subjectId: subjects[0]?.id ?? null,
          date,
          startMinuteOfDay: start,
        }));
      }
    } catch (_) {
      this._state.update((it) => it.copy({ loading: false, unavailable: true }));
    }
  }

  selectSubject(id) { this._state.update((it) => (it.subjectId === id ? it : it.copy({ subjectId: id, chapterId: null }))); }

  selectChapter(id) { this._state.update((it) => it.copy({ chapterId: id })); }

  selectType(type) { this._state.update((it) => it.copy({ activityType: type })); }

  stepDuration(up) {
    this._state.update((it) => {
      const step = up ? MANUAL_SESSION_STEP_MINUTES : -MANUAL_SESSION_STEP_MINUTES;
      return it.copy({ durationMinutes: coerceIn(it.durationMinutes + step, MANUAL_SESSION_MIN_MINUTES, MANUAL_SESSION_MAX_MINUTES) });
    });
  }

  setDate(date) { this._state.update((it) => it.copy({ date: minDate(date, localToday()) })); }

  setStartMinute(minuteOfDay) { this._state.update((it) => it.copy({ startMinuteOfDay: coerceIn(minuteOfDay, 0, 24 * 60 - 1) })); }

  save() {
    const current = this._state.value;
    const attempt = this.attemptId; if (attempt == null) return;
    if (!current.canSave) return;
    this._state.update((it) => it.copy({ saving: true, saveFailed: false }));
    (async () => {
      try {
        const now = nowMs();
        const existing = this.original;
        if (existing != null) {
          // An unchanged chapter keeps the topic the session was recorded on.
          const entry = current.chapterId === this.originalChapterId ? current.entry.copy({ nodeId: existing.nodeId }) : current.entry;
          await this.studySessionRepository.updateSession(editedWith(existing, entry, now));
        } else {
          await this.studySessionRepository.logManualSession(
            manualStudySession(`manual_${randomUUID()}`, attempt, current.entry, systemZoneId(), now),
            now,
          );
        }
        this._state.update((it) => it.copy({ saving: false, finished: true }));
      } catch (_) {
        this._state.update((it) => it.copy({ saving: false, saveFailed: true }));
      }
    })();
  }

  delete() {
    const id = this.original?.id; if (id == null) return;
    this._state.update((it) => it.copy({ saving: true, saveFailed: false }));
    (async () => {
      try {
        await this.studySessionRepository.deleteSession(id);
        this._state.update((it) => it.copy({ saving: false, finished: true }));
      } catch (_) {
        this._state.update((it) => it.copy({ saving: false, saveFailed: true }));
      }
    })();
  }
}
