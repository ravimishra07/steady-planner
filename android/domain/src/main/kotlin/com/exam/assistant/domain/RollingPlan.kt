package com.exam.assistant.domain

import java.time.LocalDate

const val DEFAULT_PLANNING_HORIZON_DAYS = 21
const val DEFAULT_STUDY_SESSION_MINUTES = 50
const val DEFAULT_STUDY_BREAK_MINUTES = 10

data class RemainingWork(
    val nodeId: String,
    val subjectId: String,
    val estimatedMinutes: Int,
    val remainingMinutes: Int,
)

data class RollingPlanResult(
    val blocks: List<StudyPlanBlock>,
    val remainingWork: List<RemainingWork>,
    val unscheduledMinutes: Int,
)

enum class PlanningOrder {
    DEFAULT,
    SHORTEST_FIRST,
}

/**
 * Builds a deterministic rolling plan from the stable ExamPack tree.
 *
 * A leaf's learn workload is its estimated minutes minus actual focused learn
 * time and intent that must be preserved. Covered and excluded leaves never
 * receive another automatic LEARN block. The caller may safely replace future
 * PLANNED/AUTO rows with [RollingPlanResult.blocks]; completed, running, moved,
 * and manual rows are supplied as [preservedBlocks] and are never recreated.
 */
fun generateRollingPlan(
    attemptId: String,
    pack: ExamPack,
    startDate: LocalDate,
    targetDate: LocalDate,
    weeklyAvailability: List<WeeklyAvailability>,
    availabilityOverrides: List<AvailabilityOverride> = emptyList(),
    includedLeafIds: Set<String> = pack.leafIds().toSet(),
    coveredLeafIds: Set<String> = emptySet(),
    focusedLearnMinutesByNode: Map<String, Int> = emptyMap(),
    preservedBlocks: List<StudyPlanBlock> = emptyList(),
    weekdayTargetMinutes: Int = Int.MAX_VALUE,
    weekendTargetMinutes: Int = Int.MAX_VALUE,
    horizonDays: Int = DEFAULT_PLANNING_HORIZON_DAYS,
    preferredSessionMinutes: Int = DEFAULT_STUDY_SESSION_MINUTES,
    breakMinutes: Int = DEFAULT_STUDY_BREAK_MINUTES,
    planningOrder: PlanningOrder = PlanningOrder.DEFAULT,
    nowMs: Long,
): RollingPlanResult {
    if (horizonDays <= 0 || startDate > targetDate) {
        return RollingPlanResult(emptyList(), emptyList(), 0)
    }

    val plannedMinutesByNode = preservedBlocks
        .filter { it.status == PlanBlockStatus.PLANNED }
        .mapNotNull { block -> block.nodeId?.let { it to block.plannedMinutes } }
        .groupingBy { it.first }
        .fold(0) { total, entry -> total + entry.second }

    val work = pack.subjects.flatMap { subject ->
        subject.nodes.flatMap(SyllabusNode::flatten)
            .filter { it.children.isEmpty() }
            .mapNotNull { leaf ->
                if (leaf.id !in includedLeafIds || leaf.id in coveredLeafIds) return@mapNotNull null
                val estimate = leaf.estimatedMinutes?.coerceAtLeast(0) ?: return@mapNotNull null
                val accountedFor = focusedLearnMinutesByNode[leaf.id].orZero() + plannedMinutesByNode[leaf.id].orZero()
                val remaining = (estimate - accountedFor).coerceAtLeast(0)
                RemainingWork(leaf.id, subject.id, estimate, remaining).takeIf { it.remainingMinutes > 0 }
            }
    }
    if (work.isEmpty()) return RollingPlanResult(emptyList(), emptyList(), 0)

    val mutableRemaining = work.associate { it.nodeId to it.remainingMinutes }.toMutableMap()
    val orderedLeaves = when (planningOrder) {
        PlanningOrder.DEFAULT -> interleaveSubjects(work)
        PlanningOrder.SHORTEST_FIRST -> work.sortedBy { it.remainingMinutes }
    }
    val blocks = mutableListOf<StudyPlanBlock>()
    val endDate = minOf(targetDate, startDate.plusDays((horizonDays - 1).toLong()))
    var leafCursor = 0

    generateSequence(startDate) { it.plusDays(1) }
        .takeWhile { it <= endDate }
        .forEach { date ->
            val dailyTarget = if (date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                weekendTargetMinutes
            } else {
                weekdayTargetMinutes
            }.coerceAtLeast(0)
            val preservedStudyMinutes = preservedBlocks
                .filter {
                    it.scheduledDate == date &&
                        it.status == PlanBlockStatus.PLANNED &&
                        it.activityType !in setOf(StudyActivityType.CLASS, StudyActivityType.BREAK)
                }
                .sumOf { it.plannedMinutes }
            var dailyRemaining = (dailyTarget - preservedStudyMinutes).coerceAtLeast(0)
            val occupied = preservedBlocks
                .filter { it.scheduledDate == date && it.status !in CLOSED_PLAN_STATUSES }
                .map { it.startMinuteOfDay until (it.startMinuteOfDay + it.plannedMinutes) }
            val freeWindows = subtractOccupied(
                windows = effectiveWindowsFor(date, weeklyAvailability, availabilityOverrides),
                occupied = occupied,
            )
            var blockIndex = 0
            freeWindows.forEach { (windowStart, windowEnd) ->
                var cursor = windowStart
                while (cursor < windowEnd && dailyRemaining > 0) {
                    val next = nextRemaining(orderedLeaves, mutableRemaining, leafCursor) ?: break
                    leafCursor = next.second + 1
                    val leaf = next.first
                    val remaining = mutableRemaining.getValue(leaf.nodeId)
                    val available = windowEnd - cursor
                    val duration = minOf(preferredSessionMinutes.coerceAtLeast(1), remaining, available, dailyRemaining)
                    if (duration <= 0) break

                    blocks += StudyPlanBlock(
                        id = deterministicAutoBlockId(attemptId, date, cursor, blockIndex, leaf.nodeId),
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
                    mutableRemaining[leaf.nodeId] = remaining - duration
                    dailyRemaining -= duration
                    blockIndex++
                    cursor += duration
                    if (cursor < windowEnd) cursor += breakMinutes.coerceAtLeast(0)
                }
            }
        }

    val remainingWork = work.map { item ->
        item.copy(remainingMinutes = mutableRemaining[item.nodeId].orZero())
    }.filter { it.remainingMinutes > 0 }
    return RollingPlanResult(
        blocks = blocks,
        remainingWork = remainingWork,
        unscheduledMinutes = remainingWork.sumOf { it.remainingMinutes },
    )
}

private val CLOSED_PLAN_STATUSES = setOf(
    PlanBlockStatus.SKIPPED,
    PlanBlockStatus.RESCHEDULED,
)

private fun Int?.orZero(): Int = this ?: 0

private fun interleaveSubjects(work: List<RemainingWork>): List<RemainingWork> {
    val bySubject = work.groupBy { it.subjectId }.mapValues { (_, leaves) -> leaves.toMutableList() }
    return buildList {
        while (bySubject.values.any { it.isNotEmpty() }) {
            bySubject.keys.forEach { subjectId ->
                bySubject.getValue(subjectId).removeFirstOrNull()?.let(::add)
            }
        }
    }
}

private fun nextRemaining(
    ordered: List<RemainingWork>,
    remaining: Map<String, Int>,
    cursor: Int,
): Pair<RemainingWork, Int>? {
    if (ordered.isEmpty()) return null
    repeat(ordered.size) { offset ->
        val index = (cursor + offset).mod(ordered.size)
        val candidate = ordered[index]
        if (remaining[candidate.nodeId].orZero() > 0) return candidate to index
    }
    return null
}

internal fun subtractOccupied(
    windows: List<Pair<Int, Int>>,
    occupied: List<IntRange>,
): List<Pair<Int, Int>> {
    val mergedOccupied = occupied
        .map { it.first to (it.last + 1) }
        .filter { it.second > it.first }
        .sortedBy { it.first }
        .fold(mutableListOf<Pair<Int, Int>>()) { merged, interval ->
            val last = merged.lastOrNull()
            if (last == null || interval.first > last.second) merged += interval
            else merged[merged.lastIndex] = last.first to maxOf(last.second, interval.second)
            merged
        }

    return windows.sortedBy { it.first }.flatMap { (start, end) ->
        if (end <= start) return@flatMap emptyList()
        val result = mutableListOf<Pair<Int, Int>>()
        var cursor = start
        mergedOccupied.forEach { (busyStart, busyEnd) ->
            if (busyEnd <= cursor || busyStart >= end) return@forEach
            if (busyStart > cursor) result += cursor to minOf(busyStart, end)
            cursor = maxOf(cursor, busyEnd)
        }
        if (cursor < end) result += cursor to end
        result
    }
}

private fun deterministicAutoBlockId(
    attemptId: String,
    date: LocalDate,
    startMinute: Int,
    blockIndex: Int,
    nodeId: String,
): String = "auto_${attemptId}_${date}_${startMinute}_${blockIndex}_${nodeId.hashCode().toUInt().toString(16)}"
