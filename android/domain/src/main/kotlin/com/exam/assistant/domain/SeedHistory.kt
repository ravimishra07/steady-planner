package com.exam.assistant.domain

import java.time.LocalDate
import java.util.UUID

const val DEMO_HISTORY_DAYS = 16

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

/**
 * Creates the small, repeatable data set used by the in-app demo-data action.
 * It covers today and the fifteen preceding days so the one-day, seven-day and
 * longer Insights ranges all have meaningful information. Stable IDs make the
 * action safe to run more than once: it refreshes the demo records instead of
 * accumulating duplicate sessions.
 */
fun generateDemoHistory(
    sections: List<SyllabusSection>,
    today: LocalDate,
    days: Int = DEMO_HISTORY_DAYS,
): Pair<List<StudySessionRecord>, Set<String>> {
    val leaves = flattenLeaves(sections)
    if (leaves.isEmpty() || days <= 0) return emptyList<StudySessionRecord>() to emptySet()

    val sessions = mutableListOf<StudySessionRecord>()
    val doneLeaves = mutableSetOf<String>()
    var leafCursor = 0

    fun nextLeaf(): SeedLeaf = leaves[(leafCursor++ % leaves.size)]

    for (dayOffset in (days - 1) downTo 0) {
        val date = today.minusDays(dayOffset.toLong())
        val completedCount = if (dayOffset == 0) 1 else 2 + (dayOffset % 2)
        repeat(completedCount) { index ->
            val leaf = nextLeaf()
            val duration = if (index == 0) 50 else 40
            sessions += StudySessionRecord(
                id = "demo-${date}-completed-$index",
                date = date,
                startMinuteOfDay = DAY_TIMELINE_START + index * 70,
                durationMinutes = duration,
                nodeKey = leaf.nodeKey,
                title = leaf.title,
                sectionName = leaf.sectionName,
                subjectId = leaf.subjectId,
                isRevision = dayOffset > 0 && dayOffset % 5 == 0 && index == completedCount - 1,
                completed = true,
            )
            doneLeaves += leaf.nodeKey
        }
    }

    // Keep two uncompleted blocks on Today so its timeline has something to act on.
    repeat(2) { index ->
        val leaf = nextLeaf()
        sessions += StudySessionRecord(
            id = "demo-${today}-planned-$index",
            date = today,
            startMinuteOfDay = 17 * 60 + index * 70,
            durationMinutes = if (index == 0) 50 else 40,
            nodeKey = leaf.nodeKey,
            title = leaf.title,
            sectionName = leaf.sectionName,
            subjectId = leaf.subjectId,
            isRevision = index == 1,
            completed = false,
        )
    }

    return sessions to doneLeaves
}
