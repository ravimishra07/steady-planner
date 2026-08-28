package com.exam.assistant.domain

/**
 * "I am intentionally not preparing this." First-class domain state,
 * replacing the old `excludedSectionKeys` field buried in a UI-state class.
 * Static syllabus data is never deleted — only marked excluded from target.
 */
data class TargetNodeOverride(
    val attemptId: String,
    val nodeId: String,
    val state: TargetNodeState,
    val updatedAtEpochMs: Long,
)

enum class TargetNodeState {
    INCLUDED,
    EXCLUDED,
}

/**
 * Deterministic inheritance rule: a node is excluded if it has its own
 * EXCLUDED override, or if any ancestor (walking from the exam pack root
 * down to this node) has an EXCLUDED override and no closer descendant
 * override re-includes it. In other words: the override *nearest* the node
 * on its root path wins; with no override anywhere on that path, the node
 * is included by default.
 *
 * [overridesByNodeId] is a flat map of every explicit override for this
 * attempt, keyed by nodeId. [ancestorChain] must list nodeId from the
 * subject/root down to (but not including) the node itself, in that order.
 */
fun isNodeEffectivelyExcluded(
    nodeId: String,
    ancestorChain: List<String>,
    overridesByNodeId: Map<String, TargetNodeState>,
): Boolean {
    overridesByNodeId[nodeId]?.let { return it == TargetNodeState.EXCLUDED }
    for (ancestorId in ancestorChain.asReversed()) {
        overridesByNodeId[ancestorId]?.let { return it == TargetNodeState.EXCLUDED }
    }
    return false
}

/** Every leaf id in [pack] that is effectively included in the target syllabus. */
fun ExamPack.effectiveTargetLeafIds(overridesByNodeId: Map<String, TargetNodeState>): Set<String> {
    if (overridesByNodeId.isEmpty()) return leafIds().toSet()
    val result = mutableSetOf<String>()
    for (subject in subjects) {
        collectIncludedLeaves(subject.nodes, ancestorChain = emptyList(), overridesByNodeId, result)
    }
    return result
}

private fun collectIncludedLeaves(
    nodes: List<SyllabusNode>,
    ancestorChain: List<String>,
    overridesByNodeId: Map<String, TargetNodeState>,
    result: MutableSet<String>,
) {
    for (node in nodes) {
        if (node.children.isEmpty()) {
            if (!isNodeEffectivelyExcluded(node.id, ancestorChain, overridesByNodeId)) {
                result += node.id
            }
        } else {
            collectIncludedLeaves(node.children, ancestorChain + node.id, overridesByNodeId, result)
        }
    }
}
