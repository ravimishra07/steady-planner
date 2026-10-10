// Port of core/data/repo/RevisionRepository.kt. suspend -> async.
// Answers "when is this topic due again?" — actual review history lives in StudySessionRepository.
import { toEpochDay } from '../../domain/time.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';

export class RevisionRepository {
  constructor(context, dispatchers) { this.dao = PrepTrackerDatabase.get(context).revisionStateDao(); }

  async byNode(attemptId, nodeId) { return this.dao.byNode(attemptId, nodeId)?.toDomain() ?? null; }

  /** Every enabled schedule for the attempt: the one input to every due/overdue/week count. */
  async allFor(attemptId) { return this.dao.allEnabled(attemptId).map((it) => it.toDomain()); }

  async dueBy(attemptId, date) { return this.dao.dueBy(attemptId, toEpochDay(date)).map((it) => it.toDomain()); }
}
