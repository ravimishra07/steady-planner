package com.exam.assistant.today

import com.exam.assistant.AppContainer
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.chapterOf
import com.exam.assistant.domain.daysToExam
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.findSubjectOf
import com.exam.assistant.domain.syllabusMetrics
import com.exam.assistant.domain.targetStates
import java.time.LocalDate
import kotlinx.coroutines.withContext

/** One planned block of today, named the way Today names it. */
data class TodayBlock(
    val id: String,
    val nodeId: String,
    /** Blank when the block has no chapter; callers fall back to a generic label. */
    val title: String,
    val subjectName: String,
    val startMinuteOfDay: Int,
    val plannedMinutes: Int,
    val isRevision: Boolean,
) {
    val endMinuteOfDay: Int get() = startMinuteOfDay + plannedMinutes
}

/**
 * What the reminder and the home-screen widgets show. Read from the same
 * repositories and the same Metrics functions as Today, so the numbers match.
 */
data class TodaySnapshot(
    val hasPlan: Boolean,
    val daysToExam: Int?,
    val coveragePercent: Int,
    /** Today's blocks still planned, in start order. */
    val blocks: List<TodayBlock>,
) {
    /** The first planned block that has not ended yet. */
    fun nextBlock(nowMinuteOfDay: Int): TodayBlock? = blocks.firstOrNull { it.endMinuteOfDay > nowMinuteOfDay }

    companion object {
        val Empty = TodaySnapshot(hasPlan = false, daysToExam = null, coveragePercent = 0, blocks = emptyList())
    }
}

/** Off the main thread; never throws — a widget or a reminder must not crash the process. */
suspend fun AppContainer.loadTodaySnapshot(today: LocalDate = LocalDate.now()): TodaySnapshot =
    withContext(dispatchers.io) {
        runCatching {
            val attempt = attemptRepository.activeAttempt() ?: return@runCatching TodaySnapshot.Empty
            val pack = examPackRepository.examPackFor(attempt.examId, attempt.id)
            val metrics = syllabusMetrics(
                pack = pack,
                overrides = targetStates(targetSyllabusRepository.overridesFor(attempt.id)),
                progress = topicProgressRepository.allOnce(attempt.id),
            )
            val blocks = planRepository.planForDate(attempt.id, today)
                .filter { it.status == PlanBlockStatus.PLANNED }
                .sortedBy { it.startMinuteOfDay }
                .map { block ->
                    val isRevision = block.activityType == StudyActivityType.REVISION
                    val node = block.nodeId?.let { pack.findNode(it) }
                    val chapter = if (isRevision) block.nodeId?.let { pack.chapterOf(it) } else null
                    TodayBlock(
                        id = block.id,
                        nodeId = block.nodeId.orEmpty(),
                        title = block.customTitle ?: chapter?.title ?: node?.title.orEmpty(),
                        subjectName = block.nodeId?.let { pack.findSubjectOf(it) }?.name.orEmpty(),
                        startMinuteOfDay = block.startMinuteOfDay,
                        plannedMinutes = block.plannedMinutes,
                        isRevision = isRevision,
                    )
                }
            TodaySnapshot(
                hasPlan = true,
                daysToExam = daysToExam(attempt, today),
                coveragePercent = metrics.overall.percent,
                blocks = blocks,
            )
        }.getOrDefault(TodaySnapshot.Empty)
    }
