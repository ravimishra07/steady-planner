package com.exam.assistant.domain

import java.time.DayOfWeek
import java.time.LocalDate

private const val DEFAULT_SESSION_MINUTES = 50
private const val MIN_SESSION_MINUTES = 30
private const val DEFAULT_BREAK_MINUTES = 10

private data class PlanLeaf(val nodeId: String, val subjectId: String)

/**
 * Creates the first rolling week of intent from real syllabus leaves and real
 * availability. It never schedules across a fixed commitment because callers
 * pass only the free windows produced from onboarding.
 */
fun generateInitialPlan(
    attemptId: String,
    pack: ExamPack,
    startDate: LocalDate,
    days: Int,
    weekdayHours: Float,
    weekendHours: Float,
    weeklyAvailability: List<WeeklyAvailability>,
    excludedLeafIds: Set<String> = emptySet(),
    nowMs: Long,
): List<StudyPlanBlock> {
    val leavesBySubject = pack.subjects.map { subject ->
        subject.nodes.flatMap { node ->
            node.leafIds().map { PlanLeaf(it, subject.id) }
        }.filterNot { it.nodeId in excludedLeafIds }
    }.filter { it.isNotEmpty() }
    val leaves = buildList {
        repeat(leavesBySubject.maxOfOrNull { it.size } ?: 0) { index ->
            leavesBySubject.forEach { subjectLeaves ->
                subjectLeaves.getOrNull(index)?.let(::add)
            }
        }
    }
    if (leaves.isEmpty() || days <= 0) return emptyList()

    val result = mutableListOf<StudyPlanBlock>()
    var leafCursor = 0
    repeat(days) { dayOffset ->
        val date = startDate.plusDays(dayOffset.toLong())
        var budget = ((if (date.dayOfWeek.isWeekend()) weekendHours else weekdayHours) * 60).toInt()
        val windows = weeklyAvailability
            .filter { it.dayOfWeek == date.dayOfWeek }
            .sortedBy { it.startMinuteOfDay }
        var blockIndex = 0
        for (window in windows) {
            var cursor = window.startMinuteOfDay
            while (budget >= MIN_SESSION_MINUTES && window.endMinuteOfDay - cursor >= MIN_SESSION_MINUTES) {
                val available = window.endMinuteOfDay - cursor
                val duration = minOf(DEFAULT_SESSION_MINUTES, budget, available)
                if (duration < MIN_SESSION_MINUTES) break
                val leaf = leaves[leafCursor % leaves.size]
                leafCursor++
                result += StudyPlanBlock(
                    id = "auto_${attemptId}_${date}_$blockIndex",
                    attemptId = attemptId,
                    nodeId = leaf.nodeId,
                    subjectId = leaf.subjectId,
                    customTitle = null,
                    activityType = StudyActivityType.LEARN,
                    scheduledDate = date,
                    startMinuteOfDay = cursor,
                    plannedMinutes = duration,
                    status = PlanBlockStatus.PLANNED,
                    source = PlanBlockSource.AUTO,
                    rescheduledFromId = null,
                    replacedById = null,
                    createdAtEpochMs = nowMs,
                    updatedAtEpochMs = nowMs,
                )
                blockIndex++
                budget -= duration
                cursor += duration + DEFAULT_BREAK_MINUTES
            }
            if (budget < MIN_SESSION_MINUTES) break
        }
    }
    return result
}

private fun DayOfWeek.isWeekend(): Boolean = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY
