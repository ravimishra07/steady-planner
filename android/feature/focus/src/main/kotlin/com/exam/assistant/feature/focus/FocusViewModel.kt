package com.exam.assistant.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.FocusController
import com.exam.assistant.core.data.FocusTransition
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusBlockRef
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.StudyOutcome
import com.exam.assistant.domain.StudyPreferences
import com.exam.assistant.domain.StudyPlanBlock
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.chapterOf
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.leafIds
import com.exam.assistant.domain.findSubjectOf
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusViewModel(
    private val focusController: FocusController,
    private val settingsStore: SettingsStore,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val planRepository: PlanRepository,
    private val studySessionRepository: StudySessionRepository,
    private val rollingPlanRepository: RollingPlanRepository,
    private val studyPreferenceRepository: StudyPreferenceRepository,
    private val topicProgressRepository: TopicProgressRepository,
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
        // Every change — from this screen, a notification action or the end alarm — arrives here.
        viewModelScope.launch {
            focusController.transitions.collect { applyTransition(it) }
        }
        viewModelScope.launch {
            _state.update { it.copy(askNotificationPermission = !settingsStore.notificationPromptShown()) }
        }
        refresh()
    }

    /** Re-reads the clock from ends_at; called on creation and every time the screen resumes. */
    fun refresh() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val stored = focusController.load()
            if (stored.isExpiredAt(now)) {
                focusController.completeIfExpired(now)
                return@launch
            }
            var session = stored.withClockAt(now)
            if (session.status == FocusStatus.IDLE) {
                // The idle timer shows the one stored session length, even if Settings changed it since.
                val preferred = preferredSessionSec()
                if (session.durationSec != preferred) {
                    session = session.copy(durationSec = preferred, remainingSec = preferred)
                    focusController.setIdle(session)
                    return@launch
                }
            }
            if (session.status == FocusStatus.DONE && lastCompletedSessionId == null) {
                // Completed by the alarm while this screen was gone: still offer the outcome and learn prompt.
                focusController.consumeCompletion()?.let { completed ->
                    lastCompletedSessionId = completed.id
                    val prompt = learnPromptFor(completed)
                    _state.update { it.copy(learnPrompt = prompt) }
                }
            }
            applySession(session)
            if (session.status == FocusStatus.RUNNING || session.status == FocusStatus.PAUSED) {
                focusController.syncLive(now)
            }
            startTickerIfNeeded(session)
        }
    }

    /** Records that the notification permission prompt has been shown once. */
    fun markNotificationPromptShown() {
        _state.update { it.copy(askNotificationPermission = false) }
        viewModelScope.launch { settingsStore.setNotificationPromptShown() }
    }

    fun startSession() {
        viewModelScope.launch {
            val block = selectedBlock ?: nextBlock()
            val duration = block?.id
                ?.let { id -> planRepository.byId(id) }
                ?.plannedMinutes
                ?.times(60)
                ?: preferredSessionSec()
            selectedBlock = null
            focusController.start(
                FocusSession(
                    status = FocusStatus.RUNNING,
                    durationSec = duration,
                    remainingSec = duration,
                    endsAtMs = System.currentTimeMillis() + duration * 1000L,
                    block = block,
                    completedToday = focusController.load().completedToday,
                ),
            )
        }
    }

    fun pause() {
        viewModelScope.launch { focusController.pause() }
    }

    fun resume() {
        viewModelScope.launch { focusController.resume() }
    }

    fun requestStop() {
        _state.update { it.copy(showStopDialog = true) }
    }

    fun setDuration(minutes: Int) {
        if (minutes !in setOf(25, 50, 90)) return
        viewModelScope.launch {
            savePreferredSessionMinutes(minutes)
            val current = focusController.load()
            if (current.status == FocusStatus.IDLE) {
                focusController.setIdle(current.copy(durationSec = minutes * 60, remainingSec = minutes * 60))
            }
        }
    }

    /**
     * Session length has one home: [StudyPreferences.preferredSessionMinutes], which is also what
     * Settings edits and the planner reads. [SettingsStore] is only the fallback before any plan exists.
     */
    private suspend fun preferredSessionSec(): Int {
        val minutes = attemptRepository.activeAttempt()
            ?.let { studyPreferenceRepository.forAttempt(it.id)?.preferredSessionMinutes }
        return minutes?.times(60) ?: settingsStore.focusDurationSec()
    }

    private suspend fun savePreferredSessionMinutes(minutes: Int) {
        val attempt = attemptRepository.activeAttempt()
        if (attempt == null) {
            settingsStore.setFocusDurationSec(minutes * 60)
            return
        }
        val current = studyPreferenceRepository.forAttempt(attempt.id) ?: StudyPreferences(attemptId = attempt.id)
        studyPreferenceRepository.upsert(current.copy(preferredSessionMinutes = minutes))
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
        viewModelScope.launch { focusController.extend(FocusController.EXTEND_SECONDS) }
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

    /** "Yes" on the learned prompt: the same write as ticking Learned on the Syllabus screen. */
    fun markChapterLearned() {
        val prompt = _state.value.learnPrompt ?: return
        _state.update { it.copy(learnPrompt = null) }
        viewModelScope.launch {
            val attempt = attemptRepository.activeAttempt() ?: return@launch
            val chapter = examPackRepository.examPackFor(attempt.examId, attempt.id).chapterOf(prompt.chapterId) ?: return@launch
            val nowMs = System.currentTimeMillis()
            topicProgressRepository.setCovered(attempt.id, chapter.leafIds(), covered = true, nowMs = nowMs)
            rollingPlanRepository.replenish(nowMs = nowMs)
        }
    }

    fun dismissLearnPrompt() {
        _state.update { it.copy(learnPrompt = null) }
    }

    /** Only a completed Learn session on a chapter that is not yet fully covered earns the prompt. */
    private suspend fun learnPromptFor(session: StudySession?): LearnPrompt? {
        if (session == null || session.activityType != StudyActivityType.LEARN) return null
        // A titled session on a chapter is a coaching backlog lecture (§5.4), not Learn on the chapter.
        if (session.customTitle != null) return null
        val nodeId = session.nodeId ?: return null
        val attempt = attemptRepository.activeAttempt() ?: return null
        val chapter = examPackRepository.examPackFor(attempt.examId, attempt.id).chapterOf(nodeId) ?: return null
        val covered = topicProgressRepository.allOnce(attempt.id)
            .filter { it.status == com.exam.assistant.domain.TopicProgressStatus.COVERED }
            .map { it.nodeId }
            .toSet()
        if (chapter.leafIds().all { it in covered }) return null
        return LearnPrompt(chapterId = chapter.id, chapterTitle = chapter.title)
    }

    fun savePartialAndStop() {
        viewModelScope.launch {
            focusController.stopSavingPartial()
            dismissStopDialog()
        }
    }

    fun discardAndStop() {
        viewModelScope.launch {
            focusController.stopDiscarding()
            dismissStopDialog()
        }
    }

    fun startAnother() {
        viewModelScope.launch {
            val current = focusController.load()
            focusController.setIdle(
                current.copy(
                    status = FocusStatus.IDLE,
                    remainingSec = current.durationSec,
                    endsAtMs = null,
                    block = nextBlock(),
                ),
            )
            startSession()
        }
    }

    fun resetToIdle() {
        viewModelScope.launch {
            val current = focusController.load()
            focusController.setIdle(
                current.copy(
                    status = FocusStatus.IDLE,
                    remainingSec = current.durationSec,
                    endsAtMs = null,
                    block = nextBlock(),
                ),
            )
        }
    }

    private suspend fun applyTransition(transition: FocusTransition) {
        if (transition.didComplete) {
            focusController.consumeCompletion()
            lastCompletedSessionId = transition.completed?.id
            val prompt = learnPromptFor(transition.completed)
            _state.update { it.copy(learnPrompt = prompt, outcome = null) }
            dismissStopDialog()
        }
        val session = transition.session.withClockAt(System.currentTimeMillis())
        applySession(session)
        startTickerIfNeeded(session)
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
        val pack = attempt?.let { examPackRepository.examPackFor(it.examId, it.id) }
        val node = block.nodeId?.let { pack?.findNode(it) }
        val subject = block.nodeId?.let { pack?.findSubjectOf(it) }
        return block.toFocusBlock(
            title = block.customTitle ?: node?.title ?: "Study session",
            subtitle = subject?.name.orEmpty(),
        )
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
                learnPrompt = if (clocked.status == FocusStatus.DONE) it.learnPrompt else null,
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
        stopTicker()
        if (session.status != FocusStatus.RUNNING) return
        lastCheckpointFocusedSeconds = session.focusedSecondsAt(System.currentTimeMillis())
        // The UI clock only. Completion is guaranteed by the end alarm and by refresh().
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                if (session.isExpiredAt(now)) {
                    _remainingSeconds.value = 0
                    focusController.completeIfExpired(now)
                    break
                }
                val snapshot = session.withClockAt(now)
                _remainingSeconds.value = snapshot.remainingSec
                val focusedSeconds = snapshot.focusedSecondsAt(now)
                if (focusedSeconds - lastCheckpointFocusedSeconds >= CHECKPOINT_SECONDS) {
                    focusController.checkpoint(session.endsAtMs, now)
                    lastCheckpointFocusedSeconds = focusedSeconds
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    class Factory(
        private val focusController: FocusController,
        private val settingsStore: SettingsStore,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val planRepository: PlanRepository,
        private val studySessionRepository: StudySessionRepository,
        private val rollingPlanRepository: RollingPlanRepository,
        private val studyPreferenceRepository: StudyPreferenceRepository,
        private val topicProgressRepository: TopicProgressRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FocusViewModel(
                focusController,
                settingsStore,
                examPackRepository,
                attemptRepository,
                planRepository,
                studySessionRepository,
                rollingPlanRepository,
                studyPreferenceRepository,
                topicProgressRepository,
            ) as T
    }

    private companion object {
        const val CHECKPOINT_SECONDS = 60
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
