// Port of core/data/backup/BackupRepository.kt. Same file format, so a backup made here imports on
// Android and vice versa:
//   { "schemaVersion": 1, "dbVersion": 11, "exportedAt": "<ISO-8601>",
//     "tables": { "<table>": { "columns": [..], "rows": [[..], ..] } },
//     "prefs":  { "<datastore>": { "<key>": { "type": "string", "value": .. } } } }
// Booleans are written as 0/1 like SQLite INTEGER cells and read back by column.
//
// Stubbed:
//  - Uri / ContentResolver. exportTo(target): target is a filename string (browser download through a
//    Blob + <a download>) or a function receiving the JSON text. importFrom(source): a File/Blob
//    (from <input type=file>) or the JSON text itself.
//  - SQLite schema introspection -> the entity registry (PrepTrackerDatabase.ENTITIES): table names
//    sorted like `ORDER BY name`, columns in entity declaration order.
//  - DataStore value types -> inferred from the JS value; Float/Long keys are listed explicitly.
import { prefs } from '../../platform/store.js';
import { Analytics, AnalyticsEvent } from '../common.js';
import { PrepTrackerDatabase, ENTITIES, columnsOf } from '../db/PrepTrackerDatabase.js';
import { PLAN_FLOAT_KEYS } from '../PlanStore.js';
import { PLAN_PROMPT_LONG_KEYS } from '../repo/RollingPlanRepository.js';

/** How an import ended. Anything but Imported leaves the current data untouched. */
export const ImportResult = Object.freeze({
  Imported: Object.freeze({ type: 'Imported' }),
  /** Made by a newer app version (a higher dbVersion or schemaVersion) than this one. */
  TooNew: Object.freeze({ type: 'TooNew' }),
  /** An older backup whose tables differ from this version's. */
  Incompatible: Object.freeze({ type: 'Incompatible' }),
  /** Not a backup file, damaged, or unreadable. */
  Unreadable: Object.freeze({ type: 'Unreadable' }),
});

const SCHEMA_VERSION = 1;
const KEY_SCHEMA_VERSION = 'schemaVersion';
const KEY_DB_VERSION = 'dbVersion';
const KEY_EXPORTED_AT = 'exportedAt';
const KEY_TABLES = 'tables';
const KEY_PREFS = 'prefs';
const KEY_COLUMNS = 'columns';
const KEY_ROWS = 'rows';
const KEY_TYPE = 'type';
const KEY_VALUE = 'value';
const TYPE_BOOLEAN = 'boolean';
const TYPE_INT = 'int';
const TYPE_LONG = 'long';
const TYPE_FLOAT = 'float';
const TYPE_DOUBLE = 'double';
const TYPE_STRING = 'string';
const TYPE_STRING_SET = 'stringSet';

/** Per-store keys whose DataStore type is not inferable from a JS number. */
const FLOAT_KEYS = { plan: new Set(PLAN_FLOAT_KEYS) };
const LONG_KEYS = { plan_prompts: new Set(PLAN_PROMPT_LONG_KEYS) };

const entityByTable = new Map(ENTITIES.map((e) => [e.tableName, e]));

const optInt = (v, def) => (typeof v === 'number' && Number.isFinite(v) ? Math.trunc(v) : def);
const isObj = (v) => v != null && typeof v === 'object' && !Array.isArray(v);

/**
 * Export and import of everything the student owns (§10.4). Import replaces, never merges. Not
 * exported: the live Focus timer and migration bookkeeping; the fast "has attempt" flag is recomputed.
 */
export class BackupRepository {
  constructor(context, dispatchers, attemptRepository, analytics = Analytics.NoOp) {
    this.attemptRepository = attemptRepository;
    this.analytics = analytics;
    this._room = null;
  }
  get room() { return this._room ?? (this._room = PrepTrackerDatabase.get()); }

  /** The DataStores holding the student's own data, by file name. */
  get userDataStores() {
    return new Map(['settings', 'custom_syllabus', 'stage_labels', 'plan', 'plan_prompts'].map((n) => [n, prefs(n)]));
  }

  /** Returns true on success. */
  async exportTo(target = defaultFileName()) {
    let ok;
    try {
      const json = JSON.stringify(await this.buildBackup());
      if (typeof target === 'function') await target(json);
      else downloadText(String(target), json);
      ok = true;
    } catch (e) {
      console.error(e);
      ok = false;
    }
    if (ok) this.analytics.log(AnalyticsEvent.EXPORT_DONE);
    return ok;
  }

  async importFrom(source) {
    let root;
    try {
      const text = typeof source === 'string' ? source : await source.text();
      root = JSON.parse(text);
      if (!isObj(root)) throw new Error('not an object');
    } catch { return ImportResult.Unreadable; }
    const schemaVersion = optInt(root[KEY_SCHEMA_VERSION], -1);
    const dbVersion = optInt(root[KEY_DB_VERSION], -1);
    const tables = isObj(root[KEY_TABLES]) ? root[KEY_TABLES] : null;
    if (schemaVersion < 1 || dbVersion < 1 || tables == null) return ImportResult.Unreadable;
    if (schemaVersion > SCHEMA_VERSION) return ImportResult.TooNew;
    const current = this.room.version;
    if (dbVersion > current) return ImportResult.TooNew;
    const currentTables = new Map(this.room.userTables().map((t) => [t, columnsOf(entityByTable.get(t))]));
    if (dbVersion < current) {
      const sameSchema = [...currentTables].every(([t, columns]) => {
        const saved = isObj(tables[t]) && Array.isArray(tables[t][KEY_COLUMNS]) ? tables[t][KEY_COLUMNS] : null;
        if (!saved) return false;
        const a = new Set(saved.map(String)); const b = new Set(columns);
        return a.size === b.size && [...a].every((c) => b.has(c));
      });
      if (!sameSchema) return ImportResult.Incompatible;
    }
    try {
      await this.room.withTransaction(() => {
        for (const t of currentTables.keys()) this.room.stores.get(t).clear();
        for (const [t, columns] of currentTables) {
          if (isObj(tables[t])) insertRows(this.room.stores.get(t), new Set(columns), tables[t]);
        }
      });
    } catch { return ImportResult.Unreadable; }
    const savedPrefs = isObj(root[KEY_PREFS]) ? root[KEY_PREFS] : {};
    for (const [name, store] of this.userDataStores) restorePrefs(store, isObj(savedPrefs[name]) ? savedPrefs[name] : null);
    try { await this.attemptRepository.refreshHasAttemptFlag(); } catch {}
    return ImportResult.Imported;
  }

  /** Log out: empties every table and the student's DataStores. The Pro entitlement is left alone. */
  async wipeAll() {
    let wiped;
    try {
      await this.room.withTransaction(() => { for (const t of this.room.userTables()) this.room.stores.get(t).clear(); });
      for (const store of this.userDataStores.values()) store.clear();
      wiped = true;
    } catch { wiped = false; }
    try { await this.attemptRepository.refreshHasAttemptFlag(); } catch {}
    return wiped;
  }

  /** The backup JSON object (also used by exportTo). */
  async buildBackup() {
    const tables = {};
    for (const t of this.room.userTables()) tables[t] = dumpTable(this.room.stores.get(t));
    const p = {};
    for (const [name, store] of this.userDataStores) p[name] = dumpPrefs(name, store.all());
    return {
      [KEY_SCHEMA_VERSION]: SCHEMA_VERSION,
      [KEY_DB_VERSION]: this.room.version,
      [KEY_EXPORTED_AT]: new Date().toISOString(),
      [KEY_TABLES]: tables,
      [KEY_PREFS]: p,
    };
  }
}
BackupRepository.SCHEMA_VERSION = SCHEMA_VERSION;

function defaultFileName() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `steadyline-backup-${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}.json`;
}

/** Browser download stand-in for ContentResolver.openOutputStream(uri). */
function downloadText(fileName, text) {
  if (typeof document === 'undefined' || typeof URL === 'undefined' || typeof Blob === 'undefined') {
    throw new Error('No output stream');
  }
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  const a = document.createElement('a');
  a.href = url; a.download = fileName; a.style.display = 'none';
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function dumpTable(store) {
  const entity = store.entity;
  const columns = columnsOf(entity);
  const bools = new Set(entity.booleanColumns ?? []);
  const rows = store.rawRows().map((r) => columns.map((c) => {
    const v = r[c];
    if (v == null) return null;
    if (bools.has(c) || typeof v === 'boolean') return v ? 1 : 0;
    return v;
  }));
  return { [KEY_COLUMNS]: columns, [KEY_ROWS]: rows };
}

/** Only columns that exist in this version's table are written; the rest take their defaults. */
function insertRows(store, known, saved) {
  const columns = Array.isArray(saved[KEY_COLUMNS]) ? saved[KEY_COLUMNS].map(String) : null;
  const rows = Array.isArray(saved[KEY_ROWS]) ? saved[KEY_ROWS] : null;
  if (!columns || !rows) return;
  const entity = store.entity;
  const defaults = entity({});
  const bools = new Set(entity.booleanColumns ?? []);
  const seen = new Set();
  for (const row of rows) {
    if (!Array.isArray(row)) throw new Error('row is not an array');
    const values = {};
    columns.forEach((column, i) => {
      if (known.has(column) && i < row.length) {
        let v = row[i];
        if (isObj(v)) throw new Error('BLOB cells are not supported'); // no BLOB columns in this schema
        if (bools.has(column) && v != null) v = v === true || v === 1 || v === '1';
        values[column] = v;
      }
    });
    for (const c of known) {
      if (!(c in values)) {
        if (defaults[c] === undefined) throw new Error(`NOT NULL constraint failed: ${c}`);
        values[c] = defaults[c];
      }
      if (values[c] == null && defaults[c] === undefined) throw new Error(`NOT NULL constraint failed: ${c}`);
    }
    const key = JSON.stringify(entity.primaryKeys.map((k) => values[k]));
    if (seen.has(key)) throw new Error('UNIQUE constraint failed'); // CONFLICT_ABORT
    seen.add(key);
    store.upsert(entity(values));
  }
}

function dumpPrefs(storeName, map) {
  const out = {};
  for (const [key, value] of Object.entries(map)) {
    let type; let encoded = value;
    if (typeof value === 'boolean') type = TYPE_BOOLEAN;
    else if (typeof value === 'string') type = TYPE_STRING;
    else if (Array.isArray(value)) { type = TYPE_STRING_SET; encoded = value.map(String); }
    else if (typeof value === 'number') {
      if (FLOAT_KEYS[storeName]?.has(key)) type = TYPE_FLOAT;
      else if (LONG_KEYS[storeName]?.has(key)) type = TYPE_LONG;
      else type = Number.isInteger(value) ? TYPE_INT : TYPE_DOUBLE;
    } else continue;
    out[key] = { [KEY_TYPE]: type, [KEY_VALUE]: encoded };
  }
  return out;
}

/** Replaces the whole store; a store missing from the file ends up empty, as on a fresh install. */
function restorePrefs(store, saved) {
  store.edit((p) => {
    for (const k of Object.keys(p)) delete p[k];
    if (!saved) return;
    for (const [name, entry] of Object.entries(saved)) {
      try {
        if (!isObj(entry)) continue;
        const v = entry[KEY_VALUE];
        switch (entry[KEY_TYPE]) {
          case TYPE_BOOLEAN: if (typeof v === 'boolean') p[name] = v; break;
          case TYPE_INT: case TYPE_LONG: if (typeof v === 'number') p[name] = Math.trunc(v); break;
          case TYPE_FLOAT: case TYPE_DOUBLE: if (typeof v === 'number') p[name] = v; break;
          case TYPE_STRING: if (v != null) p[name] = String(v); break;
          case TYPE_STRING_SET: if (Array.isArray(v)) p[name] = [...new Set(v.map(String))]; break;
          default: break;
        }
      } catch { /* runCatching per key */ }
    }
  });
}
