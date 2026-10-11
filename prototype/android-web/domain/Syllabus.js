// Port of domain/Syllabus.kt (legacy positional syllabus model)
import { dataClass, enumClass, sumOf } from './kt.js';

export const SyllabusTopicNode = dataClass({ name: undefined, hours: undefined, children: [], id: null });
export const SyllabusSection = dataClass({ name: undefined, questions: undefined, topics: undefined, id: null });

export function topicHours(node) {
  return node.hours ?? sumOf(node.children, (c) => topicHours(c));
}

export function leafKeys(node, pathKey) {
  if (node.children.length === 0) return [node.id ?? pathKey];
  return node.children.flatMap((child, index) => leafKeys(child, `${pathKey}_${index}`));
}

export const SyllabusTickState = enumClass(['NONE', 'PARTIAL', 'ALL']);

/** doneLeaves: Set<String> */
export function tickState(leafKeysList, doneLeaves) {
  if (leafKeysList.length === 0) return SyllabusTickState.NONE;
  const doneCount = leafKeysList.filter((k) => doneLeaves.has(k)).length;
  if (doneCount === 0) return SyllabusTickState.NONE;
  if (doneCount === leafKeysList.length) return SyllabusTickState.ALL;
  return SyllabusTickState.PARTIAL;
}
