// Port of domain/TargetSyllabus.kt
import { dataClass, enumClass } from './kt.js';
import { leafIds } from './ExamPack.js';

/** "I am intentionally not preparing this." */
export const TargetNodeOverride = dataClass({ attemptId: undefined, nodeId: undefined, state: undefined, updatedAtEpochMs: undefined });

export const TargetNodeState = enumClass(['INCLUDED', 'EXCLUDED']);

/** The override nearest the node on its root path wins. overridesByNodeId: Map<String, TargetNodeState>. */
export function isNodeEffectivelyExcluded(nodeId, ancestorChain, overridesByNodeId) {
  const own = overridesByNodeId.get(nodeId);
  if (own != null) return own === TargetNodeState.EXCLUDED;
  for (let i = ancestorChain.length - 1; i >= 0; i--) {
    const o = overridesByNodeId.get(ancestorChain[i]);
    if (o != null) return o === TargetNodeState.EXCLUDED;
  }
  return false;
}

/** ExamPack.effectiveTargetLeafIds(overrides): Set<String> */
export function effectiveTargetLeafIds(pack, overridesByNodeId) {
  if (overridesByNodeId.size === 0) return new Set(leafIds(pack));
  const result = new Set();
  for (const subject of pack.subjects) collectIncludedLeaves(subject.nodes, [], overridesByNodeId, result);
  return result;
}

function collectIncludedLeaves(nodes, ancestorChain, overridesByNodeId, result) {
  for (const node of nodes) {
    if (node.children.length === 0) {
      if (!isNodeEffectivelyExcluded(node.id, ancestorChain, overridesByNodeId)) result.add(node.id);
    } else {
      collectIncludedLeaves(node.children, [...ancestorChain, node.id], overridesByNodeId, result);
    }
  }
}
