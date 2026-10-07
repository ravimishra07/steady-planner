package com.exam.assistant.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "What did I study" (PRODUCTION_V1_PLAN §5.6): one calendar day's sessions with chapter, type,
 * focused time and clock time. Only sessions whose focused time counts ([countsAsStudy]) are
 * listed, so the day total matches the heatmap and Today.
 */
data class DayLog(
    val date: LocalDate,
    val entries: List<DaySessionEntry>,
    /** [studiedMinutes] for the day: the same number the heatmap shows. */
    val totalFocusedMinutes: Int,
)

/** One listed session. Kept flat and id-keyed so the list can gain edit and delete later. */
data class DaySessionEntry(
    val sessionId: String,
    /** The chapter's display title (renames applied by the pack), or a custom block's title. Null when neither exists. */
    val title: String?,
    /** The studied topic when it sits below the chapter; null when it is the chapter itself. */
    val topicTitle: String?,
    val subjectName: String?,
    val activityType: StudyActivityType,
    val focusedMinutes: Int,
    /** Local clock minute the session started, in the zone it was recorded in. */
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int?,
)

/**
 * Builds [date]'s log. [pack] must already carry the student's chapter renames
 * ([withChapterTitles]); [customTitles] maps a plan block id to its custom title for sessions
 * that have no syllabus node.
 */
fun dayLog(
    date: LocalDate,
    sessions: List<StudySession>,
    pack: ExamPack?,
    customTitles: Map<String, String> = emptyMap(),
): DayLog {
    val day = sessions.filter { it.studyDate == date && it.countsAsStudy() }.sortedBy { it.startedAtEpochMs }
    val entries = day.map { session ->
        val zone = runCatching { ZoneId.of(session.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val node = session.nodeId?.let { pack?.findNode(it) }
        val chapter = session.nodeId?.let { pack?.chapterOf(it) }
        DaySessionEntry(
            sessionId = session.id,
            title = chapter?.title ?: node?.title ?: session.planBlockId?.let { customTitles[it] },
            topicTitle = node?.title?.takeIf { chapter != null && node.id != chapter.id },
            subjectName = session.nodeId?.let { pack?.findSubjectOf(it)?.name },
            activityType = session.activityType,
            focusedMinutes = focusedMinutes(session),
            startMinuteOfDay = minuteOfDay(session.startedAtEpochMs, zone),
            endMinuteOfDay = session.endedAtEpochMs?.let { minuteOfDay(it, zone) },
        )
    }
    return DayLog(date = date, entries = entries, totalFocusedMinutes = studiedMinutes(day, date, date))
}

private fun minuteOfDay(epochMs: Long, zone: ZoneId): Int {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
    return time.hour * 60 + time.minute
}
