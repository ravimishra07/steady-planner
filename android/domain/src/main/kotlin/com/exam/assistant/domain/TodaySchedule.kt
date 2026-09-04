package com.exam.assistant.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

enum class BlockTag {
    READ,
    PRACTICE,
    REVISE,
}

data class TodayBlock(
    val id: String,
    val time: String,
    val title: String = "",
    val subtitle: String = "",
    val tag: BlockTag? = null,
    val subjectId: String? = null,
    val minutes: Int = 0,
    val isBreak: Boolean = false,
    val completed: Boolean = false,
    val scheduled: Boolean = false,
)

enum class WeekDayStatus {
    DONE,
    PARTIAL,
    TODAY,
    PLANNED,
    REST,
}

sealed interface TimelineItem {
    data class Gap(val minutes: Int) : TimelineItem
    data class Entry(val block: TodayBlock) : TimelineItem
}

/** Fixed NEET demo schedule used by domain tests. */
fun demoTodayBlocks(): List<TodayBlock> = listOf(
    TodayBlock(
        id = "0",
        time = "06:30",
        title = "Physics — Motion in a Straight Line",
        subtitle = "NCERT chapter · 90 min",
        tag = BlockTag.READ,
        subjectId = "physics",
        minutes = 90,
    ),
    TodayBlock(
        id = "break1",
        time = "08:00",
        isBreak = true,
        minutes = 15,
    ),
    TodayBlock(
        id = "2",
        time = "08:15",
        title = "Physics — 40 practice questions",
        subtitle = "NEET question set · 75 min",
        tag = BlockTag.PRACTICE,
        subjectId = "physics",
        minutes = 75,
    ),
    TodayBlock(
        id = "3",
        time = "14:00",
        title = "Revision: Some Basic Concepts of Chemistry",
        subtitle = "Done 6 days ago · 30 min",
        tag = BlockTag.REVISE,
        subjectId = "chemistry",
        minutes = 30,
    ),
    TodayBlock(
        id = "4",
        time = "15:30",
        title = "Cell: The Unit of Life",
        subtitle = "NCERT review · 30 min",
        tag = BlockTag.READ,
        subjectId = "botany",
        minutes = 30,
    ),
    TodayBlock(
        id = "5",
        time = "18:30",
        title = "Animal Kingdom — Practice",
        subtitle = "NEET question set · 45 min",
        tag = BlockTag.PRACTICE,
        subjectId = "zoology",
        minutes = 45,
    ),
)

fun todayBudget(weekdayHours: Float, weekendHours: Float, date: LocalDate = LocalDate.now()): Int {
    val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    return if (weekend) weekendHours.toInt() else weekdayHours.toInt()
}

/** Exact capacity for scheduling and progress; preserves half-hour targets such as 4.5h. */
fun todayBudgetMinutes(weekdayHours: Float, weekendHours: Float, date: LocalDate = LocalDate.now()): Int {
    val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    return (((if (weekend) weekendHours else weekdayHours) * 60f).roundToInt()).coerceAtLeast(0)
}

fun weekAround(anchor: LocalDate): List<LocalDate> {
    val start = anchor.minusDays((anchor.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return (0 until 7).map { start.plusDays(it.toLong()) }
}

fun dayStatus(offsetFromToday: Int): WeekDayStatus = when {
    offsetFromToday < 0 -> if (offsetFromToday == -1) WeekDayStatus.PARTIAL else WeekDayStatus.DONE
    offsetFromToday == 0 -> WeekDayStatus.TODAY
    offsetFromToday == 1 -> WeekDayStatus.PLANNED
    else -> WeekDayStatus.REST
}

fun parseTimeMinutes(time: String): Int {
    val parts = time.split(":")
    return parts[0].toInt() * 60 + parts[1].toInt()
}

fun formatGap(minutes: Int): String {
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}h" else "${hours}h ${remainder}m"
}

fun blockIsDone(blocksDone: Set<String>, id: String): Boolean = id in blocksDone

fun doneMinutes(blocks: List<TodayBlock>, blocksDone: Set<String>): Int =
    blocks
        .filter { !it.isBreak && blockIsDone(blocksDone, it.id) }
        .sumOf { it.minutes }

fun filterBlocks(blocks: List<TodayBlock>, activeSubjects: Set<String>): List<TodayBlock> =
    blocks.filter { block ->
        block.isBreak || block.subjectId in activeSubjects
    }

fun buildTimeline(blocks: List<TodayBlock>): List<TimelineItem> {
    val items = mutableListOf<TimelineItem>()
    var previousEnd: Int? = null
    blocks.forEach { block ->
        if (!block.isBreak && previousEnd != null) {
            val gap = parseTimeMinutes(block.time) - previousEnd
            if (gap >= 45) items += TimelineItem.Gap(gap)
        }
        items += TimelineItem.Entry(block)
        if (!block.isBreak) {
            previousEnd = parseTimeMinutes(block.time) + block.minutes
        }
    }
    return items
}
