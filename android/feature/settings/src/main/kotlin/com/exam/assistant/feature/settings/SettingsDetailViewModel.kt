package com.exam.assistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SettingsDetailUiState(
    val weekdayHours: Float = 4f,
    val weekendHours: Float = 7f,
    val studyPlace: String = "",
    val focusDurationMinutes: Int = 50,
    val showClearDialog: Boolean = false,
)

class SettingsDetailViewModel(
    private val planStore: PlanStore,
    private val settingsStore: SettingsStore,
    private val focusStore: FocusStore,
    private val syllabusStore: SyllabusStore,
    private val studySessionStore: StudySessionStore,
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsDetailViewModel(
                planStore,
                settingsStore,
                focusStore,
                syllabusStore,
                studySessionStore,
            ) as T
    }
}
