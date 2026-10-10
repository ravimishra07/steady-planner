// Port of core/data/db/TargetNodeOverrideEntity.kt. Table `target_node_override`, PK attemptId+nodeId.
import { dataClass } from '../../domain/kt.js';
import { TargetNodeOverride, TargetNodeState } from '../../domain/TargetSyllabus.js';

export const TargetNodeOverrideEntity = dataClass({
  attemptId: undefined, nodeId: undefined, state: undefined, updatedAtEpochMs: undefined,
}, {
  toDomain() {
    return TargetNodeOverride({
      attemptId: this.attemptId,
      nodeId: this.nodeId,
      state: TargetNodeState.valueOf(this.state),
      updatedAtEpochMs: this.updatedAtEpochMs,
    });
  },
});
TargetNodeOverrideEntity.tableName = 'target_node_override';
TargetNodeOverrideEntity.primaryKeys = ['attemptId', 'nodeId'];

export const toEntity = (o) => TargetNodeOverrideEntity({
  attemptId: o.attemptId, nodeId: o.nodeId, state: o.state.name, updatedAtEpochMs: o.updatedAtEpochMs,
});

export class TargetNodeOverrideDao {
  constructor(t) { this.t = t; }
  upsert(entity) { this.t.upsert(entity); }
  allOnce(attemptId) { return this.t.rows((r) => r.attemptId === attemptId); }
  delete(attemptId, nodeId) { this.t.deleteWhere((r) => r.attemptId === attemptId && r.nodeId === nodeId); }
  deleteForAttempt(attemptId) { this.t.deleteWhere((r) => r.attemptId === attemptId); }
}
