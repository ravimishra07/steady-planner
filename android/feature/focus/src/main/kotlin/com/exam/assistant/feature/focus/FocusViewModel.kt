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
import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusBlockRef
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.domain.blockIsDone
import com.exam.assistant.domain.demoTodayBlocks
import com.exam.assistant.domain.leafKeysForNodeKey
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
            val duration = settingsStore.focusDurationSec()
            val block = nextBlock()
            val session = FocusSession(
                status = FocusStatus.RUNNING,
                durationSec = duration,
                remainingSec = duration,
                endsAtMs = System.currentTimeMillis() + duration * 1000L,
                block = block,
                completedToday = focusStore.load().completedToday,
            )
            focusStore.save(session)
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
            applySession(session)
            startTickerIfNeeded(FocusStatus.RUNNING)
            onFocusLockStart()
        }
    }

    fun requestStop() {
        _state.update { it.copy(showStopDialog = true) }
    }

    fun dismissStopDialog() {
        _state.update { it.copy(showStopDialog = false) }
    }

    fun confirmStop() {
        viewModelScope.launch {
            val current = focusStore.load()
            persistSessionCompletion(current.block)
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
        val session = studySessionStore.loadAll().firstOrNull { it.id == block.sessionId } ?: return
        if (session.completed) return
        studySessionStore.upsert(session.copy(completed = true, runningEndsAtMs = null))
        val sections = syllabusRepository.tier1Sections()
        val leaves = leafKeysForNodeKey(sections, block.nodeKey)
        if (leaves.isNotEmpty()) {
            val stored = syllabusStore.load()
            syllabusStore.save(stored.copy(doneLeaves = stored.doneLeaves + leaves))
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
        val prefs = planStore.loadTodayPrefs()
        val done = prefs.blocksDone
        return demoTodayBlocks()
            .firstOrNull { !it.isBreak && !blockIsDone(done, it.id) }
            ?.let {
                FocusBlockRef(
                    id = it.id,
                    title = it.title,
                    subtitle = it.subtitle,
                    tag = it.tag,
                )
            }
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
                statusLabel = statusLabel(clocked.status),
                blockTitle = block?.title.orEmpty(),
                blockSubtitle = block?.subtitle.orEmpty(),
                blockTag = block?.tag,
                hasBlock = block != null,
            )
        }
        if (block == null && clocked.status == FocusStatus.IDLE) {
            viewModelScope.launch {
                val next = nextBlock()
                _state.update {
                    it.copy(
                        blockTitle = next?.title.orEmpty(),
                        blockSubtitle = next?.subtitle.orEmpty(),
                        blockTag = next?.tag,
                        hasBlock = next != null,
                    )
                }
            }
        }
    }

    private fun statusLabel(status: FocusStatus): String = when (status) {
        FocusStatus.RUNNING -> "running"
        FocusStatus.PAUSED -> "paused"
        FocusStatus.DONE -> "complete"
        FocusStatus.IDLE -> "idle"
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
                appScope,
                onFocusLockStart,
                onFocusLockStop,
            ) as T
    }
}
