package com.exam.assistant.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SavedPlan
import com.exam.assistant.domain.SSC_CGL_RAW_HOURS
import com.exam.assistant.domain.availableHours
import com.exam.assistant.domain.cushion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OnboardingViewModel(
    private val planStore: PlanStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun back() {
        _state.update { current ->
            val prev = current.step.previous() ?: return@update current
            current.copy(step = prev)
        }
    }

    fun continueFromCurrent() {
        val current = _state.value
        when (current.step) {
            OnboardingStep.Exam -> {
                if (ExamCatalog.isAvailable(current.examId)) advance()
            }

            OnboardingStep.Date,
            OnboardingStep.Shape,
            -> advance()

            OnboardingStep.Hours -> {
                val c = computeCushion(current)
                _state.update { it.copy(step = OnboardingStep.Cushion, cushion = c) }
            }

            OnboardingStep.Cushion -> finish(current)
        }
    }

    fun selectExam(examId: String) {
        if (!ExamCatalog.isAvailable(examId)) return
        _state.update { it.copy(examId = examId) }
    }

    fun useUnannouncedDate() {
        _state.update { it.copy(daysUntilExam = UNANNOUNCED_DAYS) }
    }

    fun selectWork(id: String) {
        val shape = DAY_SHAPES.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                workId = id,
                weekdayHours = shape.weekdayHours,
                weekendHours = shape.weekendHours,
            )
        }
    }

    fun setWeekdayHours(hours: Float) {
        _state.update { it.copy(weekdayHours = hours.halfHourStep()) }
    }

    fun setWeekendHours(hours: Float) {
        _state.update { it.copy(weekendHours = hours.halfHourStep()) }
    }

    fun setStudyPlace(place: String) {
        _state.update { it.copy(studyPlace = place) }
    }

    fun totalHours(state: OnboardingUiState = _state.value): Int =
        availableHours(
            days = state.daysUntilExam,
            weekdayHours = state.weekdayHours.toDouble(),
            weekendHours = state.weekendHours.toDouble(),
        )

    private fun advance() {
        _state.update { current ->
            val next = current.step.next() ?: return@update current
            current.copy(step = next)
        }
    }

    private fun computeCushion(state: OnboardingUiState) = cushion(
        rawHours = SSC_CGL_RAW_HOURS,
        days = state.daysUntilExam,
        weekdayHours = state.weekdayHours.toDouble(),
        weekendHours = state.weekendHours.toDouble(),
    )

    private fun finish(state: OnboardingUiState) {
        if (state.finishing) return
        _state.update { it.copy(finishing = true) }
        viewModelScope.launch {
            planStore.save(
                SavedPlan(
                    examId = state.examId,
                    daysUntilExam = state.daysUntilExam,
                    workId = state.workId,
                    weekdayHours = state.weekdayHours,
                    weekendHours = state.weekendHours,
                    studyPlace = state.studyPlace.trim(),
                ),
            )
            _state.update { it.copy(finishing = false) }
            _finished.value = true
        }
    }

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    class Factory(private val planStore: PlanStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OnboardingViewModel(planStore) as T
    }
}

private fun Float.halfHourStep(): Float = (this * 2).roundToInt() / 2f
