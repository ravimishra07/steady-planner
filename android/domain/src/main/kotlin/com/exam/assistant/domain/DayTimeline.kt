package com.exam.assistant.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Minutes a scheduled-but-unstarted revision occupies once auto-placed on the timeline. */
const val AUTO_REVISION_MINUTES = 30

const val DAY_TIMELINE_START = 6 * 60
const val DAY_TIMELINE_END = 23 * 60

data class SubtopicSlot(
    val title: String,
    val startMinuteOfDay: Int,
)

data class DayBlock(
    val id: String,
    val subjectId: String,
    val subjectLabel: String,
    val title: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val isRevision: Boolean,
    val completed: Boolean,
    val subtopics: List<SubtopicSlot>,
    val lastStudiedDaysAgo: Int? = null,
) {
    val durationMinutes: Int get() = endMinuteOfDay - startMinuteOfDay
}

/** A user-entered fixed obligation shown on Today and treated as unavailable time. */
data class FixedCommitmentBlock(
    val id: String,
    val kind: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val customLabel: String? = null,
)

sealed interface DayTimelineEntry {
    data class Study(val block: DayBlock) : DayTimelineEntry
    data class Fixed(val block: FixedCommitmentBlock) : DayTimelineEntry
    data class Gap(val startMinuteOfDay: Int, val endMinuteOfDay: Int) : DayTimelineEntry
    data class NowMarker(val minuteOfDay: Int) : DayTimelineEntry
}

private data class Slot(val start: Int, val end: Int)

private fun subtopicsFor(node: SyllabusTopicNode?, startMinuteOfDay: Int, endMinuteOfDay: Int): List<SubtopicSlot> {
    val children = node?.children.orEmpty()
    if (children.isEmpty()) return emptyList()
    val total = endMinuteOfDay - startMinuteOfDay
    val weights = children.map { child -> topicHours(child).takeIf { it > 0 } ?: 1.0 }
    val weightSum = weights.sum()
    var cursor = startMinuteOfDay
    return children.mapIndexed { index, child ->
        val slot = SubtopicSlot(title = child.name, startMinuteOfDay = cursor)
        val share = if (weightSum > 0) (weights[index] / weightSum * total).toInt() else total / children.size
        cursor = (cursor + share).coerceAtMost(endMinuteOfDay)
        slot
    }
}

private fun sessionToDayBlock(session: StudySessionRecord, sections: List<SyllabusSection>): DayBlock {
    val node = findTopicNode(sections, session.nodeKey)
    val start = session.startMinuteOfDay
    val end = start + session.durationMinutes
    return DayBlock(
        id = session.id,
        subjectId = session.subjectId,
        subjectLabel = session.sectionName,
        title = session.title,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        isRevision = session.isRevision,
        completed = session.completed,
        subtopics = if (session.isRevision) emptyList() else subtopicsFor(node, start, end),
    )
}

/**
 * The day as one continuous schedule: real sessions in their real slots, plus — for
 * today only — one auto-placed block per pending [pendingRevisions] item dropped into
 * the earliest free gap of at least [AUTO_REVISION_MINUTES]. A `NowMarker` is inserted
 * at [nowMinuteOfDay] when [date] is today.
 */
fun buildDayTimeline(
    sessions: List<StudySessionRecord>,
    sections: List<SyllabusSection>,
    pendingRevisions: List<RevisionSuggestion>,
    date: LocalDate,
    today: LocalDate,
    nowMinuteOfDay: Int,
    fixedCommitments: List<FixedCommitmentBlock> = emptyList(),
    dayStartMinute: Int = DAY_TIMELINE_START,
    dayEndMinute: Int = DAY_TIMELINE_END,
): List<DayTimelineEntry> {
    val isToday = date == today
    val realBlocks = sessions.sortedBy { it.startMinuteOfDay }.map { sessionToDayBlock(it, sections) }

    var dayStart = dayStartMinute
    var dayEnd = dayEndMinute
    realBlocks.forEach { block ->
        dayStart = minOf(dayStart, block.startMinuteOfDay)
        dayEnd = maxOf(dayEnd, block.endMinuteOfDay)
    }
    fixedCommitments.forEach { block ->
        dayStart = minOf(dayStart, block.startMinuteOfDay)
        dayEnd = maxOf(dayEnd, block.endMinuteOfDay)
    }
    if (isToday) dayEnd = maxOf(dayEnd, nowMinuteOfDay + 60)

    val gaps = mutableListOf<Slot>()
    var cursor = dayStart
    val occupied = buildList {
        realBlocks.forEach { add(Slot(it.startMinuteOfDay, it.endMinuteOfDay)) }
        fixedCommitments.forEach { add(Slot(it.startMinuteOfDay, it.endMinuteOfDay)) }
    }.sortedBy { it.start }
    occupied.forEach { slot ->
        if (slot.start > cursor) gaps += Slot(cursor, slot.start)
        cursor = maxOf(cursor, slot.end)
    }
    if (dayEnd > cursor) gaps += Slot(cursor, dayEnd)

    val autoBlocks = mutableListOf<DayBlock>()
    if (isToday && pendingRevisions.isNotEmpty()) {
        pendingRevisions.forEach { suggestion ->
            gaps.sortBy { it.start }
            val gapIndex = gaps.indexOfFirst { slot ->
                maxOf(slot.start, nowMinuteOfDay) + AUTO_REVISION_MINUTES <= slot.end
            }
            if (gapIndex >= 0) {
                val slot = gaps[gapIndex]
                val blockStart = maxOf(slot.start, nowMinuteOfDay)
                val blockEnd = blockStart + AUTO_REVISION_MINUTES
                val daysAgo = ChronoUnit.DAYS.between(suggestion.studiedOn, today).toInt()
                autoBlocks += DayBlock(
                    id = "auto-revision-${suggestion.nodeKey}",
                    subjectId = suggestion.subjectId,
                    subjectLabel = suggestion.sectionName,
                    title = suggestion.title,
                    startMinuteOfDay = blockStart,
                    endMinuteOfDay = blockEnd,
                    isRevision = true,
                    completed = false,
                    subtopics = emptyList(),
                    lastStudiedDaysAgo = daysAgo,
                )
                gaps.removeAt(gapIndex)
                if (blockStart > slot.start) gaps.add(gapIndex, Slot(slot.start, blockStart))
                if (blockEnd < slot.end) gaps.add(Slot(blockEnd, slot.end))
            }
        }
    }

    val allBlocks = (realBlocks + autoBlocks).sortedBy { it.startMinuteOfDay }
    val timelineGaps = gaps.filter { it.end > it.start }.sortedBy { it.start }

    val entries = buildList<DayTimelineEntry> {
        allBlocks.forEach { add(DayTimelineEntry.Study(it)) }
        fixedCommitments.forEach { add(DayTimelineEntry.Fixed(it)) }
        timelineGaps.forEach { add(DayTimelineEntry.Gap(it.start, it.end)) }
    }.sortedBy { entry ->
        when (entry) {
            is DayTimelineEntry.Study -> entry.block.startMinuteOfDay
            is DayTimelineEntry.Fixed -> entry.block.startMinuteOfDay
            is DayTimelineEntry.Gap -> entry.startMinuteOfDay
            is DayTimelineEntry.NowMarker -> entry.minuteOfDay
        }
    }

    if (!isToday) return entries

    // Insert NOW in true chronological order — splitting the gap it falls inside,
    // rather than shoving the marker in front of the whole (possibly hours-long) gap.
    val withNow = mutableListOf<DayTimelineEntry>()
    var markerPlaced = false
    for (entry in entries) {
        when (entry) {
            is DayTimelineEntry.Study -> {
                if (!markerPlaced && nowMinuteOfDay <= entry.block.startMinuteOfDay) {
                    withNow += DayTimelineEntry.NowMarker(nowMinuteOfDay)
                    markerPlaced = true
                }
                withNow += entry
            }
            is DayTimelineEntry.Fixed -> {
                if (!markerPlaced && nowMinuteOfDay <= entry.block.startMinuteOfDay) {
                    withNow += DayTimelineEntry.NowMarker(nowMinuteOfDay)
                    markerPlaced = true
                }
                withNow += entry
            }
            is DayTimelineEntry.Gap -> {
                if (!markerPlaced && nowMinuteOfDay in entry.startMinuteOfDay until entry.endMinuteOfDay) {
                    if (nowMinuteOfDay > entry.startMinuteOfDay) {
                        withNow += DayTimelineEntry.Gap(entry.startMinuteOfDay, nowMinuteOfDay)
                    }
                    withNow += DayTimelineEntry.NowMarker(nowMinuteOfDay)
                    markerPlaced = true
                    if (nowMinuteOfDay < entry.endMinuteOfDay) {
                        withNow += DayTimelineEntry.Gap(nowMinuteOfDay, entry.endMinuteOfDay)
                    }
                } else {
                    if (!markerPlaced && nowMinuteOfDay <= entry.startMinuteOfDay) {
                        withNow += DayTimelineEntry.NowMarker(nowMinuteOfDay)
                        markerPlaced = true
                    }
                    withNow += entry
                }
            }
            is DayTimelineEntry.NowMarker -> Unit
        }
    }
    if (!markerPlaced) withNow += DayTimelineEntry.NowMarker(nowMinuteOfDay)

    return withNow
}

/**
 * First free slot on [date] at or after [notBefore] long enough for [minMinutes],
 * or `null` if the day has no room left.
 */
fun findNextFreeSlot(
    sessions: List<StudySessionRecord>,
    date: LocalDate,
    minMinutes: Int,
    notBefore: Int,
    fixedCommitments: List<FixedCommitmentBlock> = emptyList(),
    dayStartMinute: Int = DAY_TIMELINE_START,
    dayEndMinute: Int = DAY_TIMELINE_END,
): Int? {
    val occupied = buildList {
        sessions.filter { it.date == date && !it.completed }.forEach {
            add(Slot(it.startMinuteOfDay, it.startMinuteOfDay + it.durationMinutes))
        }
        fixedCommitments.forEach { add(Slot(it.startMinuteOfDay, it.endMinuteOfDay)) }
    }.sortedBy { it.start }
    var cursor = notBefore.coerceAtLeast(dayStartMinute)
    occupied.forEach { slot ->
        if (slot.start - cursor >= minMinutes) return cursor
        cursor = maxOf(cursor, slot.end)
    }
    return if (dayEndMinute - cursor >= minMinutes) cursor else null
}
