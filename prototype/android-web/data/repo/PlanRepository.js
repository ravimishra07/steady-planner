// Port of core/data/repo/PlanRepository.kt. suspend -> async; observePlanForDate -> StateFlow.
import { toEpochDay } from '../../domain/time.js';
import { PlanBlockStatus, rescheduleBlock } from '../../domain/StudyPlan.js';
import { Analytics, AnalyticsEvent, AnalyticsParam, mapOf } from '../common.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { toEntity } from '../db/StudyPlanBlockEntity.js';

export class PlanRepository {
  constructor(context, dispatchers, analytics = Analytics.NoOp) {
    this.analytics = analytics;
    this.db = PrepTrackerDatabase.get(context);
    this.dao = this.db.studyPlanBlockDao();
  }

  /** Flow<List<StudyPlanBlock>> */
  observePlanForDate(attemptId, date) {
    return this.dao.observeForDate(attemptId, toEpochDay(date)).map((list) => list.map((it) => it.toDomain()));
  }

  async planForDate(attemptId, date) { return this.dao.forDateOnce(attemptId, toEpochDay(date)).map((it) => it.toDomain()); }

  async planBetween(attemptId, start, end) {
    return this.dao.between(attemptId, toEpochDay(start), toEpochDay(end)).map((it) => it.toDomain());
  }

  async upsert(block) { this.dao.upsert(toEntity(block)); }

  async upsertAll(blocks) { this.dao.upsertAll(blocks.map(toEntity)); }

  /** Onboarding owns the initial automatic plan and replaces that intent when setup is redone. */
  async replaceAllForAttempt(attemptId, blocks) {
    await this.db.withTransaction(() => {
      this.dao.deleteForAttempt(attemptId);
      this.dao.upsertAll(blocks.map(toEntity));
    });
  }

  /** Replans only future automatic intent; completed, skipped, rescheduled and manual history remains immutable. */
  async replaceFutureAuto(attemptId, start, blocks) {
    await this.db.withTransaction(() => {
      this.dao.deleteReplaceable(attemptId, toEpochDay(start), 0);
      this.dao.upsertAll(blocks.map(toEntity));
    });
  }

  async byId(id) { return this.dao.byId(id)?.toDomain() ?? null; }

  async skip(id, nowMs) {
    const block = this.dao.byId(id)?.toDomain();
    if (block) {
      this.dao.upsert(toEntity(block.copy({ status: PlanBlockStatus.SKIPPED, updatedAtEpochMs: nowMs })));
      this.analytics.log(AnalyticsEvent.BLOCK_DROPPED, mapOf([AnalyticsParam.ACTIVITY, block.activityType.name]));
    }
  }

  /**
   * Reschedule preserves history: the original block becomes RESCHEDULED and links to its
   * replacement; the replacement links back. Returns the replacement.
   */
  async reschedule(original, newDate, newStartMinuteOfDay, newBlockId, nowMs) {
    const [closedOriginal, replacement] = rescheduleBlock(original, newDate, newStartMinuteOfDay, newBlockId, nowMs);
    await this.db.withTransaction(() => {
      this.dao.upsert(toEntity(closedOriginal));
      this.dao.upsert(toEntity(replacement));
    });
    this.analytics.log(AnalyticsEvent.BLOCK_MOVED, mapOf([AnalyticsParam.ACTIVITY, original.activityType.name]));
    return replacement;
  }
}
