package com.exam.assistant.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.exam.assistant.domain.BatchPosition
import com.exam.assistant.domain.ChapterCoaching

/** Coaching lecture and DPP counts per chapter (§5.4). Done = highest ticked index. */
@Entity(
    tableName = "chapter_coaching",
    primaryKeys = ["attemptId", "chapterId"],
)
data class ChapterCoachingEntity(
    val attemptId: String,
    val chapterId: String,
    val lectures: Int,
    val dpps: Int,
    val lecturesDone: Int,
    val dppsDone: Int,
)

/** Where the student's coaching batch is now: one row per attempt (§5.4). */
@Entity(tableName = "batch_position")
data class BatchPositionEntity(
    @PrimaryKey val attemptId: String,
    val chapterId: String,
    val lecture: Int,
    val updatedAtEpochMs: Long,
)

fun ChapterCoachingEntity.toDomain() = ChapterCoaching(chapterId, lectures, dpps, lecturesDone, dppsDone)

fun BatchPositionEntity.toDomain() = BatchPosition(chapterId, lecture, updatedAtEpochMs)

@Dao
interface CoachingDao {
    @Upsert
    suspend fun upsert(entity: ChapterCoachingEntity)

    @Query("SELECT * FROM chapter_coaching WHERE attemptId = :attemptId")
    suspend fun allOnce(attemptId: String): List<ChapterCoachingEntity>

    @Query("DELETE FROM chapter_coaching WHERE attemptId = :attemptId AND chapterId = :chapterId")
    suspend fun delete(attemptId: String, chapterId: String)

    @Query("DELETE FROM chapter_coaching WHERE attemptId = :attemptId")
    suspend fun deleteForAttempt(attemptId: String)

    @Upsert
    suspend fun upsertPosition(entity: BatchPositionEntity)

    @Query("SELECT * FROM batch_position WHERE attemptId = :attemptId")
    suspend fun positionFor(attemptId: String): BatchPositionEntity?

    @Query("DELETE FROM batch_position WHERE attemptId = :attemptId")
    suspend fun deletePositionForAttempt(attemptId: String)
}
