package com.exam.assistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.domain.generateBackfillHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

private const val SEED_HISTORY_DAYS = 45

data class SettingsDetailUiState(
    val weekdayHours: Float = 4f,
    val weekendHours: Float = 7f,
    val studyPlace: String = "",
    val focusDurationMinutes: Int = 50,
    val showClearDialog: Boolean = false,
    val showSeedDialog: Boolean = false,
    val seeding: Boolean = false,
    val seedDone: Boolean = false,
    val seedError: String? = null,
)

class SettingsDetailViewModel(
    private val planStore: PlanStore,
    private val settingsStore: SettingsStore,
    private val focusStore: FocusStore,
    private val syllabusStore: SyllabusStore,
    private val studySessionStore: StudySessionStore,
    private val syllabusRepository: SyllabusRepository,
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

    /** Backfills 45 days of completed history from the real syllabus — for previewing a full app, not real study data. */
    fun confirmSeed() {
        _state.update { it.copy(showSeedDialog = false, seeding = true, seedError = null) }
        viewModelScope.launch {
            try {
                val plan = planStore.load()
                val sections = syllabusRepository.tier1Sections()
                val (sessions, doneLeaves) = generateBackfillHistory(
                    sections = sections,
                    today = LocalDate.now(),
                    days = SEED_HISTORY_DAYS,
                    // Guard against a near-zero plan silently producing an empty-looking backfill.
                    weekdayHours = (plan?.weekdayHours ?: 4f).coerceAtLeast(2f),
                    weekendHours = (plan?.weekendHours ?: 7f).coerceAtLeast(2f),
                )
                if (sessions.isEmpty()) {
                    _state.update {
                        it.copy(seeding = false, seedError = "No syllabus topics found to schedule — nothing was added.")
                    }
                    return@launch
                }
                studySessionStore.upsertAll(sessions)
                val storedSyllabus = syllabusStore.load()
                syllabusStore.save(storedSyllabus.copy(doneLeaves = storedSyllabus.doneLeaves + doneLeaves))
                _state.update { it.copy(seeding = false, seedDone = true) }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(seeding = false, seedError = error.message ?: "Could not generate history.")
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

    class Factory(
        private val planStore: PlanStore,
        private val settingsStore: SettingsStore,
        private val focusStore: FocusStore,
        private val syllabusStore: SyllabusStore,
        private val studySessionStore: StudySessionStore,
        private val syllabusRepository: SyllabusRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsDetailViewModel(
                planStore,
                settingsStore,
                focusStore,
                syllabusStore,
                studySessionStore,
                syllabusRepository,
            ) as T
    }
}
