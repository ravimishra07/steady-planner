// Port of core/data/db/PrepTrackerDatabase.kt (Room database "prep_tracker.db", version 11).
//
// Stubbed / changed:
//  - Room/SQLite -> platform/store.js `table(<Room table name>)`, one per @Entity. Rows are the
//    entity's columns exactly (epoch days, epoch ms, enum names, booleans). Tables whose primary key
//    is not a single `id` column get a synthetic `id` = the key columns joined (stripped on read).
//  - DAO methods are synchronous (localStorage is); repositories wrap them in async funs like Kotlin.
//  - withTransaction(block): snapshots every table and restores it if block throws, so a failed
//    transaction leaves nothing behind, like Room. Not isolated from concurrent async writers.
//  - Migrations 1..11: data starts fresh at version 11 here, so each MIGRATION_x_y is a documented
//    no-op object with the same name and version pair. The SQL is kept as a string for reference.
import { table } from '../../platform/store.js';
import { DEFAULT_NEET_EXAM_DATE } from '../../domain/ExamDates.js';
import { parseDate, toEpochDay } from '../../domain/time.js';
import { ExamAttemptEntity, ExamAttemptDao } from './ExamAttemptEntity.js';
import { StudyPlanBlockEntity, StudyPlanBlockDao } from './StudyPlanBlockEntity.js';
import { StudySessionEntity, StudySessionSegmentEntity, StudySessionDao } from './StudySessionEntity.js';
import { TopicProgressEntity, RevisionStateEntity, TopicProgressDao, RevisionStateDao } from './TopicProgressEntity.js';
import { TargetNodeOverrideEntity, TargetNodeOverrideDao } from './TargetNodeOverrideEntity.js';
import { WeeklyAvailabilityEntity, AvailabilityOverrideEntity, AvailabilityDao } from './AvailabilityEntity.js';
import {
  StudyPreferencesEntity, SubjectStudyPreferenceEntity, SubjectPreferredWindowEntity, StudyPreferenceDao,
} from './StudyPreferenceEntity.js';
import { ChapterStageEntity, ChapterStageDao } from './ChapterStageEntity.js';
import { ChapterOverrideEntity, ChapterOverrideDao } from './ChapterOverrideEntity.js';
import { ChapterCoachingEntity, BatchPositionEntity, CoachingDao } from './CoachingEntity.js';
import { MockTestEntity, MockMistakeEntity, MockTestDao } from './MockTestEntity.js';

export const DB_NAME = 'prep_tracker.db';
export const DB_VERSION = 11;

/** @Database(entities = [...]) in declaration order. */
export const ENTITIES = [
  ExamAttemptEntity,
  StudyPlanBlockEntity,
  StudySessionEntity,
  StudySessionSegmentEntity,
  TopicProgressEntity,
  RevisionStateEntity,
  TargetNodeOverrideEntity,
  WeeklyAvailabilityEntity,
  AvailabilityOverrideEntity,
  StudyPreferencesEntity,
  SubjectStudyPreferenceEntity,
  SubjectPreferredWindowEntity,
  ChapterStageEntity,
  ChapterOverrideEntity,
  ChapterCoachingEntity,
  BatchPositionEntity,
  MockTestEntity,
  MockMistakeEntity,
];

/** Column names of an entity, in declaration order (Room's column order). */
export const columnsOf = (entity) => Object.keys(entity({}));

/**
 * Typed view over one Room table. Every read returns entity objects (dataClass) of [entity].
 *   rows(pred) / rawRows(pred) / first(pred) / upsert(e) / upsertAll(es) / deleteWhere(pred)
 *   flow(mapRows)   StateFlow of mapRows(entities[])
 * Predicates receive the stored row (entity columns), as a WHERE clause would.
 */
export function entityStore(entity) {
  const t = table(entity.tableName);
  const pk = entity.primaryKeys;
  const synthetic = !(pk.length === 1 && pk[0] === 'id');
  const keyOf = (e) => (pk.length === 1 ? String(e[pk[0]]) : JSON.stringify(pk.map((k) => e[k])));
  const toRow = (e) => (synthetic ? { ...e, id: keyOf(e) } : { ...e });
  const strip = (r) => { if (synthetic) { const o = { ...r }; delete o.id; return o; } return r; };
  const fromRow = (r) => (r == null ? null : entity(strip(r)));
  return {
    entity,
    table: t,
    rawRows(pred = () => true) { return t.query((r) => pred(r)).map(strip); },
    rows(pred = () => true) { return t.query((r) => pred(r)).map(fromRow); },
    first(pred) { const r = t.query((x) => pred(x))[0]; return fromRow(r); },
    upsert(e) { t.insert(toRow(e)); },
    upsertAll(es) { if (es.length) t.insertAll(es.map(toRow)); },
    deleteWhere(pred) { return t.deleteWhere((r) => pred(r)); },
    flow(mapRows = (x) => x) { return t.flow().map((rows) => mapRows(rows.map(fromRow))); },
    clear() { t.clear(); },
    /** Raw rows incl. synthetic id, for transaction snapshots. */
    snapshot() { return t.all(); },
    restore(rows) { t.clear(); if (rows.length) t.insertAll(rows); },
  };
}

export class PrepTrackerDatabase {
  constructor() {
    this.stores = new Map(ENTITIES.map((e) => [e.tableName, entityStore(e)]));
    const s = (e) => this.stores.get(e.tableName);
    this._examAttemptDao = new ExamAttemptDao(s(ExamAttemptEntity));
    this._studyPlanBlockDao = new StudyPlanBlockDao(s(StudyPlanBlockEntity));
    this._studySessionDao = new StudySessionDao(s(StudySessionEntity), s(StudySessionSegmentEntity));
    this._topicProgressDao = new TopicProgressDao(s(TopicProgressEntity));
    this._revisionStateDao = new RevisionStateDao(s(RevisionStateEntity));
    this._targetNodeOverrideDao = new TargetNodeOverrideDao(s(TargetNodeOverrideEntity));
    this._availabilityDao = new AvailabilityDao(s(WeeklyAvailabilityEntity), s(AvailabilityOverrideEntity));
    this._studyPreferenceDao = new StudyPreferenceDao(
      s(StudyPreferencesEntity), s(SubjectStudyPreferenceEntity), s(SubjectPreferredWindowEntity));
    this._chapterStageDao = new ChapterStageDao(s(ChapterStageEntity));
    this._chapterOverrideDao = new ChapterOverrideDao(s(ChapterOverrideEntity));
    this._coachingDao = new CoachingDao(s(ChapterCoachingEntity), s(BatchPositionEntity));
    this._mockTestDao = new MockTestDao(s(MockTestEntity), s(MockMistakeEntity));
    this._txDepth = 0;
  }

  examAttemptDao() { return this._examAttemptDao; }
  studyPlanBlockDao() { return this._studyPlanBlockDao; }
  studySessionDao() { return this._studySessionDao; }
  topicProgressDao() { return this._topicProgressDao; }
  revisionStateDao() { return this._revisionStateDao; }
  targetNodeOverrideDao() { return this._targetNodeOverrideDao; }
  availabilityDao() { return this._availabilityDao; }
  studyPreferenceDao() { return this._studyPreferenceDao; }
  chapterStageDao() { return this._chapterStageDao; }
  chapterOverrideDao() { return this._chapterOverrideDao; }
  coachingDao() { return this._coachingDao; }
  mockTestDao() { return this._mockTestDao; }

  /** Room's version (SupportSQLiteDatabase.version). */
  get version() { return DB_VERSION; }

  /** Table names like `SELECT name FROM sqlite_master ... ORDER BY name`. */
  userTables() { return [...this.stores.keys()].sort(); }

  /**
   * androidx.room.withTransaction. Re-entrant like Room's: a nested call joins the outer one.
   * On throw, every table is restored to its state at the outermost start and the error rethrown.
   */
  async withTransaction(block) {
    if (this._txDepth > 0) return block();
    const snap = new Map([...this.stores].map(([n, st]) => [n, st.snapshot()]));
    this._txDepth++;
    try {
      return await block();
    } catch (e) {
      for (const [n, rows] of snap) this.stores.get(n).restore(rows);
      throw e;
    } finally {
      this._txDepth--;
    }
  }

  static get(_context) {
    if (!PrepTrackerDatabase.instance) PrepTrackerDatabase.instance = new PrepTrackerDatabase();
    return PrepTrackerDatabase.instance;
  }
}
PrepTrackerDatabase.instance = null;

/** Migration(start, end) stand-in: data starts fresh at v11, so migrate() is never run. */
const migration = (startVersion, endVersion, sql) => Object.freeze({
  startVersion, endVersion, sql, migrate(_db) { /* no-op: no pre-v11 data exists in the web clone */ },
});

/** The compile-time default exam date as an epoch day. */
const EXPECTED_NEET_2027_EPOCH_DAY = toEpochDay(parseDate(DEFAULT_NEET_EXAM_DATE));

export const MIGRATION_1_2 = migration(1, 2, [
  'ALTER TABLE study_preferences ADD COLUMN weekdayTargetMinutes INTEGER NOT NULL DEFAULT 240',
  'ALTER TABLE study_preferences ADD COLUMN weekendTargetMinutes INTEGER NOT NULL DEFAULT 420',
]);
export const MIGRATION_2_3 = migration(2, 3, ['ALTER TABLE study_session ADD COLUMN outcome TEXT']);
export const MIGRATION_3_4 = migration(3, 4, ["ALTER TABLE study_preferences ADD COLUMN planningOrder TEXT NOT NULL DEFAULT 'DEFAULT'"]);
export const MIGRATION_4_5 = migration(4, 5, [
  'ALTER TABLE study_session ADD COLUMN dueDateAtCompletionEpochDay INTEGER',
  'ALTER TABLE study_session ADD COLUMN priorIntervalDays INTEGER',
]);
export const MIGRATION_5_6 = migration(5, 6, [
  "ALTER TABLE exam_attempt ADD COLUMN planMode TEXT NOT NULL DEFAULT 'DEADLINE'",
  'ALTER TABLE study_preferences ADD COLUMN allowLearnInRevisionPhase INTEGER NOT NULL DEFAULT 0',
  `UPDATE exam_attempt SET examDateEpochDay = CASE WHEN targetCompletionDateEpochDay > ${EXPECTED_NEET_2027_EPOCH_DAY} ` +
    `THEN targetCompletionDateEpochDay ELSE ${EXPECTED_NEET_2027_EPOCH_DAY} END ` +
    'WHERE examDateEpochDay IS NULL OR examDateEpochDay = targetCompletionDateEpochDay',
]);
export const MIGRATION_6_7 = migration(6, 7, ['ALTER TABLE exam_attempt ADD COLUMN startingPoint TEXT']);
export const MIGRATION_7_8 = migration(7, 8, ['CREATE TABLE IF NOT EXISTS `chapter_stage` (...)']);
export const MIGRATION_8_9 = migration(8, 9, ['CREATE TABLE IF NOT EXISTS `chapter_override` (...)']);
export const MIGRATION_9_10 = migration(9, 10, [
  'CREATE TABLE IF NOT EXISTS `chapter_coaching` (...)',
  'CREATE TABLE IF NOT EXISTS `batch_position` (...)',
]);
export const MIGRATION_10_11 = migration(10, 11, [
  'CREATE TABLE IF NOT EXISTS `mock_test` (...)',
  'CREATE INDEX IF NOT EXISTS `index_mock_test_attemptId` ON `mock_test` (`attemptId`)',
  'CREATE TABLE IF NOT EXISTS `mock_mistake` (...)',
  'ALTER TABLE study_session ADD COLUMN isManual INTEGER NOT NULL DEFAULT 0',
]);
