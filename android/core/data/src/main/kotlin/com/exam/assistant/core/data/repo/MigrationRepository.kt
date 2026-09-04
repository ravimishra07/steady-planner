package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.core.data.decodedCommitments
import com.exam.assistant.domain.ExamAttempt
import com.exam.assistant.domain.ExamAttemptStatus
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.NEET_EXAM_ID
import com.exam.assistant.domain.PlanBlockSource
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudyPlanBlock
import com.exam.assistant.domain.StudyPreferences
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.StudySessionSegment
import com.exam.assistant.domain.StudySessionStatus
import com.exam.assistant.domain.TargetNodeOverride
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.TopicProgress
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.WeeklyAvailability
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.findSubjectOf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.withContext

/**
 * Imports legacy DataStore state into the normalized Room model without ever
 * deleting a valid legacy plan merely because Room is empty. Legacy stores are
 * left intact as a rollback source; production features no longer write them.
 */
class MigrationRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
    private val planStore: PlanStore,
    private val studySessionStore: StudySessionStore,
    private val syllabusStore: SyllabusStore,
    private val migrationStore: MigrationStore,
    private val attemptRepository: AttemptRepository,
    private val examPackRepository: ExamPackRepository,
) {
    private val db = PrepTrackerDatabase.get(context)

    companion object {
        const val CURRENT_MIGRATION_VERSION = 3
    }

    suspend fun migrateIfNeeded() = withContext(dispatchers.io) {
        if (migrationStore.currentVersion() >= CURRENT_MIGRATION_VERSION) return@withContext

        val legacyPlan = planStore.load()
        val activeAttempt = db.examAttemptDao().activeOnce()
        if (activeAttempt?.examId == NEET_EXAM_ID) {
            // A normalized attempt already exists. Import any still-unrepresented
            // legacy progress/history idempotently, but never replace Room truth.
            val pack = examPackRepository.examPackFor(NEET_EXAM_ID)
            importLegacyRows(activeAttempt.id, pack, includeAttemptSetup = false)
        } else if (activeAttempt == null && legacyPlan?.examId == NEET_EXAM_ID) {
            val pack = examPackRepository.examPackFor(NEET_EXAM_ID)
            val now = System.currentTimeMillis()
            val targetDate = legacyPlan.targetDateEpochDay?.let(LocalDate::ofEpochDay)
                ?: LocalDate.now().plusDays(legacyPlan.daysUntilExam.coerceAtLeast(1).toLong())
            val attemptId = UUID.randomUUID().toString()
            db.withTransaction {
                db.examAttemptDao().upsert(
                    ExamAttempt(
                        id = attemptId,
                        examId = NEET_EXAM_ID,
                        syllabusVersion = pack.syllabusVersion,
                        examDate = targetDate,
                        targetCompletionDate = targetDate,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        status = ExamAttemptStatus.ACTIVE,
                    ).toEntity(),
                )
                db.studyPreferenceDao().upsert(
                    StudyPreferences(
                        attemptId = attemptId,
                        weekdayTargetMinutes = (legacyPlan.weekdayHours * 60).toInt(),
                        weekendTargetMinutes = (legacyPlan.weekendHours * 60).toInt(),
                        autoScheduleRevision = true,
                        defaultStudyPlace = legacyPlan.studyPlace.ifBlank { null },
                    ).toEntity(),
                )
                db.availabilityDao().upsertWeekly(legacyAvailability(attemptId, legacyPlan).map { it.toEntity() })
            }
            importLegacyRows(attemptId, pack, includeAttemptSetup = true)
        }

        // Unsupported old-exam data is deliberately not destroyed. It is
        // ignored by the NEET product boundary and remains recoverable.
        migrationStore.markComplete(CURRENT_MIGRATION_VERSION)
        attemptRepository.refreshHasAttemptFlag()
    }

    private suspend fun importLegacyRows(attemptId: String, pack: ExamPack, includeAttemptSetup: Boolean) {
        val sessions = studySessionStore.loadAll()
        val syllabus = syllabusStore.load()
        val now = System.currentTimeMillis()
        db.withTransaction {
            val progress = syllabus.doneLeaves.mapNotNull { legacyId ->
                stableNodeId(pack, legacyId)?.let { nodeId ->
                    TopicProgress(
                        attemptId = attemptId,
                        nodeId = nodeId,
                        status = TopicProgressStatus.COVERED,
                        coveredAtEpochMs = now,
                        updatedAtEpochMs = now,
                    ).toEntity()
                }
            }
            if (progress.isNotEmpty()) db.topicProgressDao().upsertAll(progress)

            if (includeAttemptSetup) {
                syllabus.excludedSectionKeys.mapNotNull { key ->
                    val index = key.removePrefix("section_").toIntOrNull() ?: return@mapNotNull null
                    pack.subjects.getOrNull(index)
                }.flatMap { it.nodes }.forEach { node ->
                    db.targetNodeOverrideDao().upsert(
                        TargetNodeOverride(attemptId, node.id, TargetNodeState.EXCLUDED, now).toEntity(),
                    )
                }
            }

            sessions.forEach { legacy ->
                val nodeId = stableNodeId(pack, legacy.nodeKey)
                val subjectId = nodeId?.let(pack::findSubjectOf)?.id
                    ?: pack.subjects.firstOrNull { it.id == legacy.subjectId }?.id
                val existingBlock = db.studyPlanBlockDao().byId(legacy.id)
                if (existingBlock == null) {
                    db.studyPlanBlockDao().upsert(
                        StudyPlanBlock(
                            id = legacy.id,
                            attemptId = attemptId,
                            nodeId = nodeId,
                            subjectId = subjectId,
                            customTitle = if (nodeId == null) legacy.title else null,
                            activityType = if (legacy.isRevision) StudyActivityType.REVISION else StudyActivityType.LEARN,
                            scheduledDate = legacy.date,
                            startMinuteOfDay = legacy.startMinuteOfDay,
                            plannedMinutes = legacy.durationMinutes,
                            status = if (legacy.completed) PlanBlockStatus.COMPLETED else PlanBlockStatus.PLANNED,
                            source = if (legacy.id.startsWith("auto_")) PlanBlockSource.AUTO else PlanBlockSource.MANUAL,
                            rescheduledFromId = null,
                            replacedById = null,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now,
                        ).toEntity(),
                    )
                }

                if ((legacy.completed || legacy.runningEndsAtMs != null) && db.studySessionDao().byId(legacy.id) == null) {
                    val startedAt = legacy.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() +
                        legacy.startMinuteOfDay * 60_000L
                    val endedAt = if (legacy.completed) startedAt + legacy.durationMinutes * 60_000L else null
                    val status = if (legacy.completed) StudySessionStatus.COMPLETED else StudySessionStatus.RUNNING
                    db.studySessionDao().upsert(
                        StudySession(
                            id = legacy.id,
                            attemptId = attemptId,
                            nodeId = nodeId,
                            subjectId = subjectId,
                            planBlockId = legacy.id,
                            activityType = if (legacy.isRevision) StudyActivityType.REVISION else StudyActivityType.LEARN,
                            startedAtEpochMs = startedAt,
                            endedAtEpochMs = endedAt,
                            studyDate = legacy.date,
                            timeZoneId = ZoneId.systemDefault().id,
                            focusedSeconds = if (legacy.completed) legacy.durationMinutes * 60 else 0,
                            pausedSeconds = 0,
                            status = status,
                            focusLockUsed = false,
                            interruptionCount = 0,
                            customTitle = if (nodeId == null) legacy.title else null,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now,
                        ).toEntity(),
                    )
                    db.studySessionDao().upsertSegment(
                        StudySessionSegment(
                            id = "${legacy.id}_segment_0",
                            sessionId = legacy.id,
                            nodeId = nodeId,
                            subjectId = subjectId,
                            startedAtEpochMs = startedAt,
                            endedAtEpochMs = endedAt,
                            focusedSeconds = if (legacy.completed) legacy.durationMinutes * 60 else 0,
                            order = 0,
                        ).toEntity(),
                    )
                }
            }
        }
    }

    private fun stableNodeId(pack: ExamPack, legacyId: String): String? {
        if (pack.findNode(legacyId) != null) return legacyId
        if (!legacyId.startsWith("t1_")) return null
        val indices = legacyId.removePrefix("t1_").split('_').mapNotNull(String::toIntOrNull)
        if (indices.size < 2) return null
        var node = pack.subjects.getOrNull(indices.first())?.nodes?.getOrNull(indices[1]) ?: return null
        indices.drop(2).forEach { index -> node = node.children.getOrNull(index) ?: return null }
        return node.id
    }

    private fun legacyAvailability(attemptId: String, plan: com.exam.assistant.core.data.SavedPlan): List<WeeklyAvailability> {
        val commitments = plan.decodedCommitments()
        return DayOfWeek.entries.flatMap { day ->
            val legacyDay = day.value % 7
            val occupied = commitments
                .filter { legacyDay in it.days }
                .map { maxOf(plan.wakeMinute, it.startMinute) to minOf(plan.sleepMinute, it.endMinute) }
                .filter { it.second > it.first }
                .sortedBy { it.first }
                .fold(mutableListOf<Pair<Int, Int>>()) { merged, interval ->
                    val last = merged.lastOrNull()
                    if (last == null || interval.first > last.second) merged += interval
                    else merged[merged.lastIndex] = last.first to maxOf(last.second, interval.second)
                    merged
                }
            val free = mutableListOf<Pair<Int, Int>>()
            var cursor = plan.wakeMinute
            occupied.forEach { (start, end) ->
                if (start > cursor) free += cursor to start
                cursor = maxOf(cursor, end)
            }
            if (cursor < plan.sleepMinute) free += cursor to plan.sleepMinute
            free.mapIndexed { index, (start, end) ->
                WeeklyAvailability(
                    id = "migrated_${attemptId}_${day.name}_$index",
                    attemptId = attemptId,
                    dayOfWeek = day,
                    startMinuteOfDay = start,
                    endMinuteOfDay = end,
                )
            }
        }
    }
}
