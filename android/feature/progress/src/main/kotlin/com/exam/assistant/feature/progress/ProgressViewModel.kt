package com.exam.assistant.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.domain.SSC_CGL_RAW_HOURS
import com.exam.assistant.domain.computeSyllabusProgress
import com.exam.assistant.domain.cushion
import com.exam.assistant.domain.demoTodayBlocks
import com.exam.assistant.domain.doneMinutes
import com.exam.assistant.domain.todayBudget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class ProgressViewModel(
    private val planStore: PlanStore,
    private val syllabusRepository: SyllabusRepository,
    private val syllabusStore: SyllabusStore,
    private val focusStore: FocusStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val plan = planStore.load()
            if (plan == null) {
                _state.update { it.copy(loading = false, hasPlan = false) }
                return@launch
            }
            val todayPrefs = planStore.loadTodayPrefs()
            val syllabusUi = syllabusStore.load()
            val sections = syllabusRepository.tier1Sections()
            val syllabus = computeSyllabusProgress(sections, syllabusUi.doneLeaves)
            val cushion = cushion(
                rawHours = SSC_CGL_RAW_HOURS,
                days = plan.daysUntilExam,
                weekdayHours = plan.weekdayHours.toDouble(),
                weekendHours = plan.weekendHours.toDouble(),
            )
            val doneMin = doneMinutes(demoTodayBlocks(), todayPrefs.blocksDone)
            val budget = todayBudget(plan.weekdayHours, plan.weekendHours)
            val todayPct = if (budget > 0) {
                ((doneMin.toFloat() / (budget * 60)) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val focus = focusStore.load()
            _state.update {
                ProgressUiState(
                    loading = false,
                    hasPlan = true,
                    daysUntilExam = plan.daysUntilExam,
                    gapHours = kotlin.math.abs(cushion.gap),
                    isShort = cushion.isShort,
                    needHours = cushion.need,
                    haveHours = cushion.have,
                    coveragePercent = cushion.coverage,
                    todayDoneHours = String.format(Locale.US, "%.1f", doneMin / 60.0),
                    todayBudgetHours = budget,
                    todayPercent = todayPct,
                    focusSessionsToday = focus.completedToday,
                    syllabusHoursDone = syllabus.hoursDone,
                    syllabusHoursTotal = syllabus.hoursTotal,
                    sections = syllabus.sections,
                )
            }
        }
    }

    class Factory(
        private val planStore: PlanStore,
        private val syllabusRepository: SyllabusRepository,
        private val syllabusStore: SyllabusStore,
        private val focusStore: FocusStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProgressViewModel(planStore, syllabusRepository, syllabusStore, focusStore) as T
    }
}
