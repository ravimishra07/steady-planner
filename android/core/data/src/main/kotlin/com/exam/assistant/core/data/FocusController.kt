package com.exam.assistant.core.data

import com.exam.assistant.core.common.Analytics
import com.exam.assistant.core.common.AnalyticsEvent
import com.exam.assistant.core.common.AnalyticsParam
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.StudySessionSegment
import com.exam.assistant.domain.StudySessionStatus
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Platform side effects of a Focus state change: the end alarm and the
 * notifications. Implemented in `:app`, which owns the receivers they target.
 */
interface FocusEffects {
    /** The persisted session changed. Running arms the alarm and shows the ongoing notification; anything else clears them. */
    suspend fun onSessionChanged(session: FocusSession)

    /** A running session reached its end. Posts the "Session complete" alert. */
    suspend fun onSessionCompleted(session: FocusSession)

    object None : FocusEffects {
        override suspend fun onSessionChanged(session: FocusSession) = Unit
        override suspend fun onSessionCompleted(session: FocusSession) = Unit
    }
}

/** One persisted Focus change. [completed] is the Room session closed by a natural end, if any. */
data class FocusTransition(
    val session: FocusSession,
    val didComplete: Boolean = false,
    val completed: StudySession? = null,
)

/**
 * The single owner of Focus session mutations: FocusStore (the clock) plus the
 * normalised Room session, plus the alarm and notification side effects.
 *
 * The Focus screen, the notification actions, the end alarm, boot and cold-start
 * recovery all go through here, so a session completes the same way whichever
 * of them notices first. Every mutation is serialised on one mutex.
 */
class FocusController(
    private val focusStore: FocusStore,
    private val attemptRepository: AttemptRepository,
    private val planRepository: PlanRepository,
    private val studySessionRepository: StudySessionRepository,
    private val rollingPlanRepository: RollingPlanRepository,
    private val effects: FocusEffects,
    private val analytics: Analytics = Analytics.NoOp,
) {
    private val mutex = Mutex()

    private val _transitions = MutableSharedFlow<FocusTransition>(extraBufferCapacity = 16)

    /** Every persisted change, for a live Focus screen to follow changes made from a notification or alarm. */
    val transitions: SharedFlow<FocusTransition> = _transitions.asSharedFlow()

    @Volatile
    private var unseenCompletion: StudySession? = null

    suspend fun load(): FocusSession = focusStore.load()

    /** The last natural completion no screen has shown yet, cleared on read. */
    fun consumeCompletion(): StudySession? {
        val value = unseenCompletion
        unseenCompletion = null
        return value
    }

    /** Starts (or adopts) a running or paused session. */
    suspend fun start(session: FocusSession): FocusTransition = mutex.withLock {
        val previous = focusStore.load()
        val adopting = (previous.status == FocusStatus.RUNNING || previous.status == FocusStatus.PAUSED) &&
            previous.block?.id == session.block?.id
        if (!adopting) {
            analytics.log(
                AnalyticsEvent.BLOCK_STARTED,
                mapOf(AnalyticsParam.PLANNED to (session.block != null), AnalyticsParam.IS_REVISION to (session.block?.isRevision == true)),
            )
        }
        focusStore.save(session)
        ensureNormalizedSession(session, System.currentTimeMillis())
        publish(FocusTransition(session))
    }

    /** Saves a session that is not running — idle or a changed idle length. Clears alarm and notifications. */
    suspend fun setIdle(session: FocusSession): FocusTransition = mutex.withLock {
        focusStore.save(session)
        publish(FocusTransition(session))
    }

    suspend fun pause(nowMs: Long = System.currentTimeMillis()): FocusTransition? = mutex.withLock {
        val stored = focusStore.load()
        if (stored.isExpiredAt(nowMs)) return@withLock completeLocked(stored, nowMs)
        val current = stored.withClockAt(nowMs)
        if (current.status != FocusStatus.RUNNING) return@withLock null
        val session = current.copy(status = FocusStatus.PAUSED, endsAtMs = null)
        focusStore.save(session)
        syncNormalizedSession(session, nowMs)
        publish(FocusTransition(session))
    }

    suspend fun resume(nowMs: Long = System.currentTimeMillis()): FocusTransition? = mutex.withLock {
        val current = focusStore.load()
        if (current.status != FocusStatus.PAUSED) return@withLock null
        val session = current.copy(
            status = FocusStatus.RUNNING,
            endsAtMs = nowMs + current.remainingSec * 1000L,
        )
        focusStore.save(session)
        syncNormalizedSession(session, nowMs)
        publish(FocusTransition(session))
    }

    suspend fun extend(seconds: Int, nowMs: Long = System.currentTimeMillis()): FocusTransition? = mutex.withLock {
        val stored = focusStore.load()
        if (stored.isExpiredAt(nowMs)) return@withLock completeLocked(stored, nowMs)
        val current = stored.withClockAt(nowMs)
        if (current.status != FocusStatus.RUNNING && current.status != FocusStatus.PAUSED) return@withLock null
        val updated = current.copy(
            durationSec = current.durationSec + seconds,
            remainingSec = current.remainingSec + seconds,
            endsAtMs = current.endsAtMs?.plus(seconds * 1_000L),
        )
        focusStore.save(updated)
        syncNormalizedSession(updated, nowMs)
        publish(FocusTransition(updated))
    }

    /** Stop and keep the focused time as a partial session. */
    suspend fun stopSavingPartial(nowMs: Long = System.currentTimeMillis()): FocusTransition? = mutex.withLock {
        val current = focusStore.load()
        if (current.isExpiredAt(nowMs)) return@withLock completeLocked(current, nowMs)
        if (current.status != FocusStatus.RUNNING && current.status != FocusStatus.PAUSED) return@withLock null
        val snapshot = current.withClockAt(nowMs)
        persistPartialSession(snapshot, nowMs)
        finishStoppedLocked(snapshot)
    }

    /** Stop and throw the session away. */
    suspend fun stopDiscarding(nowMs: Long = System.currentTimeMillis()): FocusTransition? = mutex.withLock {
        val current = focusStore.load()
        if (current.isExpiredAt(nowMs)) return@withLock completeLocked(current, nowMs)
        if (current.status != FocusStatus.RUNNING && current.status != FocusStatus.PAUSED) return@withLock null
        abandonNormalizedSession(nowMs)
        finishStoppedLocked(current)
    }

    /** Completes a running session whose end time has passed. Null when there was nothing to complete. */
    suspend fun completeIfExpired(nowMs: Long = System.currentTimeMillis()): FocusTransition? = mutex.withLock {
        val current = focusStore.load()
        if (!current.isExpiredAt(nowMs)) return@withLock null
        completeLocked(current, nowMs)
    }

    /** Writes focused seconds so far into Room, if the stored session is still the one being ticked. */
    suspend fun checkpoint(expectedEndsAtMs: Long?, nowMs: Long = System.currentTimeMillis()) = mutex.withLock {
        val stored = focusStore.load()
        if (stored.status != FocusStatus.RUNNING || stored.endsAtMs != expectedEndsAtMs) return@withLock
        if (stored.isExpiredAt(nowMs)) return@withLock
        syncNormalizedSession(stored.withClockAt(nowMs), nowMs)
    }

    /** Opening the Focus screen: makes sure Room mirrors a live session. */
    suspend fun syncLive(nowMs: Long = System.currentTimeMillis()) = mutex.withLock {
        val stored = focusStore.load()
        if (stored.status != FocusStatus.RUNNING && stored.status != FocusStatus.PAUSED) return@withLock
        if (stored.isExpiredAt(nowMs)) return@withLock
        syncNormalizedSession(stored.withClockAt(nowMs), nowMs)
    }

    /**
     * Cold start, the end alarm, boot and app update: an expired session is
     * completed exactly once; a live one gets its alarm and notification back.
     */
    suspend fun reconcile(nowMs: Long = System.currentTimeMillis()): FocusTransition = mutex.withLock {
        val stored = focusStore.load()
        if (stored.isExpiredAt(nowMs)) return@withLock completeLocked(stored, nowMs)
        val session = stored.withClockAt(nowMs)
        runCatching { effects.onSessionChanged(session) }
        FocusTransition(session)
    }

    private suspend fun completeLocked(current: FocusSession, nowMs: Long): FocusTransition {
        ensureNormalizedSession(current, nowMs)
        val completed = completeNormalizedSession(current.durationSec, nowMs)
        runCatching { rollingPlanRepository.replenish(nowMs = nowMs) }
        val session = current.copy(
            status = FocusStatus.DONE,
            remainingSec = 0,
            endsAtMs = null,
            completedToday = current.completedToday + 1,
        )
        focusStore.save(session)
        unseenCompletion = completed
        val isRevision = current.block?.isRevision == true
        analytics.log(
            AnalyticsEvent.BLOCK_COMPLETED,
            mapOf(AnalyticsParam.PLANNED to (current.block != null), AnalyticsParam.IS_REVISION to isRevision),
        )
        if (isRevision) analytics.log(AnalyticsEvent.REVISION_COMPLETED, mapOf(AnalyticsParam.SOURCE to "focus"))
        runCatching { effects.onSessionCompleted(session) }
        return publish(FocusTransition(session, didComplete = true, completed = completed))
    }

    private suspend fun finishStoppedLocked(current: FocusSession): FocusTransition {
        val session = current.copy(
            status = FocusStatus.IDLE,
            remainingSec = current.durationSec,
            endsAtMs = null,
            block = null,
        )
        focusStore.save(session)
        return publish(FocusTransition(session))
    }

    private suspend fun publish(transition: FocusTransition): FocusTransition {
        runCatching { effects.onSessionChanged(transition.session) }
        _transitions.tryEmit(transition)
        return transition
    }

    private suspend fun persistPartialSession(focus: FocusSession, nowMs: Long) {
        val attempt = attemptRepository.activeAttempt() ?: return
        var active = studySessionRepository.activeSession(attempt.id)
        if (active == null) {
            ensureNormalizedSession(focus, nowMs)
            active = studySessionRepository.activeSession(attempt.id) ?: return
        }
        val focusedSeconds = focus.focusedSecondsAt(nowMs)
        if (focusedSeconds > 0) {
            studySessionRepository.completePartialSession(
                active.copy(focusedSeconds = focusedSeconds.coerceAtLeast(active.focusedSeconds)),
                nowMs,
            )
        } else {
            studySessionRepository.abandonSession(active, nowMs)
        }
        rollingPlanRepository.replenish(nowMs = nowMs)
    }

    private suspend fun ensureNormalizedSession(focus: FocusSession, nowMs: Long) {
        if (focus.status != FocusStatus.RUNNING && focus.status != FocusStatus.PAUSED) return
        val attempt = attemptRepository.activeAttempt() ?: return
        val existing = studySessionRepository.activeSession(attempt.id)
        if (existing != null) return
        val block = focus.block
        val planBlock = block?.let { planRepository.byId(it.id) }
        val focusedSeconds = focus.focusedSecondsAt(nowMs)
        val startedAt = focus.endsAtMs
            ?.minus(focus.durationSec * 1_000L)
            ?: (nowMs - focusedSeconds * 1_000L)
        val timeZoneId = ZoneId.systemDefault()
        val session = StudySession(
            id = "focus_${UUID.randomUUID()}",
            attemptId = attempt.id,
            nodeId = block?.nodeKey?.ifBlank { planBlock?.nodeId },
            subjectId = planBlock?.subjectId,
            planBlockId = planBlock?.id,
            activityType = if (block?.isRevision == true) StudyActivityType.REVISION else planBlock?.activityType ?: StudyActivityType.LEARN,
            startedAtEpochMs = startedAt,
            endedAtEpochMs = null,
            studyDate = Instant.ofEpochMilli(startedAt).atZone(timeZoneId).toLocalDate(),
            timeZoneId = timeZoneId.id,
            focusedSeconds = focusedSeconds,
            pausedSeconds = 0,
            status = if (focus.status == FocusStatus.PAUSED) StudySessionStatus.PAUSED else StudySessionStatus.RUNNING,
            focusLockUsed = false,
            interruptionCount = 0,
            customTitle = if (planBlock?.nodeId == null) block?.title ?: "Focus session" else planBlock.customTitle,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
        )
        studySessionRepository.startSession(
            session,
            StudySessionSegment(
                id = "${session.id}_0",
                sessionId = session.id,
                nodeId = session.nodeId,
                subjectId = session.subjectId,
                startedAtEpochMs = startedAt,
                endedAtEpochMs = null,
                focusedSeconds = 0,
                order = 0,
            ),
        )
    }

    private suspend fun syncNormalizedSession(focus: FocusSession, nowMs: Long) {
        val attempt = attemptRepository.activeAttempt() ?: return
        val active = studySessionRepository.activeSession(attempt.id) ?: run {
            ensureNormalizedSession(focus, nowMs)
            return
        }
        studySessionRepository.updateRunning(
            active.copy(
                focusedSeconds = focus.focusedSecondsAt(nowMs).coerceAtLeast(active.focusedSeconds),
                status = if (focus.status == FocusStatus.PAUSED) StudySessionStatus.PAUSED else StudySessionStatus.RUNNING,
                updatedAtEpochMs = nowMs,
            ),
        )
    }

    private suspend fun completeNormalizedSession(focusedSeconds: Int, nowMs: Long): StudySession? {
        val attempt = attemptRepository.activeAttempt() ?: return null
        val active = studySessionRepository.activeSession(attempt.id) ?: return null
        return studySessionRepository.completeSession(
            active.copy(focusedSeconds = focusedSeconds.coerceAtLeast(active.focusedSeconds)),
            today = active.studyDate,
            nowMs = nowMs,
        )
    }

    private suspend fun abandonNormalizedSession(nowMs: Long) {
        val attempt = attemptRepository.activeAttempt() ?: return
        val active = studySessionRepository.activeSession(attempt.id) ?: return
        studySessionRepository.abandonSession(active, nowMs)
    }

    companion object {
        const val EXTEND_SECONDS = 5 * 60
    }
}
