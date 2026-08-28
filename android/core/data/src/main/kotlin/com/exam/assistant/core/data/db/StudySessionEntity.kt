package com.exam.assistant.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.PrimaryKey
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.StudySessionSegment
import com.exam.assistant.domain.StudySessionStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(
    tableName = "study_session",
    indices = [
        Index("attemptId"),
        Index("studyDateEpochDay"),
        Index("status"),
        Index("nodeId"),
        Index("subjectId"),
        Index("planBlockId"),
        Index("startedAtEpochMs"),
    ],
)
@TypeConverters(Converters::class)
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val attemptId: String,
    val nodeId: String?,
    val subjectId: String?,
    val planBlockId: String?,
    val activityType: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val studyDateEpochDay: Long,
    val timeZoneId: String,
    val focusedSeconds: Int,
    val pausedSeconds: Int,
    val status: String,
    val focusLockUsed: Boolean,
    val interruptionCount: Int,
    val customTitle: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

fun StudySessionEntity.toDomain() = StudySession(
    id = id,
    attemptId = attemptId,
    nodeId = nodeId,
    subjectId = subjectId,
    planBlockId = planBlockId,
    activityType = StudyActivityType.valueOf(activityType),
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    studyDate = LocalDate.ofEpochDay(studyDateEpochDay),
    timeZoneId = timeZoneId,
    focusedSeconds = focusedSeconds,
    pausedSeconds = pausedSeconds,
    status = StudySessionStatus.valueOf(status),
    focusLockUsed = focusLockUsed,
    interruptionCount = interruptionCount,
    customTitle = customTitle,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun StudySession.toEntity() = StudySessionEntity(
    id = id,
    attemptId = attemptId,
    nodeId = nodeId,
    subjectId = subjectId,
    planBlockId = planBlockId,
    activityType = activityType.name,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    studyDateEpochDay = studyDate.toEpochDay(),
    timeZoneId = timeZoneId,
    focusedSeconds = focusedSeconds,
    pausedSeconds = pausedSeconds,
    status = status.name,
    focusLockUsed = focusLockUsed,
    interruptionCount = interruptionCount,
    customTitle = customTitle,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

@Entity(
    tableName = "study_session_segment",
    indices = [Index("sessionId"), Index("nodeId"), Index("subjectId")],
)
data class StudySessionSegmentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val nodeId: String?,
    val subjectId: String?,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val focusedSeconds: Int,
    val order: Int,
)

fun StudySessionSegmentEntity.toDomain() = StudySessionSegment(
    id = id,
    sessionId = sessionId,
    nodeId = nodeId,
    subjectId = subjectId,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    focusedSeconds = focusedSeconds,
    order = order,
)

fun StudySessionSegment.toEntity() = StudySessionSegmentEntity(
    id = id,
    sessionId = sessionId,
    nodeId = nodeId,
    subjectId = subjectId,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    focusedSeconds = focusedSeconds,
    order = order,
)

data class DaySum(val studyDateEpochDay: Long, val totalSeconds: Int, val sessionCount: Int)
data class SubjectSum(val subjectId: String?, val totalSeconds: Int)

@Dao
interface StudySessionDao {
    @Upsert
    suspend fun upsert(entity: StudySessionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<StudySessionEntity>)

    @Query("SELECT * FROM study_session WHERE id = :id")
    suspend fun byId(id: String): StudySessionEntity?

    @Query("SELECT * FROM study_session WHERE attemptId = :attemptId AND studyDateEpochDay = :epochDay")
    suspend fun forDate(attemptId: String, epochDay: Long): List<StudySessionEntity>

    @Query("SELECT * FROM study_session WHERE attemptId = :attemptId AND studyDateEpochDay BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun between(attemptId: String, startEpochDay: Long, endEpochDay: Long): List<StudySessionEntity>

    @Query("SELECT * FROM study_session WHERE attemptId = :attemptId AND nodeId = :nodeId")
    suspend fun forNode(attemptId: String, nodeId: String): List<StudySessionEntity>

    @Query("SELECT * FROM study_session WHERE planBlockId = :planBlockId LIMIT 1")
    suspend fun forPlanBlock(planBlockId: String): StudySessionEntity?

    @Query("SELECT * FROM study_session WHERE attemptId = :attemptId AND status IN ('RUNNING','PAUSED') LIMIT 1")
    suspend fun activeSession(attemptId: String): StudySessionEntity?

    @Query(
        "SELECT studyDateEpochDay, SUM(focusedSeconds) AS totalSeconds, COUNT(*) AS sessionCount " +
            "FROM study_session WHERE attemptId = :attemptId AND status = 'COMPLETED' " +
            "AND studyDateEpochDay BETWEEN :startEpochDay AND :endEpochDay GROUP BY studyDateEpochDay",
    )
    suspend fun dailySums(attemptId: String, startEpochDay: Long, endEpochDay: Long): List<DaySum>

    @Query(
        "SELECT subjectId, SUM(focusedSeconds) AS totalSeconds FROM study_session " +
            "WHERE attemptId = :attemptId AND status = 'COMPLETED' " +
            "AND studyDateEpochDay BETWEEN :startEpochDay AND :endEpochDay GROUP BY subjectId",
    )
    suspend fun subjectSums(attemptId: String, startEpochDay: Long, endEpochDay: Long): List<SubjectSum>

    @Query("SELECT MIN(studyDateEpochDay) FROM study_session WHERE attemptId = :attemptId AND status = 'COMPLETED'")
    suspend fun earliestStudyDate(attemptId: String): Long?

    @Query("DELETE FROM study_session WHERE attemptId = :attemptId")
    suspend fun deleteForAttempt(attemptId: String)

    @Upsert
    suspend fun upsertSegment(entity: StudySessionSegmentEntity)

    @Query("SELECT * FROM study_session_segment WHERE sessionId = :sessionId ORDER BY `order`")
    suspend fun segmentsFor(sessionId: String): List<StudySessionSegmentEntity>

    @Query("SELECT * FROM study_session_segment WHERE sessionId = :sessionId AND endedAtEpochMs IS NULL LIMIT 1")
    suspend fun openSegment(sessionId: String): StudySessionSegmentEntity?

    @Query(
        "DELETE FROM study_session_segment WHERE sessionId IN " +
            "(SELECT id FROM study_session WHERE attemptId = :attemptId)",
    )
    suspend fun deleteSegmentsForAttempt(attemptId: String)

    @Transaction
    suspend fun deleteAllForAttempt(attemptId: String) {
        deleteSegmentsForAttempt(attemptId)
        deleteForAttempt(attemptId)
    }
}
