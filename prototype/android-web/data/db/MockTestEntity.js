// Port of core/data/db/MockTestEntity.kt.
// Tables `mock_test` (PK id, index attemptId) and `mock_mistake` (PK mockId+chapterId).
import { dataClass } from '../../domain/kt.js';
import { toEpochDay } from '../../domain/time.js';
import { MockTest, MockMistake, MockTestType, MistakeType, NEET_MAX_TOTAL } from '../../domain/MockTest.js';
import { epochDayToDate } from './Converters.js';

/** One logged test or mock (PRODUCTION_V1_PLAN §8.1). Subject scores and counts are optional. */
export const MockTestEntity = dataClass({
  id: undefined, attemptId: undefined, dateEpochDay: undefined, name: undefined, type: undefined,
  physics: null, chemistry: null, botany: null, zoology: null, total: undefined, maxTotal: NEET_MAX_TOTAL,
  attempted: null, correct: null, wrong: null,
}, {
  toDomain() {
    return MockTest({
      id: this.id,
      attemptId: this.attemptId,
      date: epochDayToDate(this.dateEpochDay),
      name: this.name,
      type: MockTestType.valueOfOrNull(this.type) ?? MockTestType.FULL_MOCK,
      physics: this.physics,
      chemistry: this.chemistry,
      botany: this.botany,
      zoology: this.zoology,
      total: this.total,
      maxTotal: this.maxTotal,
      attempted: this.attempted,
      correct: this.correct,
      wrong: this.wrong,
    });
  },
});
MockTestEntity.tableName = 'mock_test';
MockTestEntity.primaryKeys = ['id'];

/** A chapter where marks were lost in one mock (§8.2). */
export const MockMistakeEntity = dataClass({ mockId: undefined, chapterId: undefined, type: undefined }, {
  toDomain() {
    return MockMistake({
      mockId: this.mockId,
      chapterId: this.chapterId,
      type: MistakeType.valueOfOrNull(this.type) ?? MistakeType.CONCEPT,
    });
  },
});
MockMistakeEntity.tableName = 'mock_mistake';
MockMistakeEntity.primaryKeys = ['mockId', 'chapterId'];

export const mockToEntity = (m) => MockTestEntity({
  id: m.id, attemptId: m.attemptId, dateEpochDay: toEpochDay(m.date), name: m.name, type: m.type.name,
  physics: m.physics, chemistry: m.chemistry, botany: m.botany, zoology: m.zoology,
  total: m.total, maxTotal: m.maxTotal, attempted: m.attempted, correct: m.correct, wrong: m.wrong,
});
export const mistakeToEntity = (m) => MockMistakeEntity({ mockId: m.mockId, chapterId: m.chapterId, type: m.type.name });
/** Kotlin overloads toEntity on MockTest / MockMistake; dispatch on shape. */
export const toEntity = (x) => ('mockId' in x ? mistakeToEntity(x) : mockToEntity(x));

export class MockTestDao {
  constructor(mocks, mistakes) { this.mocks = mocks; this.mistakes = mistakes; }
  upsert(entity) { this.mocks.upsert(entity); }
  byId(id) { return this.mocks.first((r) => r.id === id); }
  /** ORDER BY dateEpochDay DESC (stable on ties, like SQLite's rowid order). */
  allFor(attemptId) {
    return this.mocks.rows((r) => r.attemptId === attemptId).sort((a, b) => b.dateEpochDay - a.dateEpochDay);
  }
  mistakesFor(mockId) { return this.mistakes.rows((r) => r.mockId === mockId); }
  mistakesForAttempt(attemptId) {
    const ids = new Set(this.mocks.rawRows((r) => r.attemptId === attemptId).map((r) => r.id));
    return this.mistakes.rows((r) => ids.has(r.mockId));
  }
  upsertMistakes(entities) { this.mistakes.upsertAll(entities); }
  deleteMistakesFor(mockId) { this.mistakes.deleteWhere((r) => r.mockId === mockId); }
  deleteById(id) { this.mocks.deleteWhere((r) => r.id === id); }
  /** @Transaction */
  deleteMock(id) { this.deleteMistakesFor(id); this.deleteById(id); }
  deleteMistakesForAttempt(attemptId) {
    const ids = new Set(this.mocks.rawRows((r) => r.attemptId === attemptId).map((r) => r.id));
    this.mistakes.deleteWhere((r) => ids.has(r.mockId));
  }
  deleteMocksForAttempt(attemptId) { this.mocks.deleteWhere((r) => r.attemptId === attemptId); }
  /** @Transaction */
  deleteAllForAttempt(attemptId) { this.deleteMistakesForAttempt(attemptId); this.deleteMocksForAttempt(attemptId); }
}
