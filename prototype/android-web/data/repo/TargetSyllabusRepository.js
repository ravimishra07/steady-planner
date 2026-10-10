// Port of core/data/repo/TargetSyllabusRepository.kt. suspend -> async.
// "I am intentionally not preparing this" — never deletes static syllabus data, only overrides target membership.
import { TargetNodeOverride } from '../../domain/TargetSyllabus.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { toEntity } from '../db/TargetNodeOverrideEntity.js';

export class TargetSyllabusRepository {
  constructor(context, dispatchers) {
    this.db = PrepTrackerDatabase.get(context);
    this.dao = this.db.targetNodeOverrideDao();
  }

  async overridesFor(attemptId) { return this.dao.allOnce(attemptId).map((it) => it.toDomain()); }

  async setState(attemptId, nodeId, state, nowMs) {
    this.dao.upsert(toEntity(TargetNodeOverride({ attemptId, nodeId, state, updatedAtEpochMs: nowMs })));
  }

  async clearOverride(attemptId, nodeId) { this.dao.delete(attemptId, nodeId); }

  /** states: Map<nodeId, TargetNodeState> */
  async replaceStates(attemptId, states, nowMs) {
    await this.db.withTransaction(() => {
      this.dao.deleteForAttempt(attemptId);
      for (const [nodeId, state] of states) {
        this.dao.upsert(toEntity(TargetNodeOverride({ attemptId, nodeId, state, updatedAtEpochMs: nowMs })));
      }
    });
  }
}
