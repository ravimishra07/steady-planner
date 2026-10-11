package com.exam.assistant.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusBlockRef
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.StudySessionSegment
import com.exam.assistant.domain.StudySessionStatus
import com.exam.assistant.domain.StudyOutcome
import com.exam.assistant.domain.StudyPlanBlock
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.findSubjectOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FocusViewModel(
    private val focusStore: FocusStore,
    private val settingsStore: SettingsStore,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val planRepository: PlanRepository,
    private val studySessionRepository: StudySessionRepository,
    private val rollingPlanRepository: RollingPlanRepository,
    private val appScope: CoroutineScope,
    /** Told, never asked — Focus Lock reacts to the session lifecycle, it doesn't own it. */
    private val onFocusLockStart: () -> Unit = {},
    private val onFocusLockStop: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private var tickerJob: Job? = null
    private var lastCheckpointFocusedSeconds = 0
    private var selectedBlock: FocusBlockRef? = null
    private var lastCompletedSessionId: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val stored = focusStore.load()
            if (stored.isExpiredAt(now)) {
                completeExpiredSession(now)
                return@launch
            }
            val session = stored.withClockAt(now)
            applySession(session)
            if (session.status == FocusStatus.RUNNING || session.status == FocusStatus.PAUSED) {
                syncNormalizedSession(session, now)
            }
            startTickerIfNeeded(session)
            // Recovery path: if our process (and possibly the service) was killed mid-session,
            // re-derive Focus Lock's active state from the session rather than trusting memory.
            if (session.status == FocusStatus.RUNNING || session.status == FocusStatus.PAUSED) {
                onFocusLockStart()
            }
        }
    }

    fun startSession() {
        viewModelScope.launch {
            val block = selectedBlock ?: nextBlock()
            val duration = block?.id
                ?.let { id -> planRepository.byId(id) }
                ?.plannedMinutes
                ?.times(60)
                ?: settingsStore.focusDurationSec()
            selectedBlock = null
            val session = FocusSession(
                status = FocusStatus.RUNNING,
                durationSec = duration,
                remainingSec = duration,
                endsAtMs = System.currentTimeMillis() + duration * 1000L,
                block = block,
                completedToday = focusStore.load().completedToday,
            )
            focusStore.save(session)
            ensureNormalizedSession(session)
            applySession(session)
            startTickerIfNeeded(session)
            onFocusLockStart()
        }
    }

    fun pause() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val stored = focusStore.load()
            if (stored.isExpiredAt(now)) {
                completeExpiredSession(now)
                return@launch
            }
            val current = stored.withClockAt(now)
            if (current.status != FocusStatus.RUNNING) return@launch
            val session = current.copy(status = FocusStatus.PAUSED, endsAtMs = null)
            focusStore.save(session)
            syncNormalizedSession(session, now)
            applySession(session)
            stopTicker()
        }
    }

    fun resume() {
        viewModelScope.launch {
            val current = focusStore.load()
            if (current.status != FocusStatus.PAUSED) return@launch
            val session = current.copy(
                status = FocusStatus.RUNNING,
                endsAtMs = System.currentTimeMillis() + current.remainingSec * 1000L,
            )
            focusStore.save(session)
            syncNormalizedSession(session)
            applySession(session)
            startTickerIfNeeded(session)
            onFocusLockStart()
        }
    }

    fun requestStop() {
        _state.update { it.copy(showStopDialog = true) }
    }

    fun setDuration(minutes: Int) {
        if (minutes !in setOf(25, 50, 90)) return
        viewModelScope.launch {
            settingsStore.setFocusDurationSec(minutes * 60)
            val current = focusStore.load()
            if (current.status == FocusStatus.IDLE) {
                val updated = current.copy(durationSec = minutes * 60, remainingSec = minutes * 60)
                focusStore.save(updated)
                applySession(updated)
            }
        }
    }

    fun selectQueueItem(id: String) {
        viewModelScope.launch {
            val chosen = planRepository.byId(id) ?: return@launch
            val chosenBlock = resolveBlock(chosen)
            selectedBlock = chosenBlock
            _state.update {
                it.copy(
                    durationMinutes = chosen.plannedMinutes,
                    blockTitle = chosenBlock.title,
                    blockSubtitle = chosenBlock.subtitle,
                    blockTag = chosenBlock.tag,
                    hasBlock = true,
                    queue = it.queue.filterNot { row -> row.id == id },
                )
            }
        }
    }

    fun extendFiveMinutes() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val stored = focusStore.load()
            if (stored.isExpiredAt(now)) {
                completeExpiredSession(now)
                return@launch
            }
            val current = stored.withClockAt(now)
            if (current.status != FocusStatus.RUNNING && current.status != FocusStatus.PAUSED) return@launch
            val updated = current.copy(
                durationSec = current.durationSec + 5 * 60,
                remainingSec = current.remainingSec + 5 * 60,
                endsAtMs = current.endsAtMs?.plus(5 * 60_000L),
            )
            focusStore.save(updated)
            applySession(updated)
            syncNormalizedSession(updated, now)
            if (updated.status == FocusStatus.RUNNING) startTickerIfNeeded(updated)
        }
    }

    fun dismissStopDialog() {
        _state.update { it.copy(showStopDialog = false) }
    }

    fun recordOutcome(outcome: StudyOutcome) {
        val sessionId = lastCompletedSessionId ?: return
        viewModelScope.launch {
            studySessionRepository.recordOutcome(sessionId, outcome, System.currentTimeMillis())
            _state.update { it.copy(outcome = outcome) }
        }
    }

    fun savePartialAndStop() {
        viewModelScope.launch {
            sessionMutationMutex.withLock {
                val now = System.currentTimeMillis()
                val current = focusStore.load()
                if (current.isExpiredAt(now)) {
                    completeSessionLocked(current, now)
                    return@withLock
                }
                if (current.status != FocusStatus.RUNNING && current.status != FocusStatus.PAUSED) return@withLock
                val snapshot = current.withClockAt(now)
                persistPartialSession(snapshot, now)
                finishStoppedSession(snapshot)
            }
        }
    }

    fun discardAndStop() {
        viewModelScope.launch {
            sessionMutationMutex.withLock {
                val now = System.currentTimeMillis()
                val current = focusStore.load()
                if (current.isExpiredAt(now)) {
                    completeSessionLocked(current, now)
                    return@withLock
                }
                if (current.status != FocusStatus.RUNNING && current.status != FocusStatus.PAUSED) return@withLock
                abandonNormalizedSession(now)
                finishStoppedSession(current)
            }
        }
    }

    fun startAnother() {
        viewModelScope.launch {
            val current = focusStore.load()
            val session = current.copy(
                status = FocusStatus.IDLE,
                remainingSec = current.durationSec,
                endsAtMs = null,
                block = nextBlock(),
            )
            focusStore.save(session)
            applySession(session)
            stopTicker()
            startSession()
        }
    }

    fun resetToIdle() {
        viewModelScope.launch {
            val current = focusStore.load()
            val session = current.copy(
                status = FocusStatus.IDLE,
                remainingSec = current.durationSec,
                endsAtMs = null,
                block = nextBlock(),
            )
            focusStore.save(session)
            applySession(session)
            stopTicker()
        }
    }

    private suspend fun completeExpiredSession(nowMs: Long) {
        sessionMutationMutex.withLock {
            val current = focusStore.load()
            if (!current.isExpiredAt(nowMs)) return@withLock
            completeSessionLocked(current, nowMs)
        }
    }

    private suspend fun completeSessionLocked(current: FocusSession, nowMs: Long) {
        ensureNormalizedSession(current, nowMs)
        lastCompletedSessionId = completeNormalizedSession(current.durationSec, nowMs)?.id
        rollingPlanRepository.replenish(nowMs = nowMs)
        val session = current.copy(
            status = FocusStatus.DONE,
            remainingSec = 0,
            endsAtMs = null,
            completedToday = current.completedToday + 1,
        )
        focusStore.save(session)
        applySession(session)
        stopTicker()
        dismissStopDialog()
        onFocusLockStop()
    }

    private suspend fun finishStoppedSession(current: FocusSession) {
        val session = current.copy(
            status = FocusStatus.IDLE,
            remainingSec = current.durationSec,
            endsAtMs = null,
            block = null,
        )
        focusStore.save(session)
        applySession(session)
        stopTicker()
        dismissStopDialog()
        onFocusLockStop()
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

    private suspend fun nextBlock(): FocusBlockRef? {
        return todayQueue().firstOrNull()?.second
    }

    private suspend fun todayQueue(): List<Pair<StudyPlanBlock, FocusBlockRef>> {
        val attempt = attemptRepository.activeAttempt() ?: return emptyList()
        return planRepository.planForDate(attempt.id, LocalDate.now())
            .filter { it.status == PlanBlockStatus.PLANNED }
            .sortedBy { it.startMinuteOfDay }
            .map { it to resolveBlock(it) }
    }

    private suspend fun resolveBlock(block: StudyPlanBlock): FocusBlockRef {
        val attempt = attemptRepository.activeAttempt()
        val pack = attempt?.let { examPackRepository.examPackFor(it.examId) }
        val node = block.nodeId?.let { pack?.findNode(it) }
        val subject = block.nodeId?.let { pack?.findSubjectOf(it) }
        return block.toFocusBlock(
            title = node?.title ?: block.customTitle ?: "Study session",
            subtitle = subject?.name.orEmpty(),
        )
    }

    private suspend fun ensureNormalizedSession(
        focus: FocusSession,
        nowMs: Long = System.currentTimeMillis(),
    ) {
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
            customTitle = if (planBlock?.nodeId == null) block?.title ?: "Focus session" else null,
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

    private suspend fun syncNormalizedSession(focus: FocusSession, nowMs: Long = System.currentTimeMillis()) {
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

    private fun applySession(session: FocusSession) {
        val clocked = session
        _remainingSeconds.value = when (clocked.status) {
            FocusStatus.DONE -> 0
            else -> clocked.remainingSec
        }
        val block = clocked.block
        _state.update {
            it.copy(
                loading = false,
                status = clocked.status,
                durationMinutes = (clocked.durationSec / 60.0).toInt().coerceAtLeast(1),
                blockTitle = block?.title.orEmpty(),
                blockSubtitle = block?.subtitle.orEmpty(),
                blockTag = block?.tag,
                hasBlock = block != null,
                outcome = if (clocked.status == FocusStatus.DONE) it.outcome else null,
            )
        }
        if (block == null && clocked.status == FocusStatus.IDLE) {
            viewModelScope.launch {
                val next = nextBlock()
                val sessions = todayQueue()
                _state.update {
                    it.copy(
                        blockTitle = next?.title.orEmpty(),
                        blockSubtitle = next?.subtitle.orEmpty(),
                        blockTag = next?.tag,
                        hasBlock = next != null,
                        durationMinutes = next?.let { row -> sessions.firstOrNull { it.first.id == row.id }?.first?.plannedMinutes }
                            ?: it.durationMinutes,
                        queue = sessions
                            .filterNot { row -> row.first.id == next?.id }
                            .take(6)
                            .map { (plan, block) -> plan.toFocusQueueItem(block) },
                    )
                }
            }
        } else if (clocked.status == FocusStatus.IDLE) {
            viewModelScope.launch {
                val sessions = todayQueue()
                _state.update { current ->
                    current.copy(
                        queue = sessions
                            .filterNot { row -> row.first.id == block?.id }
                            .take(6)
                            .map { (plan, rowBlock) -> plan.toFocusQueueItem(rowBlock) },
                    )
                }
            }
        }
    }

    private fun startTickerIfNeeded(session: FocusSession) {
        if (session.status != FocusStatus.RUNNING) {
            stopTicker()
            return
        }
        lastCheckpointFocusedSeconds = session.focusedSecondsAt(System.currentTimeMillis())
        processTickerJob?.cancel()
        val job = appScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                if (session.isExpiredAt(now)) {
                    completeExpiredSession(now)
                    break
                }
                val snapshot = session.withClockAt(now)
                _remainingSeconds.value = snapshot.remainingSec
                val focusedSeconds = snapshot.focusedSecondsAt(now)
                if (focusedSeconds - lastCheckpointFocusedSeconds >= CHECKPOINT_SECONDS) {
                    val stored = focusStore.load()
                    if (stored.status != FocusStatus.RUNNING || stored.endsAtMs != session.endsAtMs) break
                    syncNormalizedSession(stored.withClockAt(now), now)
                    lastCheckpointFocusedSeconds = focusedSeconds
                }
            }
        }
        tickerJob = job
        processTickerJob = job
    }

    private fun stopTicker() {
        val job = tickerJob
        job?.cancel()
        if (processTickerJob === job) processTickerJob = null
        tickerJob = null
    }

    class Factory(
        private val focusStore: FocusStore,
        private val settingsStore: SettingsStore,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val planRepository: PlanRepository,
        private val studySessionRepository: StudySessionRepository,
        private val rollingPlanRepository: RollingPlanRepository,
        private val appScope: CoroutineScope,
        private val onFocusLockStart: () -> Unit = {},
        private val onFocusLockStop: () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FocusViewModel(
                focusStore,
                settingsStore,
                examPackRepository,
                attemptRepository,
                planRepository,
                studySessionRepository,
                rollingPlanRepository,
                appScope,
                onFocusLockStart,
                onFocusLockStop,
            ) as T
    }

    private companion object {
        const val CHECKPOINT_SECONDS = 60
        val sessionMutationMutex = Mutex()
        var processTickerJob: Job? = null
    }
}

private fun StudyPlanBlock.focusTag(): BlockTag = when (activityType) {
    StudyActivityType.REVISION -> BlockTag.REVISE
    StudyActivityType.PRACTICE, StudyActivityType.MOCK_TEST -> BlockTag.PRACTICE
    else -> BlockTag.READ
}

private fun StudyPlanBlock.toFocusBlock(title: String, subtitle: String): FocusBlockRef = FocusBlockRef(
    id = id,
    title = title,
    subtitle = subtitle,
    tag = focusTag(),
    sessionId = "",
    nodeKey = nodeId.orEmpty(),
    isRevision = activityType == StudyActivityType.REVISION,
)

private fun StudyPlanBlock.toFocusQueueItem(block: FocusBlockRef): FocusQueueItem = FocusQueueItem(
    id = id,
    title = block.title,
    subtitle = block.subtitle,
    tag = focusTag(),
    durationMinutes = plannedMinutes,
    completed = status == PlanBlockStatus.COMPLETED,
)
