package com.exam.assistant.domain

import java.time.LocalDate

/**
 * Past blocks the planner placed that the student never did (PRODUCTION_V1_PLAN §4.4,
 * "whole day missed"). A block counts when its day is before [today], it is on or
 * after [since], it is still PLANNED, the planner created it, it was not moved, and
 * no completed session is linked to it.
 */
fun missedPlannedBlocks(
    blocks: List<StudyPlanBlock>,
    sessions: List<StudySession>,
    today: LocalDate,
    since: LocalDate,
): List<StudyPlanBlock> {
    val doneBlockIds = sessions.asSequence().filter { it.countsAsStudy() }.mapNotNull { it.planBlockId }.toSet()
    return blocks.filter {
        it.scheduledDate >= since &&
            it.scheduledDate < today &&
            it.isRegenerable() &&
            it.activityType !in setOf(StudyActivityType.CLASS, StudyActivityType.BREAK) &&
            it.id !in doneBlockIds
    }
}

/**
 * Rebalance step one: the missed blocks as SKIPPED. Their unfinished work is not
 * dropped. The planner derives remaining work from focused time, so planning again
 * from today puts it back in the queue.
 */
fun skipMissedBlocks(missed: List<StudyPlanBlock>, nowMs: Long): List<StudyPlanBlock> =
    missed.map { it.copy(status = PlanBlockStatus.SKIPPED, updatedAtEpochMs = nowMs) }
