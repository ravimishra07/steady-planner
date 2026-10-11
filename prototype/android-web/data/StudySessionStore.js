// Port of core/data/StudySessionStore.kt. DataStore "study_sessions" -> prefs('study_sessions'),
// key "sessions_json" holding the same JSON array string as Android. Legacy: migration input only.
import { prefs } from '../platform/store.js';
import { isBlank } from '../domain/kt.js';
import { StudySessionRecord, parseStoreDate, toStoreString } from '../domain/StudySession.js';

const sessionsKey = 'sessions_json';

export class StudySessionStore {
  constructor(_context, _dispatchers) { this._ds = prefs('study_sessions'); }

  async loadAll() {
    const raw = this._ds.get(sessionsKey, null);
    if (isBlank(raw)) return [];
    return parseSessions(raw);
  }

  async sessionsFor(date) { return (await this.loadAll()).filter((it) => it.date === date); }

  async upsert(session) {
    const all = await this.loadAll();
    const index = all.findIndex((it) => it.id === session.id);
    if (index >= 0) all[index] = session; else all.push(session);
    this.saveAll(all);
  }

  /** Merges many records in one read-modify-write. */
  async upsertAll(sessions) {
    const all = new Map((await this.loadAll()).map((s) => [s.id, s]));
    sessions.forEach((s) => all.set(s.id, s));
    this.saveAll([...all.values()]);
  }

  /** Compatibility projection of Room's initial plan while legacy Today/Focus are being retired. */
  async replaceGeneratedPlan(attemptId, sessions) {
    const prefix = `auto_${attemptId}_`;
    const kept = (await this.loadAll()).filter((it) => !it.id.startsWith(prefix));
    this.saveAll([...kept, ...sessions]);
  }

  async replaceFutureGeneratedPlan(attemptId, start, sessions) {
    const prefix = `auto_${attemptId}_`;
    const kept = (await this.loadAll()).filter((it) => !(it.id.startsWith(prefix) && !it.completed && it.date >= start));
    this.saveAll([...kept, ...sessions]);
  }

  async replace(originalId, replacement) {
    const kept = (await this.loadAll()).filter((it) => it.id !== originalId && it.id !== replacement.id);
    this.saveAll([...kept, replacement]);
  }

  async delete(id) { this.saveAll((await this.loadAll()).filter((it) => it.id !== id)); }

  async clear() { this._ds.remove(sessionsKey); }

  /** private */
  saveAll(sessions) { this._ds.set(sessionsKey, encodeSessions(sessions)); }
}

function parseSessions(raw) {
  const array = JSON.parse(raw);
  const out = [];
  for (const obj of array) {
    const date = parseStoreDate(obj.date);
    if (date == null) continue;
    out.push(StudySessionRecord({
      id: obj.id,
      date,
      startMinuteOfDay: obj.startMin,
      durationMinutes: obj.durationMin,
      nodeKey: obj.nodeKey,
      title: obj.title,
      sectionName: obj.section,
      subjectId: obj.subjectId,
      isRevision: obj.revision ?? false,
      completed: obj.completed ?? false,
      runningEndsAtMs: obj.endsAtMs ?? null,
    }));
  }
  return out;
}

function encodeSessions(sessions) {
  return JSON.stringify(sessions.map((s) => {
    const obj = {
      id: s.id, date: toStoreString(s.date), startMin: s.startMinuteOfDay, durationMin: s.durationMinutes,
      nodeKey: s.nodeKey, title: s.title, section: s.sectionName, subjectId: s.subjectId,
      revision: s.isRevision, completed: s.completed,
    };
    if (s.runningEndsAtMs != null) obj.endsAtMs = s.runningEndsAtMs;
    return obj;
  }));
}
