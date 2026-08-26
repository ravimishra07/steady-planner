package com.exam.assistant.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val REVISION_INTERVAL_DAYS = 3

data class StudySessionRecord(
    val id: String,
    val date: LocalDate,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val nodeKey: String,
    val title: String,
    val sectionName: String,
    val subjectId: String,
    val isRevision: Boolean = false,
    val completed: Boolean = false,
    /** Wall-clock end for an in-progress sprint. */
    val runningEndsAtMs: Long? = null,
)

fun formatMinuteOfDay(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    return "%02d:%02d".format(h, m)
}

fun currentMinuteOfDay(): Int {
    val now = java.time.LocalTime.now()
    return now.hour * 60 + now.minute
}

fun StudySessionRecord.toTodayBlock(): TodayBlock = TodayBlock(
    id = id,
    time = formatMinuteOfDay(startMinuteOfDay),
    title = title,
    subtitle = if (isRevision) "Revision · $sectionName" else sectionName,
    tag = if (isRevision) BlockTag.REVISE else BlockTag.READ,
    subjectId = subjectId,
    minutes = durationMinutes,
    completed = completed,
    scheduled = !completed && runningEndsAtMs == null,
)

fun sessionsToTimeline(sessions: List<StudySessionRecord>): List<TimelineItem> {
    val blocks = sessions
        .sortedBy { it.startMinuteOfDay }
        .map { it.toTodayBlock() }
    return buildTimeline(blocks)
}

data class RevisionSuggestion(
    val nodeKey: String,
    val title: String,
    val sectionName: String,
    val subjectId: String,
    val studiedOn: LocalDate,
)

fun revisionSuggestions(
    allSessions: List<StudySessionRecord>,
    today: LocalDate = LocalDate.now(),
    intervalDays: Int = REVISION_INTERVAL_DAYS,
): List<RevisionSuggestion> {
    val studiedOn = today.minusDays(intervalDays.toLong())
    val todaySessions = allSessions.filter { it.date == today }
    val alreadyRevisedToday = todaySessions
        .filter { it.isRevision }
        .map { it.nodeKey }
        .toSet()
    return allSessions
        .filter { session ->
            session.date == studiedOn &&
                session.completed &&
                !session.isRevision &&
                session.nodeKey !in alreadyRevisedToday
        }
        .distinctBy { it.nodeKey }
        .map { session ->
            RevisionSuggestion(
                nodeKey = session.nodeKey,
                title = session.title,
                sectionName = session.sectionName,
                subjectId = session.subjectId,
                studiedOn = studiedOn,
            )
        }
}

fun sectionSubjectId(sectionIndex: Int): String = when (sectionIndex) {
    0 -> "quant"
    1 -> "reasoning"
    2 -> "ga"
    3 -> "english"
    else -> "quant"
}

fun nodeKey(sectionIndex: Int, pathIndices: List<Int>, tierPrefix: String = "t1"): String {
    val tail = pathIndices.joinToString("_")
    return "${tierPrefix}_${sectionIndex}_$tail"
}

fun findTopicNode(sections: List<SyllabusSection>, nodeKey: String): SyllabusTopicNode? {
    if (!nodeKey.startsWith("t1_")) return null
    val indices = nodeKey.removePrefix("t1_").split("_").mapNotNull { it.toIntOrNull() }
    if (indices.size < 2) return null
    val section = sections.getOrNull(indices[0]) ?: return null
    var node = section.topics.getOrNull(indices[1]) ?: return null
    for (i in 2 until indices.size) {
        node = node.children.getOrNull(indices[i]) ?: return null
    }
    return node
}

fun leafKeysForNodeKey(sections: List<SyllabusSection>, nodeKey: String): List<String> {
    val node = findTopicNode(sections, nodeKey) ?: return emptyList()
    return leafKeys(node, nodeKey)
}

private val dateStoreFormat = DateTimeFormatter.ISO_LOCAL_DATE

fun LocalDate.toStoreString(): String = format(dateStoreFormat)

fun weekStatusForDay(
    date: LocalDate,
    today: LocalDate,
    completedMinutes: Int,
    budgetHours: Int,
): WeekDayStatus {
    val offset = java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()
    if (offset == 0) return WeekDayStatus.TODAY
    if (offset > 0) return if (offset == 1) WeekDayStatus.PLANNED else WeekDayStatus.REST
    val budgetMins = budgetHours * 60
    if (completedMinutes <= 0) return WeekDayStatus.REST
    if (budgetMins > 0 && completedMinutes >= budgetMins) return WeekDayStatus.DONE
    return WeekDayStatus.PARTIAL
}

fun parseStoreDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value, dateStoreFormat) }.getOrNull()
