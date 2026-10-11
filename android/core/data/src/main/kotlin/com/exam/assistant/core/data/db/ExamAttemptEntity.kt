package com.exam.assistant.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.exam.assistant.domain.ExamAttempt
import com.exam.assistant.domain.ExamAttemptStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "exam_attempt")
@TypeConverters(Converters::class)
data class ExamAttemptEntity(
    @PrimaryKey val id: String,
    val examId: String,
    val syllabusVersion: String,
    val examDateEpochDay: Long?,
    val targetCompletionDateEpochDay: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val status: String,
)

fun ExamAttemptEntity.toDomain() = ExamAttempt(
    id = id,
    examId = examId,
    syllabusVersion = syllabusVersion,
    examDate = examDateEpochDay?.let(LocalDate::ofEpochDay),
    targetCompletionDate = LocalDate.ofEpochDay(targetCompletionDateEpochDay),
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    status = ExamAttemptStatus.valueOf(status),
)

fun ExamAttempt.toEntity() = ExamAttemptEntity(
    id = id,
    examId = examId,
    syllabusVersion = syllabusVersion,
    examDateEpochDay = examDate?.toEpochDay(),
    targetCompletionDateEpochDay = targetCompletionDate.toEpochDay(),
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    status = status.name,
)

@Dao
interface ExamAttemptDao {
    @Upsert
    suspend fun upsert(entity: ExamAttemptEntity)

    @Query("SELECT * FROM exam_attempt WHERE id = :id")
    suspend fun byId(id: String): ExamAttemptEntity?

    @Query("SELECT * FROM exam_attempt WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActive(): Flow<ExamAttemptEntity?>

    @Query("SELECT * FROM exam_attempt WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun activeOnce(): ExamAttemptEntity?

    @Query("DELETE FROM exam_attempt WHERE id = :attemptId")
    suspend fun deleteAttempt(attemptId: String)
}
