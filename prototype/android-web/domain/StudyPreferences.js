// Port of domain/StudyPreferences.kt
import { dataClass, enumClass, lazyDefault } from './kt.js';
import { PlanningOrder } from './RollingPlan.js';

/** What the student SAYS about how they want to be scheduled — soft preferences. */
export const StudyPreferences = dataClass({
  attemptId: undefined,
  weekdayTargetMinutes: 4 * 60,
  weekendTargetMinutes: 7 * 60,
  planningOrder: lazyDefault(() => PlanningOrder.DEFAULT),
  preferredSessionMinutes: null,
  shortBreakMinutes: null,
  longBreakMinutes: null,
  autoScheduleEnabled: false,
  autoRescheduleMissed: false,
  autoScheduleRevision: true,
  /** Deadline mode: allow new Learn blocks between finish-by and the exam. Off by default. */
  allowLearnInRevisionPhase: false,
  defaultStudyPlace: null,
});
export const SubjectStudyPreference = dataClass({ attemptId: undefined, subjectId: undefined, priority: undefined, targetMinutesPerWeek: null });

export const SubjectPriority = enumClass(['LOW', 'NORMAL', 'HIGH']);

/** dayOfWeek: DayOfWeek? */
export const SubjectPreferredWindow = dataClass({ id: undefined, attemptId: undefined, subjectId: undefined, dayOfWeek: undefined, startMinuteOfDay: undefined, endMinuteOfDay: undefined, strength: undefined });

export const PreferenceStrength = enumClass(['PREFERRED', 'AVOID']);
