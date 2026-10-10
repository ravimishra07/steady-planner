// Port of core/data/db/ChapterStageEntity.kt. Table `chapter_stage`, PK attemptId+chapterId+stage.
// Depth stages per chapter (§5.1). LEARNED is never a row here: it is the chapter's covered state.
import { dataClass } from '../../domain/kt.js';
import { ChapterStage, ChapterStageMark } from '../../domain/ChapterStage.js';

export const ChapterStageEntity = dataClass({
  attemptId: undefined, chapterId: undefined, stage: undefined, count: 1, updatedAtEpochMs: null,
}, {
  /** ChapterStageEntity.toDomainOrNull(): null for a stage name this version does not know. */
  toDomainOrNull() {
    const known = ChapterStage.entries.find((s) => s.name === this.stage);
    return known ? ChapterStageMark({
      attemptId: this.attemptId, chapterId: this.chapterId, stage: known, count: this.count, updatedAtEpochMs: this.updatedAtEpochMs,
    }) : null;
  },
});
ChapterStageEntity.tableName = 'chapter_stage';
ChapterStageEntity.primaryKeys = ['attemptId', 'chapterId', 'stage'];

export class ChapterStageDao {
  constructor(t) { this.t = t; }
  upsert(entity) { this.t.upsert(entity); }
  allOnce(attemptId) { return this.t.rows((r) => r.attemptId === attemptId); }
  byKey(attemptId, chapterId, stage) {
    return this.t.first((r) => r.attemptId === attemptId && r.chapterId === chapterId && r.stage === stage);
  }
  delete(attemptId, chapterId, stage) {
    this.t.deleteWhere((r) => r.attemptId === attemptId && r.chapterId === chapterId && r.stage === stage);
  }
  deleteForAttempt(attemptId) { this.t.deleteWhere((r) => r.attemptId === attemptId); }
}
