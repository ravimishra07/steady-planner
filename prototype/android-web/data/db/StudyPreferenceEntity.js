// Port of core/data/db/StudyPreferenceEntity.kt.
// Tables `study_preferences` (PK attemptId), `subject_study_preference` (PK attemptId+subjectId),
// `subject_preferred_window` (PK id).
import { dataClass } from '../../domain/kt.js';
import { DayOfWeek } from '../../domain/time.js';
import {
  StudyPreferences, SubjectStudyPreference, SubjectPreferredWindow, SubjectPriority, PreferenceStrength,
} from '../../domain/StudyPreferences.js';
import { PlanningOrder } from '../../domain/RollingPlan.js';

export const StudyPreferencesEntity = dataClass({
  attemptId: undefined, weekdayTargetMinutes: undefined, weekendTargetMinutes: undefined, planningOrder: undefined,
  preferredSessionMinutes: null, shortBreakMinutes: null, longBreakMinutes: null,
  autoScheduleEnabled: undefined, autoRescheduleMissed: undefined, autoScheduleRevision: undefined,
  defaultStudyPlace: null, allowLearnInRevisionPhase: undefined,
}, {
  toDomain() {
    return StudyPreferences({
      attemptId: this.attemptId,
      weekdayTargetMinutes: this.weekdayTargetMinutes,
      weekendTargetMinutes: this.weekendTargetMinutes,
      planningOrder: PlanningOrder.valueOf(this.planningOrder),
      preferredSessionMinutes: this.preferredSessionMinutes,
      shortBreakMinutes: this.shortBreakMinutes,
      longBreakMinutes: this.longBreakMinutes,
      autoScheduleEnabled: this.autoScheduleEnabled,
      autoRescheduleMissed: this.autoRescheduleMissed,
      autoScheduleRevision: this.autoScheduleRevision,
      defaultStudyPlace: this.defaultStudyPlace,
      allowLearnInRevisionPhase: this.allowLearnInRevisionPhase,
    });
  },
});
StudyPreferencesEntity.tableName = 'study_preferences';
StudyPreferencesEntity.primaryKeys = ['attemptId'];
StudyPreferencesEntity.booleanColumns = ['autoScheduleEnabled', 'autoRescheduleMissed', 'autoScheduleRevision', 'allowLearnInRevisionPhase'];

export const preferencesToEntity = (p) => StudyPreferencesEntity({
  attemptId: p.attemptId,
  weekdayTargetMinutes: p.weekdayTargetMinutes,
  weekendTargetMinutes: p.weekendTargetMinutes,
  planningOrder: p.planningOrder.name,
  preferredSessionMinutes: p.preferredSessionMinutes,
  shortBreakMinutes: p.shortBreakMinutes,
  longBreakMinutes: p.longBreakMinutes,
  autoScheduleEnabled: p.autoScheduleEnabled,
  autoRescheduleMissed: p.autoRescheduleMissed,
  autoScheduleRevision: p.autoScheduleRevision,
  defaultStudyPlace: p.defaultStudyPlace,
  allowLearnInRevisionPhase: p.allowLearnInRevisionPhase,
});

export const SubjectStudyPreferenceEntity = dataClass({
  attemptId: undefined, subjectId: undefined, priority: undefined, targetMinutesPerWeek: null,
}, {
  toDomain() {
    return SubjectStudyPreference({
      attemptId: this.attemptId,
      subjectId: this.subjectId,
      priority: SubjectPriority.valueOf(this.priority),
      targetMinutesPerWeek: this.targetMinutesPerWeek,
    });
  },
});
SubjectStudyPreferenceEntity.tableName = 'subject_study_preference';
SubjectStudyPreferenceEntity.primaryKeys = ['attemptId', 'subjectId'];

export const subjectPreferenceToEntity = (p) => SubjectStudyPreferenceEntity({
  attemptId: p.attemptId, subjectId: p.subjectId, priority: p.priority.name, targetMinutesPerWeek: p.targetMinutesPerWeek,
});

export const SubjectPreferredWindowEntity = dataClass({
  id: undefined, attemptId: undefined, subjectId: undefined, dayOfWeek: null,
  startMinuteOfDay: undefined, endMinuteOfDay: undefined, strength: undefined,
}, {
  toDomain() {
    return SubjectPreferredWindow({
      id: this.id,
      attemptId: this.attemptId,
      subjectId: this.subjectId,
      dayOfWeek: this.dayOfWeek == null ? null : DayOfWeek.valueOf(this.dayOfWeek),
      startMinuteOfDay: this.startMinuteOfDay,
      endMinuteOfDay: this.endMinuteOfDay,
      strength: PreferenceStrength.valueOf(this.strength),
    });
  },
});
SubjectPreferredWindowEntity.tableName = 'subject_preferred_window';
SubjectPreferredWindowEntity.primaryKeys = ['id'];

export const windowToEntity = (w) => SubjectPreferredWindowEntity({
  id: w.id, attemptId: w.attemptId, subjectId: w.subjectId, dayOfWeek: w.dayOfWeek?.name ?? null,
  startMinuteOfDay: w.startMinuteOfDay, endMinuteOfDay: w.endMinuteOfDay, strength: w.strength.name,
});

/** Kotlin overloads toEntity on the three domain types; dispatch on shape. */
export function toEntity(x) {
  if ('strength' in x) return windowToEntity(x);
  if ('priority' in x) return subjectPreferenceToEntity(x);
  return preferencesToEntity(x);
}

export class StudyPreferenceDao {
  constructor(prefs, subjects, windows) { this.prefs = prefs; this.subjects = subjects; this.windows = windows; }
  upsert(entity) { this.prefs.upsert(entity); }
  forAttempt(attemptId) { return this.prefs.first((r) => r.attemptId === attemptId); }
  deletePreferencesForAttempt(attemptId) { this.prefs.deleteWhere((r) => r.attemptId === attemptId); }
  upsertSubject(entity) { this.subjects.upsert(entity); }
  subjectPreferencesFor(attemptId) { return this.subjects.rows((r) => r.attemptId === attemptId); }
  deleteSubjectPreferencesForAttempt(attemptId) { this.subjects.deleteWhere((r) => r.attemptId === attemptId); }
  upsertWindow(entity) { this.windows.upsert(entity); }
  windowsFor(attemptId) { return this.windows.rows((r) => r.attemptId === attemptId); }
  deleteWindowsForAttempt(attemptId) { this.windows.deleteWhere((r) => r.attemptId === attemptId); }
}
