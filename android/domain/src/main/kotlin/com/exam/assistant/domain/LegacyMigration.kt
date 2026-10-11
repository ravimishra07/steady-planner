package com.exam.assistant.domain

import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure functions for the one-time legacy-DataStore -> Room migration.
 * Everything here is deterministic given its inputs — same legacy record in,
 * same new-model record out, every time — so the caller (a repository, in
 * core:data) can safely upsert the results even if migration runs twice.
 */

/**
 * Old positional key ("t1_0_3_2") -> new stable [SyllabusNode.id], built by
 * walking the legacy tree and the new [ExamPack] in lockstep — both were
 * parsed from the same tier1 array in the same order, so position still
 * lines up even though the new tree additionally carries permanent ids.
 */
fun buildLegacyNodeIdMap(examPack: ExamPack, legacySections: List<SyllabusSection>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    examPack.subjects.forEachIndexed { sectionIndex, subject ->
        val legacySection = legacySections.getOrNull(sectionIndex) ?: return@forEachIndexed
        subject.nodes.forEachIndexed { topicIndex, node ->
            val legacyTopic = legacySection.topics.getOrNull(topicIndex) ?: return@forEachIndexed
            walkLegacyPair(node, legacyTopic, "t1_${sectionIndex}_$topicIndex", map)
        }
    }
    return map
}

private fun walkLegacyPair(newNode: SyllabusNode, oldNode: SyllabusTopicNode, oldKey: String, map: MutableMap<String, String>) {
    map[oldKey] = newNode.id
    newNode.children.forEachIndexed { index, child ->
        val oldChild = oldNode.children.getOrNull(index) ?: return@forEachIndexed
        walkLegacyPair(child, oldChild, "${oldKey}_$index", map)
    }
}

/** "section_$index" (the legacy excludedSectionKeys format) -> the subject's stable id. */
fun buildLegacySectionIdMap(examPack: ExamPack): Map<String, String> =
    examPack.subjects.mapIndexed { index, subject -> "section_$index" to subject.id }.toMap()

const val LEGACY_ATTEMPT_ID = "legacy_attempt"

/**
 * SavedPlan -> ExamAttempt. Best-effort only: the legacy plan stored a
 * relative `daysUntilExam`, not an absolute creation date, so the exact
 * historical target date cannot be reconstructed — this anchors it to
 * [migrationDate] (today, when migration runs) plus that relative count.
 * Every new onboarding flow after this migration writes absolute dates
 * directly and never goes through this path.
 */
fun migrateExamAttempt(
    examId: String,
    daysUntilExam: Int,
    migrationDate: LocalDate,
    nowMs: Long,
): ExamAttempt {
    val targetDate = migrationDate.plusDays(daysUntilExam.toLong().coerceAtLeast(0))
    return ExamAttempt(
        id = LEGACY_ATTEMPT_ID,
        examId = examId,
        syllabusVersion = "", // filled in by the caller from the loaded ExamPack
        examDate = targetDate,
        targetCompletionDate = targetDate,
        createdAtEpochMs = nowMs,
        updatedAtEpochMs = nowMs,
        status = ExamAttemptStatus.ACTIVE,
    )
}

fun migrateStudyPreferences(attemptId: String, studyPlace: String?): StudyPreferences =
    StudyPreferences(attemptId = attemptId, defaultStudyPlace = studyPlace?.takeIf { it.isNotBlank() })

/** doneLeaves (old) -> TopicProgress rows (new), skipping any legacy key with no id mapping. */
fun migrateTopicProgress(attemptId: String, doneLeaves: Set<String>, nodeIdMap: Map<String, String>, nowMs: Long): List<TopicProgress> =
    doneLeaves.mapNotNull { legacyKey ->
        val nodeId = nodeIdMap[legacyKey] ?: return@mapNotNull null
        TopicProgress(attemptId, nodeId, TopicProgressStatus.COVERED, coveredAtEpochMs = nowMs, updatedAtEpochMs = nowMs)
    }

/** excludedSectionKeys (old) -> TargetNodeOverride rows (new). */
fun migrateTargetOverrides(attemptId: String, excludedSectionKeys: Set<String>, sectionIdMap: Map<String, String>, nowMs: Long): List<TargetNodeOverride> =
    excludedSectionKeys.mapNotNull { legacyKey ->
        val nodeId = sectionIdMap[legacyKey] ?: return@mapNotNull null
        TargetNodeOverride(attemptId, nodeId, TargetNodeState.EXCLUDED, nowMs)
    }

/** One legacy [StudySessionRecord] split into its plan-intent and (if completed) actual-history parts. */
data class LegacyRecordMigration(
    val planBlock: StudyPlanBlock,
    val session: StudySession?,
)

fun migrateStudySessionRecord(
    record: StudySessionRecord,
    attemptId: String,
    nodeIdMap: Map<String, String>,
    nowMs: Long,
): LegacyRecordMigration {
    val nodeId = nodeIdMap[record.nodeKey]
    val blockId = "legacy_plan_${record.id}"
    val activityType = if (record.isRevision) StudyActivityType.REVISION else StudyActivityType.LEARN
    val zone = ZoneId.systemDefault()
    val startedAtEpochMs = record.date.atStartOfDay(zone).toInstant().toEpochMilli() + record.startMinuteOfDay * 60_000L

    val isCurrentlyRunning = record.runningEndsAtMs != null
    val status = when {
        record.completed -> PlanBlockStatus.COMPLETED
        else -> PlanBlockStatus.PLANNED
    }

    val block = StudyPlanBlock(
        id = blockId,
        attemptId = attemptId,
        nodeId = nodeId,
        subjectId = record.subjectId,
        customTitle = record.title,
        activityType = activityType,
        scheduledDate = record.date,
        startMinuteOfDay = record.startMinuteOfDay,
        plannedMinutes = record.durationMinutes,
        status = status,
        source = PlanBlockSource.MANUAL,
        rescheduledFromId = null,
        replacedById = null,
        createdAtEpochMs = nowMs,
        updatedAtEpochMs = nowMs,
    )

    val session = when {
        record.completed -> StudySession(
            id = "legacy_session_${record.id}",
            attemptId = attemptId,
            nodeId = nodeId,
            subjectId = record.subjectId,
            planBlockId = blockId,
            activityType = activityType,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = startedAtEpochMs + record.durationMinutes * 60_000L,
            studyDate = record.date,
            timeZoneId = zone.id,
            focusedSeconds = record.durationMinutes * 60,
            pausedSeconds = 0,
            status = StudySessionStatus.COMPLETED,
            focusLockUsed = false,
            interruptionCount = 0,
            customTitle = record.title,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
        )
        isCurrentlyRunning -> StudySession(
            id = "legacy_session_${record.id}",
            attemptId = attemptId,
            nodeId = nodeId,
            subjectId = record.subjectId,
            planBlockId = blockId,
            activityType = activityType,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = null,
            studyDate = record.date,
            timeZoneId = zone.id,
            focusedSeconds = 0,
            pausedSeconds = 0,
            status = StudySessionStatus.RUNNING,
            focusLockUsed = false,
            interruptionCount = 0,
            customTitle = record.title,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
        )
        else -> null
    }

    return LegacyRecordMigration(planBlock = block, session = session)
}
