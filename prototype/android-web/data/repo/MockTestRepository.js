// Port of core/data/repo/MockTestRepository.kt. suspend -> async.
// Mock and test log with its mistake tags (PRODUCTION_V1_PLAN §8.1, §8.2).
import { newlyTaggedChapters, revisionStateAfterMistake } from '../../domain/MockTest.js';
import { Analytics, AnalyticsEvent, AnalyticsParam, mapOf } from '../common.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { mockToEntity, mistakeToEntity } from '../db/MockTestEntity.js';
import { revisionToEntity } from '../db/TopicProgressEntity.js';

export class MockTestRepository {
  constructor(context, dispatchers, analytics = Analytics.NoOp) {
    this.analytics = analytics;
    this.db = PrepTrackerDatabase.get(context);
    this.dao = this.db.mockTestDao();
  }

  async allFor(attemptId) { return this.dao.allFor(attemptId).map((it) => it.toDomain()); }
  async byId(id) { return this.dao.byId(id)?.toDomain() ?? null; }
  async mistakesFor(mockId) { return this.dao.mistakesFor(mockId).map((it) => it.toDomain()); }
  async mistakesForAttempt(attemptId) { return this.dao.mistakesForAttempt(attemptId).map((it) => it.toDomain()); }

  /**
   * Saves a mock and replaces its mistake tags in one transaction. Each chapter tagged for the
   * first time in this mock gets a Struggled outcome on its revision schedule.
   */
  async save(mock, mistakes, nowMs) {
    const isEdit = this.dao.byId(mock.id) != null;
    await this.db.withTransaction(() => {
      const before = this.dao.mistakesFor(mock.id).map((it) => it.toDomain());
      this.dao.upsert(mockToEntity(mock));
      this.dao.deleteMistakesFor(mock.id);
      this.dao.upsertMistakes(mistakes.map((it) => mistakeToEntity(it.copy({ mockId: mock.id }))));
      const revisionDao = this.db.revisionStateDao();
      for (const chapterId of newlyTaggedChapters(before, mistakes)) {
        const current = revisionDao.byNode(mock.attemptId, chapterId)?.toDomain() ?? null;
        revisionDao.upsert(revisionToEntity(revisionStateAfterMistake(current, mock.attemptId, chapterId, mock.date, nowMs)));
      }
    });
    this.analytics.log(AnalyticsEvent.MOCK_LOGGED, mapOf(
      [AnalyticsParam.MOCK_TYPE, mock.type.name],
      [AnalyticsParam.IS_EDIT, isEdit],
      [AnalyticsParam.COUNT, mistakes.length],
    ));
  }

  /** Deletes a mock and its tags. A revision already brought forward stays where it is. */
  async delete(mockId) { this.dao.deleteMock(mockId); }
}
