package com.exam.assistant.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

/** The student's name for a chapter (§5.2). One row per renamed chapter; no row = original name. */
@Entity(
    tableName = "chapter_override",
    primaryKeys = ["attemptId", "chapterId"],
)
data class ChapterOverrideEntity(
    val attemptId: String,
    val chapterId: String,
    val customTitle: String?,
)

@Dao
interface ChapterOverrideDao {
    @Upsert
    suspend fun upsert(entity: ChapterOverrideEntity)

    @Query("SELECT * FROM chapter_override WHERE attemptId = :attemptId")
    suspend fun allOnce(attemptId: String): List<ChapterOverrideEntity>

    @Query("DELETE FROM chapter_override WHERE attemptId = :attemptId AND chapterId = :chapterId")
    suspend fun delete(attemptId: String, chapterId: String)

    @Query("DELETE FROM chapter_override WHERE attemptId = :attemptId")
    suspend fun deleteForAttempt(attemptId: String)
}
