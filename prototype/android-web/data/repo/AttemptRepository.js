// Port of core/data/repo/AttemptRepository.kt. All suspend funs -> async; observeActiveAttempt -> StateFlow.
// DataStore "attempt_flag" -> prefs('attempt_flag'), key has_attempt.
import { prefs } from '../../platform/store.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { toEntity } from '../db/ExamAttemptEntity.js';

const attemptFlagDataStore = () => prefs('attempt_flag');
const hasAttemptKey = 'has_attempt';

/** The exam attempt is the central object everything else hangs off. */
export class AttemptRepository {
  constructor(context, dispatchers) {
    this.db = PrepTrackerDatabase.get(context);
    this.dao = this.db.examAttemptDao();
  }

  /** A tiny boolean, not a Room query — startup reads this to decide onboarding vs Home. */
  async hasAttemptFast() { return attemptFlagDataStore().get(hasAttemptKey, false) ?? false; }

  async markHasAttempt(value) { attemptFlagDataStore().set(hasAttemptKey, value); }

  /** Re-syncs the fast flag from Room — call after a write that bypassed upsert, e.g. migration. */
  async refreshHasAttemptFlag() { await this.markHasAttempt((await this.activeAttempt()) != null); }

  /** Flow<ExamAttempt?> */
  observeActiveAttempt() { return this.dao.observeActive().map((it) => it?.toDomain() ?? null); }

  async activeAttempt() { return this.dao.activeOnce()?.toDomain() ?? null; }

  async upsert(attempt) {
    this.dao.upsert(toEntity(attempt));
    await this.markHasAttempt(true);
  }

  async byId(id) { return this.dao.byId(id)?.toDomain() ?? null; }

  /** Deletes every attempt-scoped row across every table, atomically. Used by "clear plan". */
  async deleteAttemptAndAllData(attemptId) {
    const db = this.db;
    await db.withTransaction(() => {
      db.studySessionDao().deleteAllForAttempt(attemptId);
      db.mockTestDao().deleteAllForAttempt(attemptId);
      db.studyPlanBlockDao().deleteForAttempt(attemptId);
      db.topicProgressDao().deleteForAttempt(attemptId);
      db.revisionStateDao().deleteForAttempt(attemptId);
      db.chapterStageDao().deleteForAttempt(attemptId);
      db.chapterOverrideDao().deleteForAttempt(attemptId);
      db.coachingDao().deleteForAttempt(attemptId);
      db.coachingDao().deletePositionForAttempt(attemptId);
      db.targetNodeOverrideDao().deleteForAttempt(attemptId);
      db.availabilityDao().deleteWeeklyForAttempt(attemptId);
      db.availabilityDao().deleteOverridesForAttempt(attemptId);
      db.studyPreferenceDao().deletePreferencesForAttempt(attemptId);
      db.studyPreferenceDao().deleteSubjectPreferencesForAttempt(attemptId);
      db.studyPreferenceDao().deleteWindowsForAttempt(attemptId);
      this.dao.deleteAttempt(attemptId);
    });
    await this.markHasAttempt(false);
  }
}
