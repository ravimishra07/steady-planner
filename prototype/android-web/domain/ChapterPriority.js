// Port of domain/ChapterPriority.kt — NEET PYQ chapter priority (§4.2).
// MiniJson is replaced by JSON.parse (same accepted subset for the generated file; errors throw).
import { dataClass, enumClass, toInt } from './kt.js';

export const PriorityBand = enumClass(['HIGH', 'MEDIUM', 'LOW', 'NO_DATA']);

/** meanQuestionsPerYear: Double? (null with NO_DATA). sourceNames: String[]. */
export const ChapterPriority = dataClass({ chapterId: undefined, band: undefined, meanQuestionsPerYear: undefined, yearsUsed: undefined, sourceNames: undefined });

/** chapters: Map<chapterId, ChapterPriority> */
export const PyqPriorityTable = dataClass(
  { firstYear: undefined, lastYear: undefined, method: undefined, generatedFrom: undefined, chapters: undefined },
  {
    /** A chapter missing from the table has no data. */
    forChapter(chapterId) {
      return this.chapters.get(chapterId) ??
        ChapterPriority({ chapterId, band: PriorityBand.NO_DATA, meanQuestionsPerYear: null, yearsUsed: 0, sourceNames: [] });
    },
  },
);
PyqPriorityTable.EMPTY = PyqPriorityTable({ firstYear: 0, lastYear: 0, method: '', generatedFrom: '', chapters: new Map() });

/** PriorityBand.yieldRank(): lower is planned earlier under high-yield-first. */
export function yieldRank(band) {
  switch (band) {
    case PriorityBand.HIGH: return 0;
    case PriorityBand.MEDIUM: return 1;
    case PriorityBand.LOW: return 2;
    default: return 3;
  }
}

const isObj = (v) => v != null && typeof v === 'object' && !Array.isArray(v);

/** Parses the asset written by tools/build_pyq_priority.py. Throws on a malformed file. */
export function parsePyqPriority(json) {
  let root;
  try { root = JSON.parse(json); } catch (e) { throw new Error(`IllegalArgumentException: ${e.message}`); }
  if (!isObj(root)) throw new Error('IllegalArgumentException: priority file is not an object');
  const years = Array.isArray(root.years) ? root.years.filter((y) => typeof y === 'number').map(toInt) : [];
  if (!isObj(root.chapters)) throw new Error('IllegalArgumentException: priority file has no chapters');
  const chapters = new Map();
  for (const [id, node] of Object.entries(root.chapters)) {
    if (!isObj(node)) throw new Error(`IllegalArgumentException: chapter ${id} is not an object`);
    chapters.set(id, ChapterPriority({
      chapterId: id,
      band: PriorityBand.valueOfOrNull(typeof node.band === 'string' ? node.band : null) ?? PriorityBand.NO_DATA,
      meanQuestionsPerYear: typeof node.meanQuestionsPerYear === 'number' ? node.meanQuestionsPerYear : null,
      yearsUsed: typeof node.yearsUsed === 'number' ? toInt(node.yearsUsed) : 0,
      sourceNames: Array.isArray(node.sourceNames) ? node.sourceNames.filter((s) => typeof s === 'string') : [],
    }));
  }
  return PyqPriorityTable({
    firstYear: years[0] ?? 0,
    lastYear: years.length ? years[years.length - 1] : 0,
    method: typeof root.method === 'string' ? root.method : '',
    generatedFrom: typeof root.generatedFrom === 'string' ? root.generatedFrom : '',
    chapters,
  });
}
