// Port of core/data/PlanStore.kt. DataStore "plan" -> prefs('plan'), same keys.
// Legacy store: production features no longer write it; the migration reads it.
// String sets are stored as JSON arrays and returned as Set.
import { prefs } from '../platform/store.js';
import { SavedPlan } from './SavedPlan.js';
import { TodayPrefs } from './TodayPrefs.js';

/** Kotlin: internal Context.planDataStore (BackupRepository exports it). */
export const planDataStore = () => prefs('plan');

const existsKey = 'plan_exists';
const examKey = 'exam_id';
const daysKey = 'days_until_exam';
const workKey = 'work_id';
const weekdayKey = 'weekday_hours';
const weekendKey = 'weekend_hours';
const placeKey = 'study_place';
const activeSubjectsKey = 'active_subjects';
const blocksDoneKey = 'blocks_done';
const targetDateKey = 'target_date_epoch_day';
const coachingKey = 'coaching_id';
const wakeMinuteKey = 'wake_minute';
const sleepMinuteKey = 'sleep_minute';
const useProvidedSyllabusKey = 'use_provided_syllabus';
const coveredSubjectIdsKey = 'covered_subject_ids';
const commitmentsKey = 'fixed_commitments';

/** Keys whose DataStore type is Float (for backup export typing). */
export const PLAN_FLOAT_KEYS = [weekdayKey, weekendKey];

export class PlanStore {
  constructor(_context, _dispatchers) { this._ds = planDataStore(); }

  async exists() { return this._ds.get(existsKey, false) ?? false; }

  /** A stale plan for another product must never route into the NEET app. */
  async existsForExam(examId) {
    const p = this._ds.all();
    return p[existsKey] === true && p[examKey] === examId;
  }

  async save(plan) {
    this._ds.edit((it) => {
      it[existsKey] = true;
      it[examKey] = plan.examId;
      it[daysKey] = plan.daysUntilExam;
      it[workKey] = plan.workId;
      it[weekdayKey] = plan.weekdayHours;
      it[weekendKey] = plan.weekendHours;
      it[placeKey] = plan.studyPlace;
      if (plan.targetDateEpochDay != null) it[targetDateKey] = String(plan.targetDateEpochDay);
      it[coachingKey] = plan.coachingId;
      it[wakeMinuteKey] = plan.wakeMinute;
      it[sleepMinuteKey] = plan.sleepMinute;
      it[useProvidedSyllabusKey] = plan.useProvidedSyllabus;
      it[coveredSubjectIdsKey] = [...plan.coveredSubjectIds];
      it[commitmentsKey] = [...plan.commitments];
    });
  }

  async load() {
    const p = this._ds.all();
    if (p[existsKey] !== true) return null;
    const req = [examKey, daysKey, workKey, weekdayKey, weekendKey];
    if (req.some((k) => p[k] == null)) return null;
    const target = p[targetDateKey];
    const targetNum = target != null && /^[+-]?\d+$/.test(target) ? Number(target) : null;
    return SavedPlan({
      examId: p[examKey],
      daysUntilExam: p[daysKey],
      workId: p[workKey],
      weekdayHours: p[weekdayKey],
      weekendHours: p[weekendKey],
      studyPlace: p[placeKey] ?? '',
      targetDateEpochDay: targetNum,
      coachingId: p[coachingKey] ?? '',
      wakeMinute: p[wakeMinuteKey] ?? 6 * 60,
      sleepMinute: p[sleepMinuteKey] ?? 23 * 60,
      useProvidedSyllabus: p[useProvidedSyllabusKey] ?? true,
      coveredSubjectIds: new Set(p[coveredSubjectIdsKey] ?? []),
      commitments: new Set(p[commitmentsKey] ?? []),
    });
  }

  async clear() { this._ds.clear(); }

  async loadTodayPrefs() {
    const p = this._ds.all();
    return TodayPrefs({
      activeSubjects: new Set(p[activeSubjectsKey] ?? []),
      blocksDone: new Set(p[blocksDoneKey] ?? []),
    });
  }

  async saveActiveSubjects(subjects) { this._ds.set(activeSubjectsKey, [...subjects]); }
  async saveBlocksDone(done) { this._ds.set(blocksDoneKey, [...done]); }

  async updateHours(weekdayHours, weekendHours, studyPlace) {
    if (this._ds.get(existsKey) !== true) return;
    this._ds.edit((it) => {
      it[weekdayKey] = weekdayHours;
      it[weekendKey] = weekendHours;
      it[placeKey] = studyPlace;
    });
  }
}
