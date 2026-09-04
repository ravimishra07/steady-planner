package com.exam.assistant.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusBlockRef
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.domain.leafKeysForNodeKey
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.StudySessionSegment
import com.exam.assistant.domain.StudySessionStatus
import com.exam.assistant.domain.StudySessionRecord
import com.exam.assistant.domain.currentMinuteOfDay
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

class FocusViewModel(
    private val focusStore: FocusStore,
    private val planStore: PlanStore,
    private val settingsStore: SettingsStore,
    private val studySessionStore: StudySessionStore,
    private val syllabusRepository: SyllabusRepository,
    private val syllabusStore: SyllabusStore,
    private val attemptRepository: AttemptRepository,
    private val planRepository: PlanRepository,
    private val studySessionRepository: StudySessionRepository,
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
    private var selectedBlock: FocusBlockRef? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val session = focusStore.load().withClockNow()
            if (session.status == FocusStatus.DONE && session.remainingSec == 0 && session.endsAtMs == null) {
                // keep done state
            } else if (session.status == FocusStatus.RUNNING) {
                val endsAt = session.endsAtMs
                if (endsAt != null) {
                    val left = ((endsAt - System.currentTimeMillis()) / 1000).toInt()
                    if (left <= 0) {
                        completeSession(session)
                        return@launch
                    }
                }
            }
            applySession(session)
            ensureNormalizedSession(session)
            startTickerIfNeeded(session.status)
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
            val duration = block?.sessionId
                ?.let { id -> studySessionStore.sessionsFor(LocalDate.now()).firstOrNull { it.id == id } }
                ?.durationMinutes
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
            startTickerIfNeeded(FocusStatus.RUNNING)
            onFocusLockStart()
        }
    }

    fun pause() {
        viewModelScope.launch {
            val current = focusStore.load().withClockNow()
            if (current.status != FocusStatus.RUNNING) return@launch
            val endsAt = current.endsAtMs ?: return@launch
            val left = ((endsAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            val session = current.copy(status = FocusStatus.PAUSED, remainingSec = left, endsAtMs = null)
            focusStore.save(session)
            syncNormalizedSession(session)
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
            startTickerIfNeeded(FocusStatus.RUNNING)
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
            val chosen = studySessionStore.sessionsFor(LocalDate.now()).firstOrNull { it.id == id } ?: return@launch
            selectedBlock = chosen.toFocusBlock()
            _state.update {
                it.copy(
                    durationMinutes = chosen.durationMinutes,
                    blockTitle = chosen.title,
                    blockSubtitle = chosen.sectionName,
                    blockTag = chosen.focusTag(),
                    hasBlock = true,
                    queue = it.queue.filterNot { row -> row.id == id },
                )
            }
        }
    }

    fun extendFiveMinutes() {
        viewModelScope.launch {
            val current = focusStore.load().withClockNow()
            if (current.status != FocusStatus.RUNNING && current.status != FocusStatus.PAUSED) return@launch
            val updated = current.copy(
                durationSec = current.durationSec + 5 * 60,
                remainingSec = current.remainingSec + 5 * 60,
                endsAtMs = current.endsAtMs?.plus(5 * 60_000L),
            )
            focusStore.save(updated)
            applySession(updated)
            syncNormalizedSession(updated)
        }
    }

    fun dismissStopDialog() {
        _state.update { it.copy(showStopDialog = false) }
    }

    fun confirmStop() {
        viewModelScope.launch {
            val current = focusStore.load()
            abandonNormalizedSession()
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
    }

    /** Marks the underlying study session complete and folds its topic into syllabus progress. */
    private suspend fun persistSessionCompletion(block: FocusBlockRef?) {
        if (block == null || block.sessionId.isBlank()) return
        val session = studySessionStore.loadAll().firstOrNull { it.id == block.sessionId }
        if (session != null && !session.completed) {
            studySessionStore.upsert(session.copy(completed = true, runningEndsAtMs = null))
            val sections = syllabusRepository.tier1Sections()
            val leaves = leafKeysForNodeKey(sections, block.nodeKey)
            if (leaves.isNotEmpty()) {
                val stored = syllabusStore.load()
                syllabusStore.save(stored.copy(doneLeaves = stored.doneLeaves + leaves))
            }
        }
        completeNormalizedSession()
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

    private suspend fun completeSession(current: FocusSession) {
        persistSessionCompletion(current.block)
        val session = current.copy(
            status = FocusStatus.DONE,
            remainingSec = 0,
            endsAtMs = null,
            completedToday = current.completedToday + 1,
        )
        focusStore.save(session)
        applySession(session)
        stopTicker()
        onFocusLockStop()
    }

    private suspend fun nextBlock(): FocusBlockRef? {
        val sessions = studySessionStore.sessionsFor(LocalDate.now())
            .filterNot { it.completed }
            .sortedBy { it.startMinuteOfDay }
        val now = currentMinuteOfDay()
        return (sessions.firstOrNull { now in it.startMinuteOfDay until (it.startMinuteOfDay + it.durationMinutes) }
            ?: sessions.firstOrNull { it.startMinuteOfDay >= now }
            ?: sessions.firstOrNull())?.toFocusBlock()
    }

    private suspend fun ensureNormalizedSession(focus: FocusSession) {
        if (focus.status != FocusStatus.RUNNING && focus.status != FocusStatus.PAUSED) return
        val attempt = attemptRepository.activeAttempt() ?: return
        val existing = studySessionRepository.activeSession(attempt.id)
        if (existing != null) return
        val block = focus.block ?: return
        val planBlock = planRepository.byId(block.id)
        val now = System.currentTimeMillis()
        val session = StudySession(
            id = "focus_${UUID.randomUUID()}",
            attemptId = attempt.id,
            nodeId = block.nodeKey.ifBlank { planBlock?.nodeId },
            subjectId = planBlock?.subjectId,
            planBlockId = planBlock?.id,
            activityType = if (block.isRevision) StudyActivityType.REVISION else planBlock?.activityType ?: StudyActivityType.LEARN,
            startedAtEpochMs = now,
            endedAtEpochMs = null,
            studyDate = LocalDate.now(),
            timeZoneId = ZoneId.systemDefault().id,
            focusedSeconds = (focus.durationSec - focus.remainingSec).coerceAtLeast(0),
            pausedSeconds = 0,
            status = if (focus.status == FocusStatus.PAUSED) StudySessionStatus.PAUSED else StudySessionStatus.RUNNING,
            focusLockUsed = false,
            interruptionCount = 0,
            customTitle = if (planBlock?.nodeId == null) block.title else null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        studySessionRepository.startSession(
            session,
            StudySessionSegment(
                id = "${session.id}_0",
                sessionId = session.id,
                nodeId = session.nodeId,
                subjectId = session.subjectId,
                startedAtEpochMs = now,
                endedAtEpochMs = null,
                focusedSeconds = 0,
                order = 0,
            ),
        )
    }

    private suspend fun syncNormalizedSession(focus: FocusSession) {
        val attempt = attemptRepository.activeAttempt() ?: return
        val active = studySessionRepository.activeSession(attempt.id) ?: run {
            ensureNormalizedSession(focus)
            return
        }
        studySessionRepository.updateRunning(
            active.copy(
                focusedSeconds = (focus.durationSec - focus.remainingSec).coerceAtLeast(0),
                status = if (focus.status == FocusStatus.PAUSED) StudySessionStatus.PAUSED else StudySessionStatus.RUNNING,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun completeNormalizedSession() {
        val attempt = attemptRepository.activeAttempt() ?: return
        val active = studySessionRepository.activeSession(attempt.id) ?: return
        val focus = focusStore.load()
        studySessionRepository.completeSession(
            active.copy(focusedSeconds = focus.durationSec.coerceAtLeast(active.focusedSeconds)),
            today = active.studyDate,
            nowMs = System.currentTimeMillis(),
        )
    }

    private suspend fun abandonNormalizedSession() {
        val attempt = attemptRepository.activeAttempt() ?: return
        val active = studySessionRepository.activeSession(attempt.id) ?: return
        studySessionRepository.abandonSession(active, System.currentTimeMillis())
    }

    private fun applySession(session: FocusSession) {
        val clocked = session.withClockNow()
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
            )
        }
        if (block == null && clocked.status == FocusStatus.IDLE) {
            viewModelScope.launch {
                val next = nextBlock()
                val sessions = studySessionStore.sessionsFor(LocalDate.now()).sortedBy { row -> row.startMinuteOfDay }
                _state.update {
                    it.copy(
                        blockTitle = next?.title.orEmpty(),
                        blockSubtitle = next?.subtitle.orEmpty(),
                        blockTag = next?.tag,
                        hasBlock = next != null,
                        durationMinutes = next?.let { row -> sessions.firstOrNull { it.id == row.id }?.durationMinutes }
                            ?: it.durationMinutes,
                        queue = sessions
                            .filterNot { row -> row.id == next?.id }
                            .take(6)
                            .map(StudySessionRecord::toFocusQueueItem),
                    )
                }
            }
        } else if (clocked.status == FocusStatus.IDLE) {
            viewModelScope.launch {
                val sessions = studySessionStore.sessionsFor(LocalDate.now()).sortedBy { row -> row.startMinuteOfDay }
                _state.update { current ->
                    current.copy(
                        queue = sessions
                            .filterNot { row -> row.id == block?.id }
                            .take(6)
                            .map(StudySessionRecord::toFocusQueueItem),
                    )
                }
            }
        }
    }

    private fun startTickerIfNeeded(status: FocusStatus) {
        if (status != FocusStatus.RUNNING) {
            stopTicker()
            return
        }
        tickerJob?.cancel()
        tickerJob = appScope.launch {
            while (isActive) {
                delay(1000)
                val session = focusStore.load().withClockNow()
                if (session.status == FocusStatus.RUNNING) {
                    val endsAt = session.endsAtMs
                    if (endsAt != null) {
                        val left = ((endsAt - System.currentTimeMillis()) / 1000).toInt()
                        if (left <= 0) {
                            completeSession(session)
                            break
                        }
                        _remainingSeconds.value = left
                        focusStore.save(session.copy(remainingSec = left))
                        syncNormalizedSession(session.copy(remainingSec = left))
                    }
                } else {
                    break
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun onCleared() {
        stopTicker()
        super.onCleared()
    }

    class Factory(
        private val focusStore: FocusStore,
        private val planStore: PlanStore,
        private val settingsStore: SettingsStore,
        private val studySessionStore: StudySessionStore,
        private val syllabusRepository: SyllabusRepository,
        private val syllabusStore: SyllabusStore,
        private val attemptRepository: AttemptRepository,
        private val planRepository: PlanRepository,
        private val studySessionRepository: StudySessionRepository,
        private val appScope: CoroutineScope,
        private val onFocusLockStart: () -> Unit = {},
        private val onFocusLockStop: () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FocusViewModel(
                focusStore,
                planStore,
                settingsStore,
                studySessionStore,
                syllabusRepository,
                syllabusStore,
                attemptRepository,
                planRepository,
                studySessionRepository,
                appScope,
                onFocusLockStart,
                onFocusLockStop,
            ) as T
    }
}

private fun StudySessionRecord.focusTag(): BlockTag = when {
    isRevision -> BlockTag.REVISE
    title.contains("practice", ignoreCase = true) -> BlockTag.PRACTICE
    else -> BlockTag.READ
}

private fun StudySessionRecord.toFocusBlock(): FocusBlockRef = FocusBlockRef(
    id = id,
    title = title,
    subtitle = sectionName,
    tag = focusTag(),
    sessionId = id,
    nodeKey = nodeKey,
    isRevision = isRevision,
)

private fun StudySessionRecord.toFocusQueueItem(): FocusQueueItem = FocusQueueItem(
    id = id,
    title = title,
    subtitle = sectionName,
    tag = focusTag(),
    durationMinutes = durationMinutes,
    completed = completed,
)
