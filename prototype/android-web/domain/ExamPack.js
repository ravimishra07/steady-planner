// Port of domain/ExamPack.kt. Extension funs take the receiver as first arg.
import { dataClass, enumClass, sumOf, toIntOrNull } from './kt.js';

/** The only exam supported by the shipping app. */
export const NEET_EXAM_ID = 'neet';

export const ExamPack = dataClass({ schemaVersion: undefined, examId: undefined, displayName: undefined, syllabusVersion: undefined, subjects: undefined });
export const ExamSubject = dataClass({ id: undefined, name: undefined, shortName: null, order: undefined, questions: null, nodes: undefined });
export const SyllabusNode = dataClass({ id: undefined, title: undefined, kind: undefined, order: undefined, estimatedMinutes: null, children: [] });

export const SyllabusNodeKind = enumClass(['UNIT', 'CHAPTER', 'SECTION', 'TOPIC', 'SUBTOPIC']);

// Kotlin overloads the same extension name on SyllabusNode / ExamSubject / ExamPack.
// The JS functions dispatch on the receiver's shape: pack has `subjects`, subject has `nodes`.
const isPack = (x) => Array.isArray(x.subjects);
const isSubject = (x) => Array.isArray(x.nodes);

/** SyllabusNode.totalMinutes() / ExamSubject.totalMinutes() */
export function totalMinutes(node) {
  if (isSubject(node)) return subjectTotalMinutes(node);
  return (node.estimatedMinutes ?? 0) + sumOf(node.children, (c) => totalMinutes(c));
}
/** SyllabusNode.leafIds() / ExamSubject.leafIds() / ExamPack.leafIds() */
export function leafIds(node) {
  if (isPack(node)) return packLeafIds(node);
  if (isSubject(node)) return subjectLeafIds(node);
  return node.children.length === 0 ? [node.id] : node.children.flatMap((c) => leafIds(c));
}
/** ExamSubject.leafIds() */
export const subjectLeafIds = (subject) => subject.nodes.flatMap((n) => leafIds(n));
/** ExamPack.leafIds() */
export const packLeafIds = (pack) => pack.subjects.flatMap((s) => subjectLeafIds(s));
/** ExamSubject.totalMinutes() */
export const subjectTotalMinutes = (subject) => sumOf(subject.nodes, (n) => totalMinutes(n));
/** ExamSubject.flatten() — all nodes, parent before children. */
export const subjectFlatten = (subject) => subject.nodes.flatMap((n) => flatten(n));
/** SyllabusNode.flatten() / ExamSubject.flatten() */
export function flatten(node) {
  if (isSubject(node)) return subjectFlatten(node);
  return [node, ...node.children.flatMap((c) => flatten(c))];
}
/** ExamPack.findNode(nodeId) */
export function findNode(pack, nodeId) {
  for (const subject of pack.subjects) {
    const n = subjectFlatten(subject).find((x) => x.id === nodeId);
    if (n) return n;
  }
  return null;
}
/** ExamPack.findSubjectOf(nodeId) */
export function findSubjectOf(pack, nodeId) {
  return pack.subjects.find((s) => subjectFlatten(s).some((x) => x.id === nodeId)) ?? null;
}
/** ExamPack.subtreeIds(nodeId): Set<String> */
export function subtreeIds(pack, nodeId) {
  const node = findNode(pack, nodeId);
  if (!node) return new Set();
  return new Set(flatten(node).map((n) => n.id));
}
/** SyllabusNode.classNumber(): NCERT class 11/12 from the stable id; null for custom chapters. */
export function classNumber(node) {
  const part = node.id.split('.')[2];
  return part === undefined ? null : toIntOrNull(part);
}
/** ExamPack.chapters(): top-level nodes of every subject. */
export const chapters = (pack) => pack.subjects.flatMap((s) => s.nodes);
/** ExamPack.chapterOf(nodeId) */
export function chapterOf(pack, nodeId) {
  return chapters(pack).find((ch) => ch.id === nodeId || flatten(ch).some((x) => x.id === nodeId)) ?? null;
}
