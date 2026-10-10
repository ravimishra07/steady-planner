// Port of core/data/db/ExamAttemptEntity.kt. Table `exam_attempt`, PK id.
// DAO methods are synchronous here (localStorage is synchronous); Kotlin's are suspend.
import { dataClass } from '../../domain/kt.js';
import { toEpochDay } from '../../domain/time.js';
import { ExamAttempt, ExamAttemptStatus, StartingPoint } from '../../domain/ExamAttempt.js';
import { PlanMode } from '../../domain/PlanMode.js';
import { epochDayToDate } from './Converters.js';

export const ExamAttemptEntity = dataClass({
  id: undefined, examId: undefined, syllabusVersion: undefined, examDateEpochDay: null,
  targetCompletionDateEpochDay: undefined, createdAtEpochMs: undefined, updatedAtEpochMs: undefined,
  status: undefined, planMode: undefined, startingPoint: null,
}, {
  /** ExamAttemptEntity.toDomain() */
  toDomain() {
    return ExamAttempt({
      id: this.id,
      examId: this.examId,
      syllabusVersion: this.syllabusVersion,
      examDate: epochDayToDate(this.examDateEpochDay),
      targetCompletionDate: epochDayToDate(this.targetCompletionDateEpochDay),
      createdAtEpochMs: this.createdAtEpochMs,
      updatedAtEpochMs: this.updatedAtEpochMs,
      status: ExamAttemptStatus.valueOf(this.status),
      planMode: PlanMode.valueOfOrNull(this.planMode) ?? PlanMode.DEADLINE,
      startingPoint: this.startingPoint == null ? null : StartingPoint.valueOfOrNull(this.startingPoint),
    });
  },
});
ExamAttemptEntity.tableName = 'exam_attempt';
ExamAttemptEntity.primaryKeys = ['id'];

/** ExamAttempt.toEntity() */
export const toEntity = (a) => ExamAttemptEntity({
  id: a.id,
  examId: a.examId,
  syllabusVersion: a.syllabusVersion,
  examDateEpochDay: a.examDate == null ? null : toEpochDay(a.examDate),
  targetCompletionDateEpochDay: toEpochDay(a.targetCompletionDate),
  createdAtEpochMs: a.createdAtEpochMs,
  updatedAtEpochMs: a.updatedAtEpochMs,
  status: a.status.name,
  planMode: a.planMode.name,
  startingPoint: a.startingPoint?.name ?? null,
});

export class ExamAttemptDao {
  constructor(t) { this.t = t; }
  upsert(entity) { this.t.upsert(entity); }
  byId(id) { return this.t.first((r) => r.id === id); }
  /** SELECT * FROM exam_attempt WHERE status = 'ACTIVE' LIMIT 1, as a Flow. */
  observeActive() { return this.t.flow((rows) => rows.find((r) => r.status === 'ACTIVE') ?? null); }
  activeOnce() { return this.t.first((r) => r.status === 'ACTIVE'); }
  deleteAttempt(attemptId) { this.t.deleteWhere((r) => r.id === attemptId); }
}
