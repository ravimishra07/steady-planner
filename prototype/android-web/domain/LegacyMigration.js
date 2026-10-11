// Port of domain/LegacyMigration.kt — legacy DataStore -> Room migration (pure).
// The web clone starts with fresh data, so these only run if legacy-shaped data is imported.
import { dataClass, coerceAtLeast, isBlank } from './kt.js';
import { plusDays, atStartOfDayMs, systemZoneId } from './time.js';
import { ExamAttempt, ExamAttemptStatus } from './ExamAttempt.js';
import { StudyPreferences } from './StudyPreferences.js';
import { TopicProgress, TopicProgressStatus } from './TopicProgress.js';
import { TargetNodeOverride, TargetNodeState } from './TargetSyllabus.js';
import { StudyPlanBlock, StudyActivityType, PlanBlockStatus, PlanBlockSource } from './StudyPlan.js';
import { StudySession, StudySessionStatus } from './StudyHistory.js';

/** Old positional key ("t1_0_3_2") -> stable node id: Map<String,String>. */
export function buildLegacyNodeIdMap(examPack, legacySections) {
  const map = new Map();
  examPack.subjects.forEach((subject, sectionIndex) => {
    const legacySection = legacySections[sectionIndex];
    if (!legacySection) return;
    subject.nodes.forEach((node, topicIndex) => {
      const legacyTopic = legacySection.topics[topicIndex];
      if (!legacyTopic) return;
      walkLegacyPair(node, legacyTopic, `t1_${sectionIndex}_${topicIndex}`, map);
    });
  });
  return map;
}

function walkLegacyPair(newNode, oldNode, oldKey, map) {
  map.set(oldKey, newNode.id);
  newNode.children.forEach((child, index) => {
    const oldChild = oldNode.children[index];
    if (!oldChild) return;
    walkLegacyPair(child, oldChild, `${oldKey}_${index}`, map);
  });
}

/** "section_$index" -> subject id. */
export const buildLegacySectionIdMap = (examPack) => new Map(examPack.subjects.map((s, i) => [`section_${i}`, s.id]));

export const LEGACY_ATTEMPT_ID = 'legacy_attempt';

/** SavedPlan -> ExamAttempt (best effort; anchored to migrationDate). */
export function migrateExamAttempt(examId, daysUntilExam, migrationDate, nowMs) {
  const targetDate = plusDays(migrationDate, coerceAtLeast(daysUntilExam, 0));
  return ExamAttempt({
    id: LEGACY_ATTEMPT_ID, examId, syllabusVersion: '', examDate: targetDate, targetCompletionDate: targetDate,
    createdAtEpochMs: nowMs, updatedAtEpochMs: nowMs, status: ExamAttemptStatus.ACTIVE,
  });
}

export const migrateStudyPreferences = (attemptId, studyPlace) =>
  StudyPreferences({ attemptId, defaultStudyPlace: !isBlank(studyPlace) ? studyPlace : null });

/** doneLeaves: Set<String>, nodeIdMap: Map. */
export const migrateTopicProgress = (attemptId, doneLeaves, nodeIdMap, nowMs) =>
  [...doneLeaves].map((k) => nodeIdMap.get(k)).filter((id) => id != null).map((nodeId) =>
    TopicProgress({ attemptId, nodeId, status: TopicProgressStatus.COVERED, coveredAtEpochMs: nowMs, updatedAtEpochMs: nowMs }));

export const migrateTargetOverrides = (attemptId, excludedSectionKeys, sectionIdMap, nowMs) =>
  [...excludedSectionKeys].map((k) => sectionIdMap.get(k)).filter((id) => id != null).map((nodeId) =>
    TargetNodeOverride({ attemptId, nodeId, state: TargetNodeState.EXCLUDED, updatedAtEpochMs: nowMs }));

export const LegacyRecordMigration = dataClass({ planBlock: undefined, session: undefined });

export function migrateStudySessionRecord(record, attemptId, nodeIdMap, nowMs) {
  const nodeId = nodeIdMap.get(record.nodeKey) ?? null;
  const blockId = `legacy_plan_${record.id}`;
  const activityType = record.isRevision ? StudyActivityType.REVISION : StudyActivityType.LEARN;
  const zoneId = systemZoneId();
  const startedAtEpochMs = atStartOfDayMs(record.date) + record.startMinuteOfDay * 60_000;
  const isCurrentlyRunning = record.runningEndsAtMs != null;
  const status = record.completed ? PlanBlockStatus.COMPLETED : PlanBlockStatus.PLANNED;

  const block = StudyPlanBlock({
    id: blockId, attemptId, nodeId, subjectId: record.subjectId, customTitle: record.title, activityType,
    scheduledDate: record.date, startMinuteOfDay: record.startMinuteOfDay, plannedMinutes: record.durationMinutes,
    status, source: PlanBlockSource.MANUAL, rescheduledFromId: null, replacedById: null,
    createdAtEpochMs: nowMs, updatedAtEpochMs: nowMs,
  });

  const base = {
    id: `legacy_session_${record.id}`, attemptId, nodeId, subjectId: record.subjectId, planBlockId: blockId,
    activityType, startedAtEpochMs, studyDate: record.date, timeZoneId: zoneId, pausedSeconds: 0,
    focusLockUsed: false, interruptionCount: 0, customTitle: record.title, createdAtEpochMs: nowMs, updatedAtEpochMs: nowMs,
  };
  let session = null;
  if (record.completed) {
    session = StudySession({ ...base, endedAtEpochMs: startedAtEpochMs + record.durationMinutes * 60_000, focusedSeconds: record.durationMinutes * 60, status: StudySessionStatus.COMPLETED });
  } else if (isCurrentlyRunning) {
    session = StudySession({ ...base, endedAtEpochMs: null, focusedSeconds: 0, status: StudySessionStatus.RUNNING });
  }
  return LegacyRecordMigration({ planBlock: block, session });
}
