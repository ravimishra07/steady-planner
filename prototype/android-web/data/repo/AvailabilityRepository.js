// Port of core/data/repo/AvailabilityRepository.kt. suspend -> async.
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { weeklyToEntity, overrideToEntity } from '../db/AvailabilityEntity.js';

export class AvailabilityRepository {
  constructor(context, dispatchers) { this.dao = PrepTrackerDatabase.get(context).availabilityDao(); }

  async weeklyFor(attemptId) { return this.dao.weeklyFor(attemptId).map((it) => it.toDomain()); }

  async overridesFor(attemptId) { return this.dao.overridesFor(attemptId).map((it) => it.toDomain()); }

  /** Replaces the whole weekly pattern for [attemptId] — used by onboarding/settings and legacy migration. */
  async replaceWeekly(attemptId, windows) {
    this.dao.deleteWeeklyForAttempt(attemptId);
    this.dao.upsertWeekly(windows.map(weeklyToEntity));
  }

  async upsertOverride(override) { this.dao.upsertOverride(overrideToEntity(override)); }
}
