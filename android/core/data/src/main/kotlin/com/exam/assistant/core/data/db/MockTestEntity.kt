package com.exam.assistant.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.exam.assistant.domain.MistakeType
import com.exam.assistant.domain.MockMistake
import com.exam.assistant.domain.MockTest
import com.exam.assistant.domain.MockTestType
import com.exam.assistant.domain.NEET_MAX_TOTAL
import java.time.LocalDate

/** One logged test or mock (PRODUCTION_V1_PLAN §8.1). Subject scores and counts are optional. */
@Entity(tableName = "mock_test", indices = [Index("attemptId")])
data class MockTestEntity(
    @PrimaryKey val id: String,
    val attemptId: String,
    val dateEpochDay: Long,
    val name: String,
    val type: String,
    val physics: Int?,
    val chemistry: Int?,
    val botany: Int?,
    val zoology: Int?,
    val total: Int,
    @ColumnInfo(defaultValue = "$NEET_MAX_TOTAL") val maxTotal: Int,
    val attempted: Int?,
    val correct: Int?,
    val wrong: Int?,
)

/** A chapter where marks were lost in one mock (§8.2). */
@Entity(tableName = "mock_mistake", primaryKeys = ["mockId", "chapterId"])
data class MockMistakeEntity(
    val mockId: String,
    val chapterId: String,
    val type: String,
)

fun MockTestEntity.toDomain() = MockTest(
    id = id,
    attemptId = attemptId,
    date = LocalDate.ofEpochDay(dateEpochDay),
    name = name,
    type = runCatching { MockTestType.valueOf(type) }.getOrDefault(MockTestType.FULL_MOCK),
    physics = physics,
    chemistry = chemistry,
    botany = botany,
    zoology = zoology,
    total = total,
    maxTotal = maxTotal,
    attempted = attempted,
    correct = correct,
    wrong = wrong,
)

fun MockTest.toEntity() = MockTestEntity(
    id = id,
    attemptId = attemptId,
    dateEpochDay = date.toEpochDay(),
    name = name,
    type = type.name,
    physics = physics,
    chemistry = chemistry,
    botany = botany,
    zoology = zoology,
    total = total,
    maxTotal = maxTotal,
    attempted = attempted,
    correct = correct,
    wrong = wrong,
)

fun MockMistakeEntity.toDomain() = MockMistake(
    mockId = mockId,
    chapterId = chapterId,
    type = runCatching { MistakeType.valueOf(type) }.getOrDefault(MistakeType.CONCEPT),
)

fun MockMistake.toEntity() = MockMistakeEntity(mockId, chapterId, type.name)

@Dao
interface MockTestDao {
    @Upsert
    suspend fun upsert(entity: MockTestEntity)

    @Query("SELECT * FROM mock_test WHERE id = :id")
    suspend fun byId(id: String): MockTestEntity?

    @Query("SELECT * FROM mock_test WHERE attemptId = :attemptId ORDER BY dateEpochDay DESC")
    suspend fun allFor(attemptId: String): List<MockTestEntity>

    @Query("SELECT * FROM mock_mistake WHERE mockId = :mockId")
    suspend fun mistakesFor(mockId: String): List<MockMistakeEntity>

    @Query("SELECT * FROM mock_mistake WHERE mockId IN (SELECT id FROM mock_test WHERE attemptId = :attemptId)")
    suspend fun mistakesForAttempt(attemptId: String): List<MockMistakeEntity>

    @Upsert
    suspend fun upsertMistakes(entities: List<MockMistakeEntity>)

    @Query("DELETE FROM mock_mistake WHERE mockId = :mockId")
    suspend fun deleteMistakesFor(mockId: String)

    @Query("DELETE FROM mock_test WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun deleteMock(id: String) {
        deleteMistakesFor(id)
        deleteById(id)
    }

    @Query("DELETE FROM mock_mistake WHERE mockId IN (SELECT id FROM mock_test WHERE attemptId = :attemptId)")
    suspend fun deleteMistakesForAttempt(attemptId: String)

    @Query("DELETE FROM mock_test WHERE attemptId = :attemptId")
    suspend fun deleteMocksForAttempt(attemptId: String)

    @Transaction
    suspend fun deleteAllForAttempt(attemptId: String) {
        deleteMistakesForAttempt(attemptId)
        deleteMocksForAttempt(attemptId)
    }
}
