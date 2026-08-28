package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.StudySessionSegment
import com.exam.assistant.domain.StudySessionStatus
import com.exam.assistant.domain.revisionStateAfterLearn
import com.exam.assistant.domain.revisionStateAfterReview
import com.exam.assistant.domain.topicProgressAfterStudy
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The actual-history writer. [StudyPlanBlock] is intent; this is truth.
 * Owns the transactional "complete a Focus session" flow (§27/§39): close
 * session+segment, mark the linked plan block completed, update topic
 * progress and revision state — one atomic write, never partial.
 */
class StudySessionRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val db = PrepTrackerDatabase.get(context)
    private val sessionDao = db.studySessionDao()

    suspend fun byId(id: String): StudySession? = withContext(dispatchers.io) { sessionDao.byId(id)?.toDomain() }

    suspend fun activeSession(attemptId: String): StudySession? =
        withContext(dispatchers.io) { sessionDao.activeSession(attemptId)?.toDomain() }

    suspend fun forDate(attemptId: String, date: LocalDate): List<StudySession> =
        withContext(dispatchers.io) { sessionDao.forDate(attemptId, date.toEpochDay()).map { it.toDomain() } }

    suspend fun between(attemptId: String, start: LocalDate, end: LocalDate): List<StudySession> =
        withContext(dispatchers.io) {
            sessionDao.between(attemptId, start.toEpochDay(), end.toEpochDay()).map { it.toDomain() }
        }

    suspend fun forNode(attemptId: String, nodeId: String): List<StudySession> =
        withContext(dispatchers.io) { sessionDao.forNode(attemptId, nodeId).map { it.toDomain() } }

    suspend fun segmentsFor(sessionId: String): List<StudySessionSegment> =
        withContext(dispatchers.io) { sessionDao.segmentsFor(sessionId).map { it.toDomain() } }

    suspend fun dailyFocusedSeconds(attemptId: String, start: LocalDate, end: LocalDate): Map<LocalDate, Pair<Int, Int>> =
        withContext(dispatchers.io) {
            sessionDao.dailySums(attemptId, start.toEpochDay(), end.toEpochDay())
                .associate { LocalDate.ofEpochDay(it.studyDateEpochDay) to (it.totalSeconds to it.sessionCount) }
        }

    suspend fun subjectFocusedSeconds(attemptId: String, start: LocalDate, end: LocalDate): Map<String?, Int> =
        withContext(dispatchers.io) {
            sessionDao.subjectSums(attemptId, start.toEpochDay(), end.toEpochDay()).associate { it.subjectId to it.totalSeconds }
        }

    suspend fun earliestStudyDate(attemptId: String): LocalDate? =
        withContext(dispatchers.io) { sessionDao.earliestStudyDate(attemptId)?.let(LocalDate::ofEpochDay) }

    /** Starts a session, planned (linked to [planBlockId]) or unplanned (null). Opens segment 0 on [nodeId]. */
    suspend fun startSession(
        session: StudySession,
        firstSegment: StudySessionSegment,
    ): StudySession = withContext(dispatchers.io) {
        db.withTransaction {
            sessionDao.upsert(session.toEntity())
            sessionDao.upsertSegment(firstSegment.toEntity())
        }
        session
    }

    /** Persists elapsed-time updates while a session is running/paused (ticker writes). */
    suspend fun updateRunning(session: StudySession) = withContext(dispatchers.io) {
        sessionDao.upsert(session.toEntity())
    }

    /**
     * Closes the current open segment on [session] and opens a new one on
     * [nextNodeId] — the parent session keeps running throughout. Supports
     * a future multi-topic Focus session; unused until that UI exists.
     */
    suspend fun switchSegment(sessionId: String, closedFocusedSeconds: Int, nowMs: Long, nextSegment: StudySessionSegment) =
        withContext(dispatchers.io) {
            db.withTransaction {
                val open = sessionDao.openSegment(sessionId)
                if (open != null) {
                    sessionDao.upsertSegment(open.copy(endedAtEpochMs = nowMs, focusedSeconds = closedFocusedSeconds))
                }
                sessionDao.upsertSegment(nextSegment.toEntity())
            }
        }

    /**
     * The one Focus-completion transaction: close session + open segment,
     * mark the linked plan block completed (if any), advance topic progress
     * and revision state. Nothing here is a separate, uncoordinated write.
     */
    suspend fun completeSession(
        session: StudySession,
        today: LocalDate,
        nowMs: Long,
    ): StudySession = withContext(dispatchers.io) {
        val completed = session.copy(status = StudySessionStatus.COMPLETED, endedAtEpochMs = nowMs, updatedAtEpochMs = nowMs)
        db.withTransaction {
            sessionDao.upsert(completed.toEntity())
            sessionDao.openSegment(session.id)?.let { open ->
                sessionDao.upsertSegment(open.copy(endedAtEpochMs = nowMs, focusedSeconds = completed.focusedSeconds))
            }
            session.planBlockId?.let { planBlockId ->
                db.studyPlanBlockDao().byId(planBlockId)?.let { block ->
                    db.studyPlanBlockDao().upsert(block.copy(status = PlanBlockStatus.COMPLETED.name, updatedAtEpochMs = nowMs))
                }
            }
            val nodeId = session.nodeId
            if (nodeId != null) {
                val progressDao = db.topicProgressDao()
                val current = progressDao.byNode(session.attemptId, nodeId)?.toDomain()
                progressDao.upsert(topicProgressAfterStudy(current, session.attemptId, nodeId, nowMs).toEntity())

                val revisionDao = db.revisionStateDao()
                val currentRevision = revisionDao.byNode(session.attemptId, nodeId)?.toDomain()
                val updatedRevision = if (session.activityType == StudyActivityType.REVISION) {
                    revisionStateAfterReview(currentRevision, session.attemptId, nodeId, today, nowMs)
                } else {
                    revisionStateAfterLearn(currentRevision, session.attemptId, nodeId, today, nowMs)
                }
                revisionDao.upsert(updatedRevision.toEntity())
            }
        }
        completed
    }

    suspend fun abandonSession(session: StudySession, nowMs: Long) = withContext(dispatchers.io) {
        sessionDao.upsert(session.copy(status = StudySessionStatus.ABANDONED, endedAtEpochMs = nowMs, updatedAtEpochMs = nowMs).toEntity())
    }
}
