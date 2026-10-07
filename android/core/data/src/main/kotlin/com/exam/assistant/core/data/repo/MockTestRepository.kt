package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.MockMistake
import com.exam.assistant.domain.MockTest
import com.exam.assistant.domain.newlyTaggedChapters
import com.exam.assistant.domain.revisionStateAfterMistake
import kotlinx.coroutines.withContext

/** Mock and test log with its mistake tags (PRODUCTION_V1_PLAN §8.1, §8.2). */
class MockTestRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val db = PrepTrackerDatabase.get(context)
    private val dao = db.mockTestDao()

    suspend fun allFor(attemptId: String): List<MockTest> =
        withContext(dispatchers.io) { dao.allFor(attemptId).map { it.toDomain() } }

    suspend fun byId(id: String): MockTest? = withContext(dispatchers.io) { dao.byId(id)?.toDomain() }

    suspend fun mistakesFor(mockId: String): List<MockMistake> =
        withContext(dispatchers.io) { dao.mistakesFor(mockId).map { it.toDomain() } }

    suspend fun mistakesForAttempt(attemptId: String): List<MockMistake> =
        withContext(dispatchers.io) { dao.mistakesForAttempt(attemptId).map { it.toDomain() } }

    /**
     * Saves a mock and replaces its mistake tags in one transaction. Each chapter tagged for the
     * first time in this mock gets a Struggled outcome on its revision schedule; a chapter that
     * was already tagged is left alone, so editing and re-saving never applies it twice.
     */
    suspend fun save(mock: MockTest, mistakes: List<MockMistake>, nowMs: Long) = withContext(dispatchers.io) {
        db.withTransaction {
            val before = dao.mistakesFor(mock.id).map { it.toDomain() }
            dao.upsert(mock.toEntity())
            dao.deleteMistakesFor(mock.id)
            dao.upsertMistakes(mistakes.map { it.copy(mockId = mock.id).toEntity() })
            val revisionDao = db.revisionStateDao()
            newlyTaggedChapters(before, mistakes).forEach { chapterId ->
                val current = revisionDao.byNode(mock.attemptId, chapterId)?.toDomain()
                revisionDao.upsert(revisionStateAfterMistake(current, mock.attemptId, chapterId, mock.date, nowMs).toEntity())
            }
        }
    }

    /** Deletes a mock and its tags. A revision already brought forward stays where it is. */
    suspend fun delete(mockId: String) = withContext(dispatchers.io) { dao.deleteMock(mockId) }
}
