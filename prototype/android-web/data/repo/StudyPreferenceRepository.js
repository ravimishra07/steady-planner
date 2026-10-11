// Port of core/data/repo/StudyPreferenceRepository.kt. suspend -> async.
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { preferencesToEntity, subjectPreferenceToEntity, windowToEntity } from '../db/StudyPreferenceEntity.js';

export class StudyPreferenceRepository {
  constructor(context, dispatchers) { this.dao = PrepTrackerDatabase.get(context).studyPreferenceDao(); }

  async forAttempt(attemptId) { return this.dao.forAttempt(attemptId)?.toDomain() ?? null; }
  async upsert(preferences) { this.dao.upsert(preferencesToEntity(preferences)); }
  async subjectPreferences(attemptId) { return this.dao.subjectPreferencesFor(attemptId).map((it) => it.toDomain()); }
  async upsertSubjectPreference(preference) { this.dao.upsertSubject(subjectPreferenceToEntity(preference)); }
  async preferredWindows(attemptId) { return this.dao.windowsFor(attemptId).map((it) => it.toDomain()); }
  async upsertWindow(window) { this.dao.upsertWindow(windowToEntity(window)); }
}
