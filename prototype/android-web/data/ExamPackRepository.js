// Port of core/data/ExamPackRepository.kt.
//
// Stubbed / changed:
//  - context.assets.open(name) -> fetch('assets/<name>') (same file names as android/app/src/main/assets),
//    so every loader is async. The parsed pack is cached in memory per exam id, like Kotlin.
//  - DataStore "custom_syllabus" -> prefs('custom_syllabus'), keys "chapters_<examId>" holding the
//    same JSON array string.
//  - `examPackFor(examId)` and `examPackFor(examId, attemptId)` are one function: attemptId omitted or
//    null returns the pack without chapter renames (the Kotlin one-arg overload).
import { prefs } from '../platform/store.js';
import { dataClass, coerceAtLeast, isBlank, toInt } from '../domain/kt.js';
import { ExamPack, ExamSubject, SyllabusNode, SyllabusNodeKind, NEET_EXAM_ID } from '../domain/ExamPack.js';
import { withChapterTitles } from '../domain/ChapterTitles.js';
import { PyqPriorityTable, parsePyqPriority } from '../domain/ChapterPriority.js';
import { PrepTrackerDatabase } from './db/PrepTrackerDatabase.js';
import { ChapterOverrideEntity } from './db/ChapterOverrideEntity.js';

/** Kotlin: internal Context.customSyllabusDataStore (BackupRepository exports it). */
export const customSyllabusDataStore = () => prefs('custom_syllabus');

const PRIORITY_ASSET = 'neet_pyq_priority.json';

/** Base URL of the bundled assets (relative to index.html). Overridable for tests. */
export const assetConfig = { base: 'assets/' };

async function readAsset(name) {
  const res = await fetch(`${assetConfig.base}${name}`);
  if (!res.ok) throw new Error(`asset ${name}: HTTP ${res.status}`);
  return res.text();
}

export const CustomSyllabusChapter = dataClass({
  id: undefined, examId: undefined, subjectId: undefined, subjectName: undefined, title: undefined,
  estimatedMinutes: undefined, topics: [],
});

/**
 * Loads the versioned, stable-ID ExamPack from the bundled asset — parsed off the critical
 * path, cached in memory, never re-parsed per screen.
 */
export class ExamPackRepository {
  constructor(context, dispatchers) {
    this._db = null;
    this.bundledCache = new Map(); // examId -> Promise<ExamPack>
    this.priorityCache = null; // Promise<PyqPriorityTable>
  }
  get db() { return this._db ?? (this._db = PrepTrackerDatabase.get()); }

  /**
   * PYQ chapter priorities (`neet_pyq_priority.json`). Read on first use, then cached. A missing or
   * unreadable file yields an empty table, so planning falls back to syllabus order.
   */
  async pyqPriorities() {
    if (!this.priorityCache) {
      this.priorityCache = readAsset(PRIORITY_ASSET).then(parsePyqPriority).catch(() => PyqPriorityTable.EMPTY);
    }
    return this.priorityCache;
  }

  async examPack() { return this.bundledPack(NEET_EXAM_ID); }

  /**
   * Returns only the pack that belongs to [examId], plus custom chapters; with [attemptId], the pack
   * as that student sees it (chapter renames, §5.2).
   */
  async examPackFor(examId, attemptId = null) {
    const pack = await this.packWithCustom(examId, await this.customChapters(examId));
    return attemptId == null ? pack : withChapterTitles(pack, await this.chapterTitles(attemptId));
  }

  /** The student's chapter renames, by chapter id. */
  async chapterTitles(attemptId) {
    const m = new Map();
    for (const row of this.db.chapterOverrideDao().allOnce(attemptId)) {
      if (row.customTitle != null && !isBlank(row.customTitle)) m.set(row.chapterId, row.customTitle);
    }
    return m;
  }

  /** Renames a chapter for this attempt. A null or blank [title] restores the original name. */
  async renameChapter(attemptId, chapterId, title) {
    const dao = this.db.chapterOverrideDao();
    if (isBlank(title)) dao.delete(attemptId, chapterId);
    else dao.upsert(ChapterOverrideEntity({ attemptId, chapterId, customTitle: title.trim() }));
  }

  async packWithCustom(examId, custom) {
    const base = await this.bundledPack(examId);
    return this.mergeCustom(base, custom);
  }

  async customChapters(examId) {
    const raw = customSyllabusDataStore().get(customKey(examId), null);
    if (isBlank(raw)) return [];
    try {
      return JSON.parse(raw).map((obj) => {
        for (const k of ['id', 'subjectId', 'subjectName', 'title']) if (typeof obj[k] !== 'string') throw new Error(k);
        if (typeof obj.minutes !== 'number') throw new Error('minutes');
        return CustomSyllabusChapter({
          id: obj.id,
          examId,
          subjectId: obj.subjectId,
          subjectName: obj.subjectName,
          title: obj.title,
          estimatedMinutes: toInt(obj.minutes),
          topics: Array.isArray(obj.topics) ? obj.topics.map(String) : [],
        });
      });
    } catch {
      return [];
    }
  }

  async saveCustomChapters(examId, chapters) {
    const array = chapters.map((c) => ({
      id: c.id, subjectId: c.subjectId, subjectName: c.subjectName, title: c.title,
      minutes: c.estimatedMinutes, topics: [...c.topics],
    }));
    customSyllabusDataStore().set(customKey(examId), JSON.stringify(array));
  }

  async clearCustomChapters(examId) { customSyllabusDataStore().remove(customKey(examId)); }

  /** private */
  mergeCustom(base, custom) {
    if (custom.length === 0) return base;
    const bySubject = new Map();
    for (const c of custom) { if (!bySubject.has(c.subjectId)) bySubject.set(c.subjectId, []); bySubject.get(c.subjectId).push(c); }
    const existing = base.subjects.map((subject) => {
      const additions = (bySubject.get(subject.id) ?? []).map((chapter, index) => toNode(chapter, subject.nodes.length + index));
      return subject.copy({ nodes: [...subject.nodes, ...additions] });
    });
    const existingIds = new Set(existing.map((s) => s.id));
    const grouped = new Map();
    for (const c of custom) {
      if (existingIds.has(c.subjectId)) continue;
      if (!grouped.has(c.subjectId)) grouped.set(c.subjectId, []);
      grouped.get(c.subjectId).push(c);
    }
    const newSubjects = [...grouped].map(([subjectId, chapters]) => ExamSubject({
      id: subjectId,
      name: chapters[0].subjectName,
      order: existing.length,
      nodes: chapters.map((chapter, index) => toNode(chapter, index)),
    }));
    return base.copy({ subjects: [...existing, ...newSubjects] });
  }

  /** private: cached per exam id. */
  bundledPack(examId) {
    let p = this.bundledCache.get(examId);
    if (!p) {
      p = examId === NEET_EXAM_ID
        ? loadExamPack('syllabus_neet.json')
        : Promise.resolve(ExamPack({
          schemaVersion: 1, examId, displayName: examId.toUpperCase(), syllabusVersion: 'custom-1', subjects: [],
        }));
      p = p.catch((e) => { this.bundledCache.delete(examId); throw e; });
      this.bundledCache.set(examId, p);
    }
    return p;
  }
}

const customKey = (examId) => `chapters_${examId}`;

/** CustomSyllabusChapter.toNode(order) */
function toNode(chapter, order) {
  return SyllabusNode({
    id: chapter.id,
    title: chapter.title,
    kind: SyllabusNodeKind.CHAPTER,
    order,
    estimatedMinutes: chapter.topics.length === 0 ? chapter.estimatedMinutes : null,
    children: chapter.topics.map((topic, index) => SyllabusNode({
      id: `${chapter.id}_topic_${index}`,
      title: topic,
      kind: SyllabusNodeKind.TOPIC,
      order: index,
      estimatedMinutes: coerceAtLeast(Math.trunc(chapter.estimatedMinutes / chapter.topics.length), 1),
    })),
  });
}

async function loadExamPack(assetName) {
  const root = JSON.parse(await readAsset(assetName));
  const req = (o, k) => { if (o[k] === undefined) throw new Error(`JSONObject["${k}"] not found.`); return o[k]; };
  const tier1 = req(root, 'tier1');
  return ExamPack({
    schemaVersion: typeof root.schemaVersion === 'number' ? toInt(root.schemaVersion) : 1,
    examId: String(req(root, 'examId')),
    displayName: String(req(root, 'displayName')),
    syllabusVersion: String(req(root, 'syllabusVersion')),
    subjects: tier1.map((json, index) => parseSubject(json, index, req)),
  });
}

function parseSubject(json, order, req) {
  return ExamSubject({
    id: String(req(json, 'id')),
    name: String(req(json, 'n')),
    order,
    questions: 'q' in json ? (typeof json.q === 'number' ? toInt(json.q) : 0) : null,
    nodes: parseNodes(req(json, 't'), 0, req),
  });
}

function parseNodes(array, depth, req) {
  return array.map((json, index) => SyllabusNode({
    id: String(req(json, 'id')),
    title: String(req(json, 'n')),
    kind: kindForDepth(depth),
    order: index,
    estimatedMinutes: 'm' in json ? (typeof json.m === 'number' ? toInt(json.m) : 0)
      : 'h' in json ? toInt((typeof json.h === 'number' ? json.h : NaN) * 60)
        : null,
    children: 'c' in json ? parseNodes(json.c, depth + 1, req) : [],
  }));
}

/** This pack is 3 levels deep under each subject: chapter -> topic -> subtopic. */
function kindForDepth(depth) {
  switch (depth) {
    case 0: return SyllabusNodeKind.CHAPTER;
    case 1: return SyllabusNodeKind.TOPIC;
    default: return SyllabusNodeKind.SUBTOPIC;
  }
}
