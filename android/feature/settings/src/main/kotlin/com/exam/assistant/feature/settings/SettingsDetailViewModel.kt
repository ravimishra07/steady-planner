package com.exam.assistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.NEET_EXAM_ID
import com.exam.assistant.domain.TopicProgress
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.generateDemoHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class SettingsDetailUiState(
    val weekdayHours: Float = 4f,
    val weekendHours: Float = 7f,
    val studyPlace: String = "",
    val focusDurationMinutes: Int = 50,
    val showClearDialog: Boolean = false,
    val showSeedDialog: Boolean = false,
    val seeding: Boolean = false,
    val seedDone: Boolean = false,
    val seedError: SeedHistoryError? = null,
)

enum class SeedHistoryError { NoTopics, Failed }

class SettingsDetailViewModel(
    private val planStore: PlanStore,
    private val settingsStore: SettingsStore,
    private val focusStore: FocusStore,
    private val syllabusStore: SyllabusStore,
    private val studySessionStore: StudySessionStore,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val topicProgressRepository: TopicProgressRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsDetailUiState())
    val state: StateFlow<SettingsDetailUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val plan = planStore.load()
            val focusSec = settingsStore.focusDurationSec()
            _state.update {
                it.copy(
                    weekdayHours = plan?.weekdayHours ?: 4f,
                    weekendHours = plan?.weekendHours ?: 7f,
                    studyPlace = plan?.studyPlace.orEmpty(),
                    focusDurationMinutes = focusSec / 60,
                )
            }
        }
    }

    fun setWeekdayHours(value: Float) {
        val stepped = (value * 2).roundToInt() / 2f
        _state.update { it.copy(weekdayHours = stepped) }
        persistHours()
    }

    fun setWeekendHours(value: Float) {
        val stepped = (value * 2).roundToInt() / 2f
        _state.update { it.copy(weekendHours = stepped) }
        persistHours()
    }

    fun setStudyPlace(value: String) {
        _state.update { it.copy(studyPlace = value) }
        persistHours()
    }

    fun setFocusDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsStore.setFocusDurationSec(minutes * 60)
            _state.update { it.copy(focusDurationMinutes = minutes) }
        }
    }

    fun requestClear() {
        _state.update { it.copy(showClearDialog = true) }
    }

    fun dismissClear() {
        _state.update { it.copy(showClearDialog = false) }
    }

    fun confirmClear(onCleared: () -> Unit) {
        viewModelScope.launch {
            attemptRepository.activeAttempt()?.let { attempt ->
                examPackRepository.clearCustomChapters(attempt.examId)
                attemptRepository.deleteAttemptAndAllData(attempt.id)
            }
            planStore.clear()
            syllabusStore.clear()
            focusStore.clear()
            studySessionStore.clear()
            dismissClear()
            onCleared()
        }
    }

    fun requestSeed() {
        _state.update { it.copy(showSeedDialog = true) }
    }

    fun dismissSeed() {
        _state.update { it.copy(showSeedDialog = false) }
    }

    /** Adds sample activity to every currently shipped data source so app screens stay in sync. */
    fun confirmSeed() {
        _state.update { it.copy(showSeedDialog = false, seeding = true, seedError = null) }
        viewModelScope.launch {
            try {
                val attempt = attemptRepository.activeAttempt()?.takeIf { it.examId == NEET_EXAM_ID }
                    ?: throw IllegalStateException("NEET attempt required")
                val pack = examPackRepository.examPackFor(NEET_EXAM_ID)
                val today = LocalDate.now()
                val (sessions, doneLeaves) = generateDemoHistory(
                    pack = pack,
                    today = today,
                )
                if (sessions.isEmpty()) {
                    _state.update {
                        it.copy(seeding = false, seedError = SeedHistoryError.NoTopics)
                    }
                    return@launch
                }
                studySessionStore.upsertAll(sessions)
                val storedSyllabus = syllabusStore.load()
                syllabusStore.save(storedSyllabus.copy(doneLeaves = storedSyllabus.doneLeaves + doneLeaves))
                seedRoomSyllabusProgress(attempt.id, doneLeaves)
                _state.update { it.copy(seeding = false, seedDone = true) }
            } catch (_: Throwable) {
                _state.update {
                    it.copy(seeding = false, seedError = SeedHistoryError.Failed)
                }
            }
        }
    }

    fun dismissSeedDone() {
        _state.update { it.copy(seedDone = false) }
    }

    fun dismissSeedError() {
        _state.update { it.copy(seedError = null) }
    }

    private fun persistHours() {
        val current = _state.value
        viewModelScope.launch {
            planStore.updateHours(current.weekdayHours, current.weekendHours, current.studyPlace)
        }
    }

    private suspend fun seedRoomSyllabusProgress(
        attemptId: String,
        doneLeaves: Set<String>,
    ) {
        val nowMs = System.currentTimeMillis()
        val progress = doneLeaves.map { nodeId ->
            TopicProgress(
                attemptId = attemptId,
                nodeId = nodeId,
                status = TopicProgressStatus.COVERED,
                coveredAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs,
            )
        }
        topicProgressRepository.upsertAll(progress)
    }

    class Factory(
        private val planStore: PlanStore,
        private val settingsStore: SettingsStore,
        private val focusStore: FocusStore,
        private val syllabusStore: SyllabusStore,
        private val studySessionStore: StudySessionStore,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val topicProgressRepository: TopicProgressRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsDetailViewModel(
                planStore,
                settingsStore,
                focusStore,
                syllabusStore,
                studySessionStore,
                examPackRepository,
                attemptRepository,
                topicProgressRepository,
            ) as T
    }
}
