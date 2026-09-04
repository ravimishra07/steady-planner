package com.exam.assistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.domain.StudyPreferences
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsDetailUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saveDone: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val weekdayHours: Float = 4f,
    val weekendHours: Float = 7f,
    val studyPlace: String = "",
    val focusDurationMinutes: Int = 50,
    val showClearDialog: Boolean = false,
    val clearing: Boolean = false,
    val clearFailed: Boolean = false,
    val showDiscardDialog: Boolean = false,
)

class SettingsDetailViewModel(
    private val planStore: PlanStore,
    private val settingsStore: SettingsStore,
    private val focusStore: FocusStore,
    private val syllabusStore: SyllabusStore,
    private val studySessionStore: StudySessionStore,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val studyPreferenceRepository: StudyPreferenceRepository,
    private val rollingPlanRepository: RollingPlanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsDetailUiState())
    val state: StateFlow<SettingsDetailUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, loadFailed = false, saveFailed = false) }
        viewModelScope.launch {
            runCatching {
                val attempt = attemptRepository.activeAttempt()
                val preferences = attempt?.let { studyPreferenceRepository.forAttempt(it.id) }
                val focusSec = settingsStore.focusDurationSec()
                LoadedSettings(
                    weekdayHours = preferences?.weekdayTargetMinutes?.div(60f) ?: 4f,
                    weekendHours = preferences?.weekendTargetMinutes?.div(60f) ?: 7f,
                    studyPlace = preferences?.defaultStudyPlace.orEmpty(),
                    focusDurationMinutes = preferences?.preferredSessionMinutes
                        ?: focusSec / 60,
                )
            }.onSuccess { loaded ->
                _state.update {
                    it.copy(
                        loading = false,
                        loadFailed = false,
                        weekdayHours = loaded.weekdayHours,
                        weekendHours = loaded.weekendHours,
                        studyPlace = loaded.studyPlace,
                        focusDurationMinutes = loaded.focusDurationMinutes,
                        hasUnsavedChanges = false,
                    )
                }
            }.onFailure {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun setWeekdayHours(value: Float) = updateDraft {
        copy(weekdayHours = steppedHours(value))
    }

    fun setWeekendHours(value: Float) = updateDraft {
        copy(weekendHours = steppedHours(value))
    }

    fun setStudyPlace(value: String) = updateDraft {
        copy(studyPlace = value)
    }

    fun setFocusDurationMinutes(minutes: Int) = updateDraft {
        copy(focusDurationMinutes = minutes)
    }

    fun save() {
        val draft = _state.value
        if (draft.loading || draft.saving || !draft.hasUnsavedChanges) return
        _state.update { it.copy(saving = true, saveFailed = false, saveDone = false) }
        viewModelScope.launch {
            runCatching {
                val attempt = attemptRepository.activeAttempt()
                    ?: error("An active attempt is required to save study preferences")
                val currentPreferences = studyPreferenceRepository.forAttempt(attempt.id)
                    ?: StudyPreferences(attemptId = attempt.id)
                studyPreferenceRepository.upsert(
                    currentPreferences.copy(
                        weekdayTargetMinutes = (draft.weekdayHours * 60).roundToInt(),
                        weekendTargetMinutes = (draft.weekendHours * 60).roundToInt(),
                        preferredSessionMinutes = draft.focusDurationMinutes,
                        defaultStudyPlace = draft.studyPlace.trim().ifBlank { null },
                    ),
                )
                check(rollingPlanRepository.replenish(fromDate = LocalDate.now()) != null) {
                    "The study plan could not be regenerated"
                }
                settingsStore.setFocusDurationSec(draft.focusDurationMinutes * 60)
            }.onSuccess {
                _state.update {
                    it.copy(
                        saving = false,
                        saveDone = true,
                        hasUnsavedChanges = false,
                        studyPlace = draft.studyPlace.trim(),
                    )
                }
            }.onFailure {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    fun dismissSaveResult() {
        _state.update { it.copy(saveDone = false, saveFailed = false) }
    }

    /** Returns true when navigation can proceed immediately. */
    fun requestBack(): Boolean {
        if (_state.value.saving) return false
        if (!_state.value.hasUnsavedChanges) return true
        _state.update { it.copy(showDiscardDialog = true) }
        return false
    }

    fun dismissDiscard() {
        _state.update { it.copy(showDiscardDialog = false) }
    }

    fun confirmDiscard() {
        _state.update { it.copy(showDiscardDialog = false, hasUnsavedChanges = false) }
    }

    fun requestClear() {
        if (!_state.value.saving) _state.update { it.copy(showClearDialog = true, clearFailed = false) }
    }

    fun dismissClear() {
        if (!_state.value.clearing) _state.update { it.copy(showClearDialog = false) }
    }

    fun dismissClearError() {
        _state.update { it.copy(clearFailed = false) }
    }

    fun confirmClear(onCleared: () -> Unit) {
        if (_state.value.clearing) return
        _state.update { it.copy(clearing = true, clearFailed = false) }
        viewModelScope.launch {
            runCatching {
                attemptRepository.activeAttempt()?.let { attempt ->
                    examPackRepository.clearCustomChapters(attempt.examId)
                    attemptRepository.deleteAttemptAndAllData(attempt.id)
                }
                planStore.clear()
                syllabusStore.clear()
                focusStore.clear()
                studySessionStore.clear()
            }.onSuccess {
                _state.update { it.copy(clearing = false, showClearDialog = false) }
                onCleared()
            }.onFailure {
                _state.update {
                    it.copy(clearing = false, showClearDialog = false, clearFailed = true)
                }
            }
        }
    }

    private fun updateDraft(transform: SettingsDetailUiState.() -> SettingsDetailUiState) {
        _state.update { current ->
            if (current.loading || current.saving) current
            else current.transform().copy(
                hasUnsavedChanges = true,
                saveDone = false,
                saveFailed = false,
            )
        }
    }

    class Factory(
        private val planStore: PlanStore,
        private val settingsStore: SettingsStore,
        private val focusStore: FocusStore,
        private val syllabusStore: SyllabusStore,
        private val studySessionStore: StudySessionStore,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val studyPreferenceRepository: StudyPreferenceRepository,
        private val rollingPlanRepository: RollingPlanRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsDetailViewModel(
                planStore = planStore,
                settingsStore = settingsStore,
                focusStore = focusStore,
                syllabusStore = syllabusStore,
                studySessionStore = studySessionStore,
                examPackRepository = examPackRepository,
                attemptRepository = attemptRepository,
                studyPreferenceRepository = studyPreferenceRepository,
                rollingPlanRepository = rollingPlanRepository,
            ) as T
    }
}

private data class LoadedSettings(
    val weekdayHours: Float,
    val weekendHours: Float,
    val studyPlace: String,
    val focusDurationMinutes: Int,
)

internal fun steppedHours(value: Float): Float = (value * 2).roundToInt() / 2f
