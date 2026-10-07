package com.exam.assistant.domain

data class SyllabusTopicNode(
    val name: String,
    val hours: Double?,
    val children: List<SyllabusTopicNode> = emptyList(),
    /** Stable pack id when sourced from the normalized ExamPack; null for legacy positional data. */
    val id: String? = null,
)

data class SyllabusSection(
    val name: String,
    val questions: Int,
    val topics: List<SyllabusTopicNode>,
    /** Stable ExamPack subject id; null only for pre-migration legacy fixtures. */
    val id: String? = null,
)

fun topicHours(node: SyllabusTopicNode): Double =
    node.hours ?: node.children.sumOf { topicHours(it) }

fun leafKeys(node: SyllabusTopicNode, pathKey: String): List<String> {
    if (node.children.isEmpty()) return listOf(node.id ?: pathKey)
    return node.children.flatMapIndexed { index, child ->
        leafKeys(child, "${pathKey}_$index")
    }
}

enum class SyllabusTickState { NONE, PARTIAL, ALL }

fun tickState(leafKeys: List<String>, doneLeaves: Set<String>): SyllabusTickState {
    if (leafKeys.isEmpty()) return SyllabusTickState.NONE
    val doneCount = leafKeys.count { it in doneLeaves }
    return when {
        doneCount == 0 -> SyllabusTickState.NONE
        doneCount == leafKeys.size -> SyllabusTickState.ALL
        else -> SyllabusTickState.PARTIAL
    }
}
