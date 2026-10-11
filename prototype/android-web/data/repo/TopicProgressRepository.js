// Port of core/data/repo/TopicProgressRepository.kt. suspend -> async; observeAll -> StateFlow.
// What the student has explicitly marked covered — the Syllabus checkmark's one source of truth.
import { TopicProgress, TopicProgressStatus, toggledTopicProgress } from '../../domain/TopicProgress.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { progressToEntity } from '../db/TopicProgressEntity.js';

export class TopicProgressRepository {
  constructor(context, dispatchers) {
    this.db = PrepTrackerDatabase.get(context);
    this.dao = this.db.topicProgressDao();
  }

  /** Flow<List<TopicProgress>> */
  observeAll(attemptId) { return this.dao.observeAll(attemptId).map((list) => list.map((it) => it.toDomain())); }

  async allOnce(attemptId) { return this.dao.allOnce(attemptId).map((it) => it.toDomain()); }
  async byNode(attemptId, nodeId) { return this.dao.byNode(attemptId, nodeId)?.toDomain() ?? null; }
  async upsert(progress) { this.dao.upsert(progressToEntity(progress)); }
  async upsertAll(progress) { this.dao.upsertAll(progress.map(progressToEntity)); }

  /** Replaces onboarding's covered-selection cleanly when setup is redone. */
  async clearAll(attemptId) { this.dao.deleteForAttempt(attemptId); }

  /** The one entry point for "tick this topic" — centralizes the toggle rule (see toggledTopicProgress). */
  async toggle(attemptId, nodeId, nowMs) {
    const current = this.dao.byNode(attemptId, nodeId)?.toDomain() ?? null;
    const updated = toggledTopicProgress(current, attemptId, nodeId, nowMs);
    this.dao.upsert(progressToEntity(updated));
    return updated;
  }

  /** One atomic parent/bulk action; partial writes can never leave a misleading tri-state. */
  async setCovered(attemptId, nodeIds, covered, nowMs) {
    await this.db.withTransaction(() => {
      this.dao.upsertAll([...new Set(nodeIds)].map((nodeId) => progressToEntity(TopicProgress({
        attemptId,
        nodeId,
        status: covered ? TopicProgressStatus.COVERED : TopicProgressStatus.NOT_STARTED,
        coveredAtEpochMs: covered ? nowMs : null,
        updatedAtEpochMs: nowMs,
      }))));
    });
  }
}
