// Port of domain/Calibration.kt
import { dataClass, roundToInt, sumOf, coerceIn } from './kt.js';
import { flatten, leafIds } from './ExamPack.js';

export const CALIBRATION_MIN_CHAPTERS = 3;
export const CALIBRATION_MIN_FACTOR = 0.5;
export const CALIBRATION_MAX_FACTOR = 2.5;

/** Personal pace against bundled estimates. active once measured on >= CALIBRATION_MIN_CHAPTERS. */
export const Calibration = dataClass(
  { factor: undefined, chapters: undefined },
  {
    get active() { return this.chapters >= CALIBRATION_MIN_CHAPTERS; },
    /** The factor to the nearest tenth. */
    get rounded() { return roundToInt(this.factor * 10) / 10.0; },
  },
);
Calibration.NONE = Calibration({ factor: 1.0, chapters: 0 });

/**
 * Median of (actual focused Learn minutes ÷ estimated minutes) over fully covered chapters.
 * focusedLearnMinutesByNode: Map<String,Int>; coveredLeafIds: Set<String>.
 */
export function personalCalibration(pack, focusedLearnMinutesByNode, coveredLeafIds) {
  if (focusedLearnMinutesByNode.size === 0) return Calibration.NONE;
  const ratios = pack.subjects.flatMap((subject) =>
    subject.nodes.flatMap((chapter) => {
      const nodes = flatten(chapter);
      const actual = sumOf(nodes, (n) => focusedLearnMinutesByNode.get(n.id) ?? 0);
      const leaves = leafIds(chapter);
      const estimate = sumOf(nodes.filter((n) => n.children.length === 0), (n) => n.estimatedMinutes ?? 0);
      if (actual <= 0 || estimate <= 0 || leaves.length === 0 || !leaves.every((l) => coveredLeafIds.has(l))) return [];
      return [actual / estimate];
    }),
  );
  if (ratios.length < CALIBRATION_MIN_CHAPTERS) return Calibration.NONE;
  const sorted = [...ratios].sort((a, b) => a - b);
  const mid = Math.trunc(sorted.length / 2);
  const median = sorted.length % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
  return Calibration({ factor: coerceIn(median, CALIBRATION_MIN_FACTOR, CALIBRATION_MAX_FACTOR), chapters: ratios.length });
}

export const personalCalibrationFactor = (pack, m, covered) => personalCalibration(pack, m, covered).factor;
