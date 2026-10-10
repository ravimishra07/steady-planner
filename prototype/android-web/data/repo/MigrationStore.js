// Port of core/data/repo/MigrationStore.kt. DataStore "migration" -> prefs('migration'),
// key legacy_migration_version. Gate so the legacy-to-Room migration runs exactly once.
import { prefs } from '../../platform/store.js';

const versionKey = 'legacy_migration_version';

export class MigrationStore {
  constructor(context, dispatchers) { this._ds = prefs('migration'); }
  async currentVersion() { return this._ds.get(versionKey, 0) ?? 0; }
  async markComplete(version) { this._ds.set(versionKey, version); }
}
