package com.exam.assistant.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.exam.assistant.domain.PlanBlockSource
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudyPlanBlock
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(
    tableName = "study_plan_block",
    indices = [
        Index("attemptId"),
        Index("scheduledDateEpochDay"),
        Index("status"),
        Index("nodeId"),
        Index("subjectId"),
    ],
)
@TypeConverters(Converters::class)
data class StudyPlanBlockEntity(
    @PrimaryKey val id: String,
    val attemptId: String,
    val nodeId: String?,
    val subjectId: String?,
    val customTitle: String?,
    val activityType: String,
    val scheduledDateEpochDay: Long,
    val startMinuteOfDay: Int,
    val plannedMinutes: Int,
    val status: String,
    val source: String,
    val rescheduledFromId: String?,
    val replacedById: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

fun StudyPlanBlockEntity.toDomain() = StudyPlanBlock(
    id = id,
    attemptId = attemptId,
    nodeId = nodeId,
    subjectId = subjectId,
    customTitle = customTitle,
    activityType = StudyActivityType.valueOf(activityType),
    scheduledDate = LocalDate.ofEpochDay(scheduledDateEpochDay),
    startMinuteOfDay = startMinuteOfDay,
    plannedMinutes = plannedMinutes,
    status = PlanBlockStatus.valueOf(status),
    source = PlanBlockSource.valueOf(source),
    rescheduledFromId = rescheduledFromId,
    replacedById = replacedById,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun StudyPlanBlock.toEntity() = StudyPlanBlockEntity(
    id = id,
    attemptId = attemptId,
    nodeId = nodeId,
    subjectId = subjectId,
    customTitle = customTitle,
    activityType = activityType.name,
    scheduledDateEpochDay = scheduledDate.toEpochDay(),
    startMinuteOfDay = startMinuteOfDay,
    plannedMinutes = plannedMinutes,
    status = status.name,
    source = source.name,
    rescheduledFromId = rescheduledFromId,
    replacedById = replacedById,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

@Dao
interface StudyPlanBlockDao {
    @Upsert
    suspend fun upsert(entity: StudyPlanBlockEntity)

    @Upsert
    suspend fun upsertAll(entities: List<StudyPlanBlockEntity>)

    @Query("SELECT * FROM study_plan_block WHERE attemptId = :attemptId AND scheduledDateEpochDay = :epochDay")
    fun observeForDate(attemptId: String, epochDay: Long): Flow<List<StudyPlanBlockEntity>>

    @Query("SELECT * FROM study_plan_block WHERE attemptId = :attemptId AND scheduledDateEpochDay = :epochDay")
    suspend fun forDateOnce(attemptId: String, epochDay: Long): List<StudyPlanBlockEntity>

    @Query("SELECT * FROM study_plan_block WHERE attemptId = :attemptId AND scheduledDateEpochDay BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun between(attemptId: String, startEpochDay: Long, endEpochDay: Long): List<StudyPlanBlockEntity>

    @Query("SELECT * FROM study_plan_block WHERE id = :id")
    suspend fun byId(id: String): StudyPlanBlockEntity?

    @Query("DELETE FROM study_plan_block WHERE attemptId = :attemptId")
    suspend fun deleteForAttempt(attemptId: String)
}
