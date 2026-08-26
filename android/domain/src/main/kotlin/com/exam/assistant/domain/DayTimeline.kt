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

sealed interface DayTimelineEntry {
    data class Study(val block: DayBlock) : DayTimelineEntry
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
): List<DayTimelineEntry> {
    val isToday = date == today
    val realBlocks = sessions.sortedBy { it.startMinuteOfDay }.map { sessionToDayBlock(it, sections) }

    var dayStart = DAY_TIMELINE_START
    var dayEnd = DAY_TIMELINE_END
    realBlocks.forEach { block ->
        dayStart = minOf(dayStart, block.startMinuteOfDay)
        dayEnd = maxOf(dayEnd, block.endMinuteOfDay)
    }
    if (isToday) dayEnd = maxOf(dayEnd, nowMinuteOfDay + 60)

    val gaps = mutableListOf<Slot>()
    var cursor = dayStart
    realBlocks.forEach { block ->
        if (block.startMinuteOfDay > cursor) gaps += Slot(cursor, block.startMinuteOfDay)
        cursor = maxOf(cursor, block.endMinuteOfDay)
    }
    if (dayEnd > cursor) gaps += Slot(cursor, dayEnd)

    val autoBlocks = mutableListOf<DayBlock>()
    if (isToday && pendingRevisions.isNotEmpty()) {
        pendingRevisions.forEach { suggestion ->
            val gapIndex = gaps.indexOfFirst { it.end - it.start >= AUTO_REVISION_MINUTES }
            if (gapIndex >= 0) {
                val slot = gaps[gapIndex]
                val earliestStart = maxOf(slot.start, nowMinuteOfDay)
                val blockStart = if (earliestStart + AUTO_REVISION_MINUTES > slot.end) slot.start else earliestStart
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

    val entries = mutableListOf<DayTimelineEntry>()
    var bi = 0
    var gi = 0
    while (bi < allBlocks.size || gi < timelineGaps.size) {
        val nextBlock = allBlocks.getOrNull(bi)
        val nextGap = timelineGaps.getOrNull(gi)
        if (nextBlock != null && (nextGap == null || nextBlock.startMinuteOfDay <= nextGap.start)) {
            entries += DayTimelineEntry.Study(nextBlock)
            bi++
        } else if (nextGap != null) {
            entries += DayTimelineEntry.Gap(nextGap.start, nextGap.end)
            gi++
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
): Int? {
    val daySessions = sessions.filter { it.date == date && !it.completed }.sortedBy { it.startMinuteOfDay }
    var cursor = notBefore.coerceAtLeast(DAY_TIMELINE_START)
    daySessions.forEach { session ->
        if (session.startMinuteOfDay - cursor >= minMinutes) return cursor
        cursor = maxOf(cursor, session.startMinuteOfDay + session.durationMinutes)
    }
    return if (DAY_TIMELINE_END - cursor >= minMinutes) cursor else null
}
