package com.exam.assistant.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.SyllabusUiState
import com.exam.assistant.domain.InsightPlan
import com.exam.assistant.domain.InsightPeriod
import com.exam.assistant.domain.SyllabusSection
import com.exam.assistant.domain.computeInsights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProgressViewModel(
    private val planStore: PlanStore,
    private val syllabusRepository: SyllabusRepository,
    private val syllabusStore: SyllabusStore,
    private val studySessionStore: StudySessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    private var sections: List<SyllabusSection> = emptyList()
    private var storedSyllabus = SyllabusUiState()
    private var cachedPlan: InsightPlan? = null
    private var sessions = emptyList<com.exam.assistant.domain.StudySessionRecord>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val plan = planStore.load()
                sections = syllabusRepository.tier1Sections()
                storedSyllabus = syllabusStore.load()
                sessions = studySessionStore.loadAll()
                cachedPlan = plan?.let {
                    InsightPlan(
                        daysUntilTarget = it.daysUntilExam,
                        weekdayHours = it.weekdayHours,
                        weekendHours = it.weekendHours,
                    )
                }
                if (plan == null) {
                    _state.update { it.copy(loading = false, hasPlan = false, data = null, errorMessage = null) }
                } else {
                    rebuild()
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(loading = false, errorMessage = error.message ?: "Insights could not be loaded")
                }
            }
        }
    }

    fun selectPeriod(period: InsightPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        viewModelScope.launch { rebuild() }
    }

    fun openTargetManager() = _state.update { it.copy(showTargetManager = true) }

    fun closeTargetManager() = _state.update { it.copy(showTargetManager = false) }

    fun toggleSectionTarget(key: String) {
        val nextExcluded = storedSyllabus.excludedSectionKeys.toMutableSet()
        if (!nextExcluded.add(key)) nextExcluded.remove(key)
        storedSyllabus = storedSyllabus.copy(excludedSectionKeys = nextExcluded)
        viewModelScope.launch {
            syllabusStore.save(storedSyllabus)
            rebuild()
        }
    }

    private suspend fun rebuild() {
        val plan = cachedPlan ?: return
        val data = computeInsights(
            sessions = sessions,
            sections = sections,
            doneLeaves = storedSyllabus.doneLeaves,
            excludedSectionKeys = storedSyllabus.excludedSectionKeys,
            plan = plan,
            period = _state.value.period,
        )
        _state.update {
            it.copy(
                loading = false,
                hasPlan = true,
                data = data,
                targetSections = sections.mapIndexed { index, section ->
                    TargetSectionUi(
                        key = "section_$index",
                        name = section.name,
                        excluded = "section_$index" in storedSyllabus.excludedSectionKeys,
                    )
                },
                errorMessage = null,
            )
        }
    }

    class Factory(
        private val planStore: PlanStore,
        private val syllabusRepository: SyllabusRepository,
        private val syllabusStore: SyllabusStore,
        private val studySessionStore: StudySessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProgressViewModel(planStore, syllabusRepository, syllabusStore, studySessionStore) as T
    }
}
