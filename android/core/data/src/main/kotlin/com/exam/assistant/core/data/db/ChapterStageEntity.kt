package com.exam.assistant.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import com.exam.assistant.domain.ChapterStage
import com.exam.assistant.domain.ChapterStageMark

/** Depth stages per chapter (§5.1). LEARNED is never a row here: it is the chapter's covered state. */
@Entity(
    tableName = "chapter_stage",
    primaryKeys = ["attemptId", "chapterId", "stage"],
)
data class ChapterStageEntity(
    val attemptId: String,
    val chapterId: String,
    val stage: String,
    @ColumnInfo(defaultValue = "1")
    val count: Int,
    val updatedAtEpochMs: Long?,
)

fun ChapterStageEntity.toDomainOrNull(): ChapterStageMark? =
    ChapterStage.entries.firstOrNull { it.name == stage }?.let { known ->
        ChapterStageMark(attemptId, chapterId, known, count, updatedAtEpochMs)
    }

@Dao
interface ChapterStageDao {
    @Upsert
    suspend fun upsert(entity: ChapterStageEntity)

    @Query("SELECT * FROM chapter_stage WHERE attemptId = :attemptId")
    suspend fun allOnce(attemptId: String): List<ChapterStageEntity>

    @Query("SELECT * FROM chapter_stage WHERE attemptId = :attemptId AND chapterId = :chapterId AND stage = :stage")
    suspend fun byKey(attemptId: String, chapterId: String, stage: String): ChapterStageEntity?

    @Query("DELETE FROM chapter_stage WHERE attemptId = :attemptId AND chapterId = :chapterId AND stage = :stage")
    suspend fun delete(attemptId: String, chapterId: String, stage: String)

    @Query("DELETE FROM chapter_stage WHERE attemptId = :attemptId")
    suspend fun deleteForAttempt(attemptId: String)
}
