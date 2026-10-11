// Port of core/data/db/CoachingEntity.kt.
// Tables `chapter_coaching` (PK attemptId+chapterId) and `batch_position` (PK attemptId).
import { dataClass } from '../../domain/kt.js';
import { ChapterCoaching, BatchPosition } from '../../domain/CoachingBacklog.js';

/** Coaching lecture and DPP counts per chapter (§5.4). Done = highest ticked index. */
export const ChapterCoachingEntity = dataClass({
  attemptId: undefined, chapterId: undefined, lectures: undefined, dpps: undefined, lecturesDone: undefined, dppsDone: undefined,
}, {
  toDomain() {
    return ChapterCoaching({
      chapterId: this.chapterId, lectures: this.lectures, dpps: this.dpps,
      lecturesDone: this.lecturesDone, dppsDone: this.dppsDone,
    });
  },
});
ChapterCoachingEntity.tableName = 'chapter_coaching';
ChapterCoachingEntity.primaryKeys = ['attemptId', 'chapterId'];

/** Where the student's coaching batch is now: one row per attempt (§5.4). */
export const BatchPositionEntity = dataClass({
  attemptId: undefined, chapterId: undefined, lecture: undefined, updatedAtEpochMs: undefined,
}, {
  toDomain() {
    return BatchPosition({ chapterId: this.chapterId, lecture: this.lecture, updatedAtEpochMs: this.updatedAtEpochMs });
  },
});
BatchPositionEntity.tableName = 'batch_position';
BatchPositionEntity.primaryKeys = ['attemptId'];

export class CoachingDao {
  constructor(coaching, positions) { this.coaching = coaching; this.positions = positions; }
  upsert(entity) { this.coaching.upsert(entity); }
  allOnce(attemptId) { return this.coaching.rows((r) => r.attemptId === attemptId); }
  delete(attemptId, chapterId) { this.coaching.deleteWhere((r) => r.attemptId === attemptId && r.chapterId === chapterId); }
  deleteForAttempt(attemptId) { this.coaching.deleteWhere((r) => r.attemptId === attemptId); }
  upsertPosition(entity) { this.positions.upsert(entity); }
  positionFor(attemptId) { return this.positions.first((r) => r.attemptId === attemptId); }
  deletePositionForAttempt(attemptId) { this.positions.deleteWhere((r) => r.attemptId === attemptId); }
}
