package com.exam.assistant.domain

import java.time.LocalDate
import java.util.UUID

/** One flattened, pickable leaf topic — the unit the seeder schedules sessions against. */
private data class SeedLeaf(
    val nodeKey: String,
    val title: String,
    val sectionName: String,
    val subjectId: String,
)

private fun flattenLeaves(sections: List<SyllabusSection>): List<SeedLeaf> =
    sections.flatMapIndexed { sectionIndex, section ->
        val subjectId = sectionSubjectId(sectionIndex)
        section.topics.flatMapIndexed { topicIndex, topic ->
            collectLeaves(topic, "t1_${sectionIndex}_$topicIndex", section.name, subjectId)
        }
    }

private fun collectLeaves(
    node: SyllabusTopicNode,
    key: String,
    sectionName: String,
    subjectId: String,
): List<SeedLeaf> {
    if (node.children.isEmpty()) return listOf(SeedLeaf(key, node.name, sectionName, subjectId))
    return node.children.flatMapIndexed { index, child ->
        collectLeaves(child, "${key}_$index", sectionName, subjectId)
    }
}

/**
 * Backfills [days] of completed study history ending yesterday (today is left alone),
 * working sequentially through the syllabus so coverage looks like real, steady progress
 * rather than random scatter. Sessions fill each day's real budget in ~50-minute blocks.
 *
 * Pure and deterministic given the same inputs except for generated session IDs.
 */
fun generateBackfillHistory(
    sections: List<SyllabusSection>,
    today: LocalDate,
    days: Int,
    weekdayHours: Float,
    weekendHours: Float,
): Pair<List<StudySessionRecord>, Set<String>> {
    val leaves = flattenLeaves(sections)
    if (leaves.isEmpty() || days <= 0) return emptyList<StudySessionRecord>() to emptySet()

    val sessions = mutableListOf<StudySessionRecord>()
    val doneLeaves = mutableSetOf<String>()
    var leafCursor = 0
    val sessionMinutes = 50

    for (dayOffset in days downTo 1) {
        val date = today.minusDays(dayOffset.toLong())
        val budgetMinutes = (todayBudget(weekdayHours, weekendHours, date) * 60)
            .coerceIn(0, 12 * 60)
        if (budgetMinutes < sessionMinutes) continue

        var minuteCursor = DAY_TIMELINE_START
        var minutesUsed = 0
        while (minutesUsed + sessionMinutes <= budgetMinutes && minuteCursor + sessionMinutes <= DAY_TIMELINE_END) {
            val leaf = leaves[leafCursor % leaves.size]
            leafCursor++
            sessions += StudySessionRecord(
                id = UUID.randomUUID().toString(),
                date = date,
                startMinuteOfDay = minuteCursor,
                durationMinutes = sessionMinutes,
                nodeKey = leaf.nodeKey,
                title = leaf.title,
                sectionName = leaf.sectionName,
                subjectId = leaf.subjectId,
                isRevision = false,
                completed = true,
                runningEndsAtMs = null,
            )
            doneLeaves += leaf.nodeKey
            minuteCursor += sessionMinutes + 10
            minutesUsed += sessionMinutes
        }
    }

    return sessions to doneLeaves
}
