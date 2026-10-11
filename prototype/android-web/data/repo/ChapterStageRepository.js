// Port of core/data/repo/ChapterStageRepository.kt. suspend -> async.
// Per-chapter tracking beyond Learned: depth stages (§5.1) and coaching counts + batch position (§5.4).
import { coerceAtLeast, coerceIn } from '../../domain/kt.js';
import { ChapterStage } from '../../domain/ChapterStage.js';
import { MAX_COACHING_COUNT } from '../../domain/CoachingBacklog.js';
import { Analytics, AnalyticsEvent, AnalyticsParam, mapOf } from '../common.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { ChapterStageEntity } from '../db/ChapterStageEntity.js';
import { ChapterCoachingEntity, BatchPositionEntity } from '../db/CoachingEntity.js';

export class ChapterStageRepository {
  constructor(context, dispatchers, analytics = Analytics.NoOp) {
    this.analytics = analytics;
    this.db = PrepTrackerDatabase.get(context);
    this.dao = this.db.chapterStageDao();
  }

  async marksFor(attemptId) {
    return this.dao.allOnce(attemptId).map((it) => it.toDomainOrNull()).filter((x) => x != null);
  }

  /** Focus revision counters by node, the base of each chapter's "Revised xN". Map<nodeId, RevisionState>. */
  async revisionStatesFor(attemptId) {
    return new Map(this.db.revisionStateDao().allEnabled(attemptId).map((it) => [it.nodeId, it.toDomain()]));
  }

  /** One-tap tick: present -> removed, absent -> added. */
  async toggle(attemptId, chapterId, stage, nowMs) {
    await this.db.withTransaction(() => {
      if (this.dao.byKey(attemptId, chapterId, stage.name) != null) {
        this.dao.delete(attemptId, chapterId, stage.name);
      } else {
        this.dao.upsert(ChapterStageEntity({ attemptId, chapterId, stage: stage.name, count: 1, updatedAtEpochMs: nowMs }));
        if (stage === ChapterStage.LEARNED) this.analytics.log(AnalyticsEvent.CHAPTER_LEARNED);
      }
    });
  }

  /** Adds [delta] to a counted stage (Revised). Never below zero; zero removes the row. */
  async adjustCount(attemptId, chapterId, stage, delta, nowMs) {
    await this.db.withTransaction(() => {
      const next = coerceAtLeast((this.dao.byKey(attemptId, chapterId, stage.name)?.count ?? 0) + delta, 0);
      if (stage === ChapterStage.REVISED && delta > 0) {
        this.analytics.log(AnalyticsEvent.REVISION_COMPLETED, mapOf([AnalyticsParam.SOURCE, 'manual']));
      }
      if (next === 0) this.dao.delete(attemptId, chapterId, stage.name);
      else this.dao.upsert(ChapterStageEntity({ attemptId, chapterId, stage: stage.name, count: next, updatedAtEpochMs: nowMs }));
    });
  }

  async coachingFor(attemptId) { return this.db.coachingDao().allOnce(attemptId).map((it) => it.toDomain()); }

  async batchPosition(attemptId) { return this.db.coachingDao().positionFor(attemptId)?.toDomain() ?? null; }

  /** Saves one chapter's counts, kept in range. All-zero counts remove the row. */
  async saveCoaching(attemptId, coaching) {
    const clean = coaching.normalized();
    if (clean.isEmpty) {
      this.db.coachingDao().delete(attemptId, clean.chapterId);
    } else {
      this.db.coachingDao().upsert(ChapterCoachingEntity({
        attemptId, chapterId: clean.chapterId, lectures: clean.lectures, dpps: clean.dpps,
        lecturesDone: clean.lecturesDone, dppsDone: clean.dppsDone,
      }));
    }
  }

  /**
   * "Taught in class today": the batch is at [chapterId], lecture [lecture]. A chapter whose
   * lecture count is below that grows to match.
   */
  async setBatchPosition(attemptId, chapterId, lecture, nowMs) {
    const at = coerceIn(lecture, 1, MAX_COACHING_COUNT);
    await this.db.withTransaction(() => {
      const dao = this.db.coachingDao();
      dao.upsertPosition(BatchPositionEntity({ attemptId, chapterId, lecture: at, updatedAtEpochMs: nowMs }));
      const existing = dao.allOnce(attemptId).find((it) => it.chapterId === chapterId) ?? null;
      if (existing == null) {
        dao.upsert(ChapterCoachingEntity({ attemptId, chapterId, lectures: at, dpps: 0, lecturesDone: 0, dppsDone: 0 }));
      } else if (existing.lectures < at) {
        dao.upsert(existing.copy({ lectures: at }));
      }
    });
  }
}
