package com.exam.assistant.domain

data class SyllabusTopicNode(
    val name: String,
    val hours: Double?,
    val children: List<SyllabusTopicNode> = emptyList(),
)

data class SyllabusSection(
    val name: String,
    val questions: Int,
    val topics: List<SyllabusTopicNode>,
)

data class SyllabusSectionProgress(
    val name: String,
    val questions: Int,
    val topicsTotal: Int,
    val topicsDone: Int,
    val hoursTotal: Int,
    val hoursDone: Int,
    val percent: Int,
)

data class SyllabusProgress(
    val sections: List<SyllabusSectionProgress>,
    val hoursTotal: Int,
    val hoursDone: Int,
    val topicsTotal: Int,
    val topicsDone: Int,
    val percent: Int,
)

fun topicHours(node: SyllabusTopicNode): Double =
    node.hours ?: node.children.sumOf { topicHours(it) }

fun leafKeys(node: SyllabusTopicNode, pathKey: String): List<String> {
    if (node.children.isEmpty()) return listOf(pathKey)
    return node.children.flatMapIndexed { index, child ->
        leafKeys(child, "${pathKey}_$index")
    }
}

fun computeSyllabusProgress(
    sections: List<SyllabusSection>,
    doneLeaves: Set<String>,
    tierPrefix: String = "t1",
): SyllabusProgress {
    val sectionProgress = sections.mapIndexed { sectionIndex, section ->
        var hoursDone = 0.0
        var topicsDone = 0
        val hoursTotal = section.topics.sumOf { topicHours(it) }
        section.topics.forEachIndexed { topicIndex, topic ->
            val leaves = leafKeys(topic, "${tierPrefix}_${sectionIndex}_$topicIndex")
            val doneCount = leaves.count { it in doneLeaves }
            val fraction = if (leaves.isEmpty()) 0.0 else doneCount.toDouble() / leaves.size
            hoursDone += topicHours(topic) * fraction
            if (fraction >= 1.0) topicsDone++
        }
        SyllabusSectionProgress(
            name = section.name,
            questions = section.questions,
            topicsTotal = section.topics.size,
            topicsDone = topicsDone,
            hoursTotal = hoursTotal.toInt(),
            hoursDone = hoursDone.toInt(),
            percent = if (hoursTotal <= 0) 0 else ((hoursDone / hoursTotal) * 100).toInt().coerceIn(0, 100),
        )
    }
    val hoursTotal = sectionProgress.sumOf { it.hoursTotal }
    val hoursDone = sectionProgress.sumOf { it.hoursDone }
    return SyllabusProgress(
        sections = sectionProgress,
        hoursTotal = hoursTotal,
        hoursDone = hoursDone,
        topicsTotal = sectionProgress.sumOf { it.topicsTotal },
        topicsDone = sectionProgress.sumOf { it.topicsDone },
        percent = if (hoursTotal <= 0) 0 else ((hoursDone.toDouble() / hoursTotal) * 100).toInt().coerceIn(0, 100),
    )
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
