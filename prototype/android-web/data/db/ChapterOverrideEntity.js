// Port of core/data/db/ChapterOverrideEntity.kt. Table `chapter_override`, PK attemptId+chapterId.
// The student's name for a chapter (§5.2). No row = original name.
import { dataClass } from '../../domain/kt.js';

export const ChapterOverrideEntity = dataClass({ attemptId: undefined, chapterId: undefined, customTitle: null });
ChapterOverrideEntity.tableName = 'chapter_override';
ChapterOverrideEntity.primaryKeys = ['attemptId', 'chapterId'];

export class ChapterOverrideDao {
  constructor(t) { this.t = t; }
  upsert(entity) { this.t.upsert(entity); }
  allOnce(attemptId) { return this.t.rows((r) => r.attemptId === attemptId); }
  delete(attemptId, chapterId) { this.t.deleteWhere((r) => r.attemptId === attemptId && r.chapterId === chapterId); }
  deleteForAttempt(attemptId) { this.t.deleteWhere((r) => r.attemptId === attemptId); }
}
