package com.exam.assistant.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.exam.assistant.domain.RevisionState
import com.exam.assistant.domain.TopicProgress
import com.exam.assistant.domain.TopicProgressStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(
    tableName = "topic_progress",
    primaryKeys = ["attemptId", "nodeId"],
    indices = [Index("attemptId", "status")],
)
data class TopicProgressEntity(
    val attemptId: String,
    val nodeId: String,
    val status: String,
    val coveredAtEpochMs: Long?,
    val updatedAtEpochMs: Long,
)

fun TopicProgressEntity.toDomain() = TopicProgress(
    attemptId = attemptId,
    nodeId = nodeId,
    status = TopicProgressStatus.valueOf(status),
    coveredAtEpochMs = coveredAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun TopicProgress.toEntity() = TopicProgressEntity(
    attemptId = attemptId,
    nodeId = nodeId,
    status = status.name,
    coveredAtEpochMs = coveredAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

@Dao
interface TopicProgressDao {
    @Upsert
    suspend fun upsert(entity: TopicProgressEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TopicProgressEntity>)

    @Query("SELECT * FROM topic_progress WHERE attemptId = :attemptId")
    fun observeAll(attemptId: String): Flow<List<TopicProgressEntity>>

    @Query("SELECT * FROM topic_progress WHERE attemptId = :attemptId")
    suspend fun allOnce(attemptId: String): List<TopicProgressEntity>

    @Query("SELECT * FROM topic_progress WHERE attemptId = :attemptId AND nodeId = :nodeId")
    suspend fun byNode(attemptId: String, nodeId: String): TopicProgressEntity?

    @Query("DELETE FROM topic_progress WHERE attemptId = :attemptId")
    suspend fun deleteForAttempt(attemptId: String)
}

@Entity(
    tableName = "revision_state",
    primaryKeys = ["attemptId", "nodeId"],
    indices = [Index("nextDueDateEpochDay")],
)
@TypeConverters(Converters::class)
data class RevisionStateEntity(
    val attemptId: String,
    val nodeId: String,
    val enabled: Boolean,
    val lastReviewedAtEpochMs: Long?,
    val nextDueDateEpochDay: Long?,
    val revisionCount: Int,
    val intervalDays: Int?,
    val updatedAtEpochMs: Long,
)

fun RevisionStateEntity.toDomain() = RevisionState(
    attemptId = attemptId,
    nodeId = nodeId,
    enabled = enabled,
    lastReviewedAtEpochMs = lastReviewedAtEpochMs,
    nextDueDate = nextDueDateEpochDay?.let(LocalDate::ofEpochDay),
    revisionCount = revisionCount,
    intervalDays = intervalDays,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun RevisionState.toEntity() = RevisionStateEntity(
    attemptId = attemptId,
    nodeId = nodeId,
    enabled = enabled,
    lastReviewedAtEpochMs = lastReviewedAtEpochMs,
    nextDueDateEpochDay = nextDueDate?.toEpochDay(),
    revisionCount = revisionCount,
    intervalDays = intervalDays,
    updatedAtEpochMs = updatedAtEpochMs,
)

@Dao
interface RevisionStateDao {
    @Upsert
    suspend fun upsert(entity: RevisionStateEntity)

    @Query("SELECT * FROM revision_state WHERE attemptId = :attemptId AND nodeId = :nodeId")
    suspend fun byNode(attemptId: String, nodeId: String): RevisionStateEntity?

    @Query("SELECT * FROM revision_state WHERE attemptId = :attemptId AND nextDueDateEpochDay <= :epochDay AND enabled = 1")
    suspend fun dueBy(attemptId: String, epochDay: Long): List<RevisionStateEntity>

    @Query("DELETE FROM revision_state WHERE attemptId = :attemptId")
    suspend fun deleteForAttempt(attemptId: String)
}
