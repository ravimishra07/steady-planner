// Port of core/data/SavedPlan.kt. What legacy onboarding wrote; read only by the migration now.
import { dataClass, toIntOrNull, isBlank } from '../domain/kt.js';

export const SavedPlan = dataClass({
  examId: undefined,
  daysUntilExam: undefined,
  workId: undefined,
  weekdayHours: undefined,
  weekendHours: undefined,
  studyPlace: undefined,
  targetDateEpochDay: null,
  coachingId: '',
  wakeMinute: 6 * 60,
  sleepMinute: 23 * 60,
  useProvidedSyllabus: true,
  coveredSubjectIds: new Set(),
  commitments: new Set(),
});

export const SavedCommitment = dataClass({
  id: undefined,
  kind: undefined,
  startMinute: undefined,
  endMinute: undefined,
  /** Sunday = 0 through Saturday = 6, matching java.time via value modulo 7. */
  days: undefined,
  customLabel: null,
});

/** SavedPlan.decodedCommitments(). Fail closed per row: malformed data is ignored. */
export function decodedCommitments(plan) {
  const out = [];
  for (const encoded of plan.commitments) {
    const parts = encoded.split('|');
    if (parts.length < 5 || parts.length > 6) continue;
    const start = toIntOrNull(parts[2]); if (start == null) continue;
    const end = toIntOrNull(parts[3]); if (end == null) continue;
    const days = new Set(parts[4].split(',').map(toIntOrNull).filter((d) => d != null && d >= 0 && d <= 6));
    if (isBlank(parts[0]) || isBlank(parts[1]) || end <= start || days.size === 0) continue;
    let customLabel = parts[5];
    if (customLabel != null && !isBlank(customLabel)) {
      try { customLabel = decodeURIComponent(customLabel.replace(/\+/g, ' ')).trim(); } catch { customLabel = null; }
      if (customLabel != null && isBlank(customLabel)) customLabel = null;
    } else customLabel = null;
    out.push(SavedCommitment({ id: parts[0], kind: parts[1], startMinute: start, endMinute: end, days, customLabel }));
  }
  return out;
}
