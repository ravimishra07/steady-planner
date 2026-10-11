// Port of core/data/repo/MigrationRepository.kt. suspend -> async.
// Imports legacy DataStore state (PlanStore, StudySessionStore, SyllabusStore) into the normalized
// tables without ever deleting a valid legacy plan. In the web clone the legacy stores start empty,
// so on a fresh install this just marks the migration complete — same as a new Android install.
import { coerceAtLeast, toInt, toIntOrNull } from '../../domain/kt.js';
import { DayOfWeek, today as localToday, nowMs as clockNowMs, plusDays, ofEpochDay, parseDate, maxDate, atStartOfDayMs, systemZoneId } from '../../domain/time.js';
import { DEFAULT_NEET_EXAM_DATE } from '../../domain/ExamDates.js';
import { ExamAttempt, ExamAttemptStatus } from '../../domain/ExamAttempt.js';
import { NEET_EXAM_ID, findNode, findSubjectOf } from '../../domain/ExamPack.js';
import { PlanBlockSource, PlanBlockStatus, StudyActivityType, StudyPlanBlock } from '../../domain/StudyPlan.js';
import { StudyPreferences } from '../../domain/StudyPreferences.js';
import { StudySession, StudySessionSegment, StudySessionStatus } from '../../domain/StudyHistory.js';
import { TargetNodeOverride, TargetNodeState } from '../../domain/TargetSyllabus.js';
import { TopicProgress, TopicProgressStatus } from '../../domain/TopicProgress.js';
import { WeeklyAvailability } from '../../domain/Availability.js';
import { randomUUID } from '../common.js';
import { decodedCommitments } from '../SavedPlan.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { toEntity as attemptToEntity } from '../db/ExamAttemptEntity.js';
import { preferencesToEntity } from '../db/StudyPreferenceEntity.js';
import { weeklyToEntity } from '../db/AvailabilityEntity.js';
import { progressToEntity } from '../db/TopicProgressEntity.js';
import { toEntity as overrideToEntity } from '../db/TargetNodeOverrideEntity.js';
import { toEntity as blockToEntity } from '../db/StudyPlanBlockEntity.js';
import { sessionToEntity, segmentToEntity } from '../db/StudySessionEntity.js';

export class MigrationRepository {
  constructor({ context, dispatchers, planStore, studySessionStore, syllabusStore, migrationStore, attemptRepository, examPackRepository }) {
    this.planStore = planStore;
    this.studySessionStore = studySessionStore;
    this.syllabusStore = syllabusStore;
    this.migrationStore = migrationStore;
    this.attemptRepository = attemptRepository;
    this.examPackRepository = examPackRepository;
    this.db = PrepTrackerDatabase.get(context);
  }

  async migrateIfNeeded() {
    if ((await this.migrationStore.currentVersion()) >= MigrationRepository.CURRENT_MIGRATION_VERSION) return;
    const db = this.db;
    const legacyPlan = await this.planStore.load();
    const activeAttempt = db.examAttemptDao().activeOnce();
    if (activeAttempt?.examId === NEET_EXAM_ID) {
      // A normalized attempt already exists. Import any still-unrepresented legacy progress/history.
      const pack = await this.examPackRepository.examPackFor(NEET_EXAM_ID);
      await this.importLegacyRows(activeAttempt.id, pack, false);
    } else if (activeAttempt == null && legacyPlan?.examId === NEET_EXAM_ID) {
      const pack = await this.examPackRepository.examPackFor(NEET_EXAM_ID);
      const now = clockNowMs();
      const targetDate = legacyPlan.targetDateEpochDay != null
        ? ofEpochDay(legacyPlan.targetDateEpochDay)
        : plusDays(localToday(), coerceAtLeast(legacyPlan.daysUntilExam, 1));
      const attemptId = randomUUID();
      await db.withTransaction(() => {
        db.examAttemptDao().upsert(attemptToEntity(ExamAttempt({
          id: attemptId,
          examId: NEET_EXAM_ID,
          syllabusVersion: pack.syllabusVersion,
          examDate: maxDate(targetDate, parseDate(DEFAULT_NEET_EXAM_DATE)),
          targetCompletionDate: targetDate,
          createdAtEpochMs: now,
          updatedAtEpochMs: now,
          status: ExamAttemptStatus.ACTIVE,
        })));
        db.studyPreferenceDao().upsert(preferencesToEntity(StudyPreferences({
          attemptId,
          weekdayTargetMinutes: toInt(legacyPlan.weekdayHours * 60),
          weekendTargetMinutes: toInt(legacyPlan.weekendHours * 60),
          autoScheduleRevision: true,
          defaultStudyPlace: legacyPlan.studyPlace.trim() === '' ? null : legacyPlan.studyPlace,
        })));
        db.availabilityDao().upsertWeekly(this.legacyAvailability(attemptId, legacyPlan).map(weeklyToEntity));
      });
      await this.importLegacyRows(attemptId, pack, true);
    }
    // Unsupported old-exam data is deliberately not destroyed.
    await this.migrationStore.markComplete(MigrationRepository.CURRENT_MIGRATION_VERSION);
    await this.attemptRepository.refreshHasAttemptFlag();
  }

  /** private */
  async importLegacyRows(attemptId, pack, includeAttemptSetup) {
    const sessions = await this.studySessionStore.loadAll();
    const syllabus = await this.syllabusStore.load();
    const now = clockNowMs();
    const db = this.db;
    await db.withTransaction(() => {
      const progress = [];
      for (const legacyId of syllabus.doneLeaves) {
        const nodeId = this.stableNodeId(pack, legacyId);
        if (nodeId != null) {
          progress.push(progressToEntity(TopicProgress({
            attemptId, nodeId, status: TopicProgressStatus.COVERED, coveredAtEpochMs: now, updatedAtEpochMs: now,
          })));
        }
      }
      if (progress.length > 0) db.topicProgressDao().upsertAll(progress);

      if (includeAttemptSetup) {
        const subjects = [...syllabus.excludedSectionKeys].map((key) => {
          const index = toIntOrNull(key.startsWith('section_') ? key.slice('section_'.length) : key);
          return index == null ? null : (pack.subjects[index] ?? null);
        }).filter((s) => s != null);
        for (const node of subjects.flatMap((it) => it.nodes)) {
          db.targetNodeOverrideDao().upsert(overrideToEntity(TargetNodeOverride({
            attemptId, nodeId: node.id, state: TargetNodeState.EXCLUDED, updatedAtEpochMs: now,
          })));
        }
      }

      for (const legacy of sessions) {
        const nodeId = this.stableNodeId(pack, legacy.nodeKey);
        const subjectId = (nodeId != null ? findSubjectOf(pack, nodeId)?.id : null)
          ?? pack.subjects.find((it) => it.id === legacy.subjectId)?.id ?? null;
        const existingBlock = db.studyPlanBlockDao().byId(legacy.id);
        if (existingBlock == null) {
          db.studyPlanBlockDao().upsert(blockToEntity(StudyPlanBlock({
            id: legacy.id,
            attemptId,
            nodeId,
            subjectId,
            customTitle: nodeId == null ? legacy.title : null,
            activityType: legacy.isRevision ? StudyActivityType.REVISION : StudyActivityType.LEARN,
            scheduledDate: legacy.date,
            startMinuteOfDay: legacy.startMinuteOfDay,
            plannedMinutes: legacy.durationMinutes,
            status: legacy.completed ? PlanBlockStatus.COMPLETED : PlanBlockStatus.PLANNED,
            source: legacy.id.startsWith('auto_') ? PlanBlockSource.AUTO : PlanBlockSource.MANUAL,
            rescheduledFromId: null,
            replacedById: null,
            createdAtEpochMs: now,
            updatedAtEpochMs: now,
          })));
        }

        if ((legacy.completed || legacy.runningEndsAtMs != null) && db.studySessionDao().byId(legacy.id) == null) {
          const startedAt = atStartOfDayMs(legacy.date) + legacy.startMinuteOfDay * 60_000;
          const endedAt = legacy.completed ? startedAt + legacy.durationMinutes * 60_000 : null;
          const status = legacy.completed ? StudySessionStatus.COMPLETED : StudySessionStatus.RUNNING;
          db.studySessionDao().upsert(sessionToEntity(StudySession({
            id: legacy.id,
            attemptId,
            nodeId,
            subjectId,
            planBlockId: legacy.id,
            activityType: legacy.isRevision ? StudyActivityType.REVISION : StudyActivityType.LEARN,
            startedAtEpochMs: startedAt,
            endedAtEpochMs: endedAt,
            studyDate: legacy.date,
            timeZoneId: systemZoneId(),
            focusedSeconds: legacy.completed ? legacy.durationMinutes * 60 : 0,
            pausedSeconds: 0,
            status,
            focusLockUsed: false,
            interruptionCount: 0,
            customTitle: nodeId == null ? legacy.title : null,
            createdAtEpochMs: now,
            updatedAtEpochMs: now,
          })));
          db.studySessionDao().upsertSegment(segmentToEntity(StudySessionSegment({
            id: `${legacy.id}_segment_0`,
            sessionId: legacy.id,
            nodeId,
            subjectId,
            startedAtEpochMs: startedAt,
            endedAtEpochMs: endedAt,
            focusedSeconds: legacy.completed ? legacy.durationMinutes * 60 : 0,
            order: 0,
          })));
        }
      }
    });
  }

  /** private */
  stableNodeId(pack, legacyId) {
    if (findNode(pack, legacyId) != null) return legacyId;
    if (!legacyId.startsWith('t1_')) return null;
    const indices = legacyId.slice(3).split('_').map(toIntOrNull).filter((x) => x != null);
    if (indices.length < 2) return null;
    let node = pack.subjects[indices[0]]?.nodes?.[indices[1]];
    if (!node) return null;
    for (const index of indices.slice(2)) {
      node = node.children[index];
      if (!node) return null;
    }
    return node.id;
  }

  /** private */
  legacyAvailability(attemptId, plan) {
    const commitments = decodedCommitments(plan);
    return DayOfWeek.entries.flatMap((day) => {
      const legacyDay = day.value % 7;
      const occupied = commitments
        .filter((it) => it.days.has(legacyDay))
        .map((it) => [Math.max(plan.wakeMinute, it.startMinute), Math.min(plan.sleepMinute, it.endMinute)])
        .filter((it) => it[1] > it[0])
        .sort((a, b) => a[0] - b[0])
        .reduce((merged, interval) => {
          const last = merged[merged.length - 1];
          if (last == null || interval[0] > last[1]) merged.push(interval);
          else merged[merged.length - 1] = [last[0], Math.max(last[1], interval[1])];
          return merged;
        }, []);
      const free = [];
      let cursor = plan.wakeMinute;
      for (const [start, end] of occupied) {
        if (start > cursor) free.push([cursor, start]);
        cursor = Math.max(cursor, end);
      }
      if (cursor < plan.sleepMinute) free.push([cursor, plan.sleepMinute]);
      return free.map(([start, end], index) => WeeklyAvailability({
        id: `migrated_${attemptId}_${day.name}_${index}`,
        attemptId,
        dayOfWeek: day,
        startMinuteOfDay: start,
        endMinuteOfDay: end,
      }));
    });
  }
}
MigrationRepository.CURRENT_MIGRATION_VERSION = 3;
