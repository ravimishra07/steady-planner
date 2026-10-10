// Port of core/data/repo/StudySessionRepository.kt. suspend -> async.
// The actual-history writer. StudyPlanBlock is intent; this is truth. Owns the transactional
// "complete a Focus session" flow: close session+segment, mark the linked plan block completed,
// update topic progress and revision state — one atomic write (withTransaction rolls back on throw).
import { idiv } from '../../domain/kt.js';
import { toEpochDay, ofEpochDay } from '../../domain/time.js';
import { StudySessionSegment, StudySessionStatus } from '../../domain/StudyHistory.js';
import { PlanBlockStatus } from '../../domain/StudyPlan.js';
import { completeStudySession, recordStudyOutcome } from '../../domain/RevisionSchedule.js';
import { topicProgressAfterStudy } from '../../domain/TopicProgress.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { sessionToEntity, segmentToEntity } from '../db/StudySessionEntity.js';
import { progressToEntity, revisionToEntity } from '../db/TopicProgressEntity.js';

export class StudySessionRepository {
  constructor(context, dispatchers) {
    this.db = PrepTrackerDatabase.get(context);
    this.sessionDao = this.db.studySessionDao();
  }

  async byId(id) { return this.sessionDao.byId(id)?.toDomain() ?? null; }
  async activeSession(attemptId) { return this.sessionDao.activeSession(attemptId)?.toDomain() ?? null; }
  async forDate(attemptId, date) { return this.sessionDao.forDate(attemptId, toEpochDay(date)).map((it) => it.toDomain()); }
  async between(attemptId, start, end) {
    return this.sessionDao.between(attemptId, toEpochDay(start), toEpochDay(end)).map((it) => it.toDomain());
  }
  async forNode(attemptId, nodeId) { return this.sessionDao.forNode(attemptId, nodeId).map((it) => it.toDomain()); }
  async segmentsFor(sessionId) { return this.sessionDao.segmentsFor(sessionId).map((it) => it.toDomain()); }

  /** Map<LocalDate, [totalSeconds, sessionCount]> */
  async dailyFocusedSeconds(attemptId, start, end) {
    return new Map(this.sessionDao.dailySums(attemptId, toEpochDay(start), toEpochDay(end))
      .map((it) => [ofEpochDay(it.studyDateEpochDay), [it.totalSeconds, it.sessionCount]]));
  }

  /** Map<subjectId|null, totalSeconds> */
  async subjectFocusedSeconds(attemptId, start, end) {
    return new Map(this.sessionDao.subjectSums(attemptId, toEpochDay(start), toEpochDay(end))
      .map((it) => [it.subjectId, it.totalSeconds]));
  }

  /** Map<nodeId, minutes> (integer division like Kotlin). */
  async completedLearnMinutesByNode(attemptId) {
    return new Map(this.sessionDao.completedLearnSumsByNode(attemptId).map((it) => [it.nodeId, idiv(it.totalSeconds, 60)]));
  }

  async earliestStudyDate(attemptId) {
    const d = this.sessionDao.earliestStudyDate(attemptId);
    return d == null ? null : ofEpochDay(d);
  }

  /** Starts a session, planned (linked to planBlockId) or unplanned (null). Opens segment 0. */
  async startSession(session, firstSegment) {
    await this.db.withTransaction(() => {
      this.sessionDao.upsert(sessionToEntity(session));
      this.sessionDao.upsertSegment(segmentToEntity(firstSegment));
    });
    return session;
  }

  /** Persists a transition or coarse checkpoint while a session is running/paused. */
  async updateRunning(session) { this.sessionDao.upsert(sessionToEntity(session)); }

  /** Closes the current open segment and opens a new one on nextSegment. Unused until multi-topic UI. */
  async switchSegment(sessionId, closedFocusedSeconds, nowMs, nextSegment) {
    await this.db.withTransaction(() => {
      const open = this.sessionDao.openSegment(sessionId);
      if (open != null) this.sessionDao.upsertSegment(open.copy({ endedAtEpochMs: nowMs, focusedSeconds: closedFocusedSeconds }));
      this.sessionDao.upsertSegment(segmentToEntity(nextSegment));
    });
  }

  /**
   * The one Focus-completion transaction: close session + open segment, mark the linked plan block
   * completed (if any), advance topic progress and revision state.
   */
  async completeSession(session, today, nowMs) {
    const nodeId = session.nodeId;
    let stored;
    await this.db.withTransaction(() => {
      const revisionDao = this.db.revisionStateDao();
      const currentRevision = nodeId != null ? (revisionDao.byNode(session.attemptId, nodeId)?.toDomain() ?? null) : null;
      const result = completeStudySession(session, currentRevision, today, nowMs);
      stored = result.session;
      this.sessionDao.upsert(sessionToEntity(stored));
      const open = this.sessionDao.openSegment(session.id);
      if (open) this.sessionDao.upsertSegment(open.copy({ endedAtEpochMs: nowMs, focusedSeconds: stored.focusedSeconds }));
      if (session.planBlockId != null) {
        const block = this.db.studyPlanBlockDao().byId(session.planBlockId);
        if (block) this.db.studyPlanBlockDao().upsert(block.copy({ status: PlanBlockStatus.COMPLETED.name, updatedAtEpochMs: nowMs }));
      }
      if (nodeId != null) {
        const progressDao = this.db.topicProgressDao();
        const current = progressDao.byNode(session.attemptId, nodeId)?.toDomain() ?? null;
        progressDao.upsert(progressToEntity(topicProgressAfterStudy(current, session.attemptId, nodeId, nowMs)));
        if (result.revision != null) revisionDao.upsert(revisionToEntity(result.revision));
      }
    });
    return stored;
  }

  /**
   * Saves useful time when the student ends early. The history is closed and the topic becomes
   * in-progress, but the planned block and revision cycle stay untouched.
   */
  async completePartialSession(session, nowMs) {
    const completed = session.copy({ status: StudySessionStatus.COMPLETED, endedAtEpochMs: nowMs, updatedAtEpochMs: nowMs });
    await this.db.withTransaction(() => {
      this.sessionDao.upsert(sessionToEntity(completed));
      const open = this.sessionDao.openSegment(session.id);
      if (open) this.sessionDao.upsertSegment(open.copy({ endedAtEpochMs: nowMs, focusedSeconds: completed.focusedSeconds }));
      const nodeId = session.nodeId;
      if (nodeId != null) {
        const progressDao = this.db.topicProgressDao();
        const current = progressDao.byNode(session.attemptId, nodeId)?.toDomain() ?? null;
        progressDao.upsert(progressToEntity(topicProgressAfterStudy(current, session.attemptId, nodeId, nowMs)));
      }
    });
    return completed;
  }

  async abandonSession(session, nowMs) {
    await this.db.withTransaction(() => {
      this.sessionDao.upsert(sessionToEntity(session.copy({
        status: StudySessionStatus.ABANDONED, endedAtEpochMs: nowMs, updatedAtEpochMs: nowMs,
      })));
      const open = this.sessionDao.openSegment(session.id);
      if (open) this.sessionDao.upsertSegment(open.copy({ endedAtEpochMs: nowMs, focusedSeconds: 0 }));
    });
  }

  /**
   * Stores the student's rating. The revision schedule is recomputed from the interval recorded when
   * the session completed, so the rating replaces the provisional schedule.
   */
  async recordOutcome(sessionId, outcome, nowMs) {
    await this.db.withTransaction(() => {
      const session = this.sessionDao.byId(sessionId)?.toDomain();
      if (!session) return;
      const revisionDao = this.db.revisionStateDao();
      const current = session.nodeId != null ? (revisionDao.byNode(session.attemptId, session.nodeId)?.toDomain() ?? null) : null;
      const result = recordStudyOutcome(session, current, outcome, nowMs);
      this.sessionDao.upsert(sessionToEntity(result.session));
      if (result.revision != null) revisionDao.upsert(revisionToEntity(result.revision));
    });
  }

  /**
   * "Log study I did offline" (§6.3): stores a session that is already COMPLETED, through the same
   * completion rules as Focus, dated on the day the student studied.
   */
  async logManualSession(session, nowMs) {
    const nodeId = session.nodeId;
    let stored;
    await this.db.withTransaction(() => {
      const revisionDao = this.db.revisionStateDao();
      const currentRevision = nodeId != null ? (revisionDao.byNode(session.attemptId, nodeId)?.toDomain() ?? null) : null;
      const result = completeStudySession(session, currentRevision, session.studyDate, nowMs);
      stored = result.session.copy({ endedAtEpochMs: session.endedAtEpochMs });
      this.sessionDao.upsert(sessionToEntity(stored));
      this.sessionDao.upsertSegment(segmentToEntity(this.manualSegment(stored)));
      if (nodeId != null) {
        const progressDao = this.db.topicProgressDao();
        const current = progressDao.byNode(session.attemptId, nodeId)?.toDomain() ?? null;
        progressDao.upsert(progressToEntity(topicProgressAfterStudy(current, session.attemptId, nodeId, nowMs)));
        if (result.revision != null) revisionDao.upsert(revisionToEntity(result.revision));
      }
    });
    return stored;
  }

  /** Saves an edited past session (§6.3). Only the record changes; the revision schedule is left alone. */
  async updateSession(session) {
    await this.db.withTransaction(() => {
      this.sessionDao.upsert(sessionToEntity(session));
      const segments = this.sessionDao.segmentsFor(session.id);
      if (segments.length <= 1) {
        this.sessionDao.deleteSegmentsFor(session.id);
        this.sessionDao.upsertSegment(segmentToEntity(this.manualSegment(session, segments[0]?.id ?? null)));
      }
    });
  }

  /** Deletes a past session and its segments. The plan block it completed keeps its status. */
  async deleteSession(sessionId) { this.sessionDao.deleteSession(sessionId); }

  /** private */
  manualSegment(session, id = null) {
    return StudySessionSegment({
      id: id ?? `${session.id}_0`,
      sessionId: session.id,
      nodeId: session.nodeId,
      subjectId: session.subjectId,
      startedAtEpochMs: session.startedAtEpochMs,
      endedAtEpochMs: session.endedAtEpochMs,
      focusedSeconds: session.focusedSeconds,
      order: 0,
    });
  }
}
