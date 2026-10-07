package com.exam.assistant.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.domain.dayLog
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads one day's sessions; [dayLog] builds the list, with chapter renames from the pack. */
class DayLogViewModel(
    private val date: LocalDate,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val studySessionRepository: StudySessionRepository,
    private val planRepository: PlanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DayLogUiState(date = date))
    val state: StateFlow<DayLogUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        try {
            val attempt = attemptRepository.activeAttempt()
            if (attempt == null) {
                _state.update { it.copy(loading = false, log = null, loadFailed = false) }
                return@launch
            }
            val pack = examPackRepository.examPackFor(attempt.examId, attempt.id)
            val sessions = studySessionRepository.forDate(attempt.id, date)
            val customTitles = planRepository.planBetween(attempt.id, date, date)
                .mapNotNull { block -> block.customTitle?.let { block.id to it } }
                .toMap()
            _state.update {
                it.copy(loading = false, log = dayLog(date, sessions, pack, customTitles), loadFailed = false)
            }
        } catch (_: Throwable) {
            _state.update { it.copy(loading = false, loadFailed = true) }
        }
    }

    class Factory(
        private val date: LocalDate,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val studySessionRepository: StudySessionRepository,
        private val planRepository: PlanRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DayLogViewModel(
            date, examPackRepository, attemptRepository, studySessionRepository, planRepository,
        ) as T
    }
}
