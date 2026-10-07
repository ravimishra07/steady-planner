package com.exam.assistant.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.AvailabilityRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RevisionRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.Calibration
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.PlanFit
import com.exam.assistant.domain.InsightPeriod
import com.exam.assistant.domain.InsightPlan
import com.exam.assistant.domain.RevisionState
import com.exam.assistant.domain.StudyPlanBlock
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.TopicProgress
import com.exam.assistant.domain.computeInsights
import com.exam.assistant.domain.currentMinuteOfDay
import com.exam.assistant.domain.syllabusMetrics
import com.exam.assistant.domain.targetStates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Insights holds no formulas: it loads raw state and hands it to
 * [computeInsights], which is built from the canonical functions in Metrics.kt.
 */
class ProgressViewModel(
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val planRepository: PlanRepository,
    private val studySessionRepository: StudySessionRepository,
    private val topicProgressRepository: TopicProgressRepository,
    private val targetSyllabusRepository: TargetSyllabusRepository,
    private val rollingPlanRepository: RollingPlanRepository,
    private val studyPreferenceRepository: StudyPreferenceRepository,
    private val revisionRepository: RevisionRepository,
    private val availabilityRepository: AvailabilityRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    private var pack: ExamPack? = null
    private var attemptId: String? = null
    private var cachedPlan: InsightPlan? = null
    private var sessions = emptyList<StudySession>()
    private var planBlocks = emptyList<StudyPlanBlock>()
    private var revisionStates = emptyList<RevisionState>()
    private var progress = emptyList<TopicProgress>()
    private var targetOverrides = emptyMap<String, TargetNodeState>()
    private var calibration = Calibration.NONE
    private var fit: PlanFit? = null

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        try {
            val attempt = attemptRepository.activeAttempt()
            if (attempt == null) {
                _state.update { it.copy(loading = false, hasPlan = false, data = null, loadFailed = false) }
                return@launch
            }
            attemptId = attempt.id
            val today = LocalDate.now()
            val preferences = studyPreferenceRepository.forAttempt(attempt.id)
            pack = examPackRepository.examPackFor(attempt.examId)
            val start = (studySessionRepository.earliestStudyDate(attempt.id) ?: today).coerceAtMost(today.minusDays(83))
            sessions = studySessionRepository.between(attempt.id, start, today)
            planBlocks = planRepository.planBetween(attempt.id, start, today)
            revisionStates = revisionRepository.allFor(attempt.id)
            progress = topicProgressRepository.allOnce(attempt.id)
            targetOverrides = targetStates(targetSyllabusRepository.overridesFor(attempt.id))
            calibration = rollingPlanRepository.calibration()
            fit = rollingPlanRepository.planFit(withOptions = false)
            cachedPlan = InsightPlan(
                startedOn = Instant.ofEpochMilli(attempt.createdAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate(),
                targetDate = attempt.targetCompletionDate,
                weeklyAvailability = availabilityRepository.weeklyFor(attempt.id),
                availabilityOverrides = availabilityRepository.overridesFor(attempt.id),
                weekdayTargetMinutes = preferences?.weekdayTargetMinutes ?: (4 * 60),
                weekendTargetMinutes = preferences?.weekendTargetMinutes ?: (7 * 60),
            )
            rebuild()
        } catch (_: Throwable) {
            _state.update { it.copy(loading = false, loadFailed = true) }
        }
    }

    fun selectPeriod(period: InsightPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        viewModelScope.launch { rebuild() }
    }

    fun openTargetManager() = _state.update { it.copy(showTargetManager = true) }
    fun closeTargetManager() = _state.update { it.copy(showTargetManager = false) }

    fun toggleSectionTarget(key: String) = viewModelScope.launch {
        val id = attemptId ?: return@launch
        val subject = pack?.subjects?.firstOrNull { it.id == key } ?: return@launch
        val shouldExclude = subject.nodes.any { targetOverrides[it.id] != TargetNodeState.EXCLUDED }
        val now = System.currentTimeMillis()
        subject.nodes.forEach { node ->
            if (shouldExclude) targetSyllabusRepository.setState(id, node.id, TargetNodeState.EXCLUDED, now)
            else targetSyllabusRepository.clearOverride(id, node.id)
        }
        targetOverrides = if (shouldExclude) {
            targetOverrides + subject.nodes.associate { it.id to TargetNodeState.EXCLUDED }
        } else {
            targetOverrides - subject.nodes.map { it.id }.toSet()
        }
        rollingPlanRepository.replenish()
        rebuild()
    }

    private fun rebuild() {
        val plan = cachedPlan ?: return
        val currentPack = pack ?: return
        val data = computeInsights(
            pack = currentPack,
            syllabus = syllabusMetrics(currentPack, targetOverrides, progress, calibration.factor),
            sessions = sessions,
            planBlocks = planBlocks,
            revisionStates = revisionStates,
            plan = plan,
            period = _state.value.period,
            today = LocalDate.now(),
            nowMinuteOfDay = currentMinuteOfDay(),
            calibration = calibration,
            fit = fit,
        )
        _state.update {
            it.copy(
                loading = false,
                hasPlan = true,
                data = data,
                targetSections = currentPack.subjects.map { subject ->
                    TargetSectionUi(
                        key = subject.id,
                        name = subject.name,
                        excluded = subject.nodes.isNotEmpty() && subject.nodes.all { node ->
                            targetOverrides[node.id] == TargetNodeState.EXCLUDED
                        },
                    )
                },
                loadFailed = false,
            )
        }
    }

    class Factory(
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val planRepository: PlanRepository,
        private val studySessionRepository: StudySessionRepository,
        private val topicProgressRepository: TopicProgressRepository,
        private val targetSyllabusRepository: TargetSyllabusRepository,
        private val rollingPlanRepository: RollingPlanRepository,
        private val studyPreferenceRepository: StudyPreferenceRepository,
        private val revisionRepository: RevisionRepository,
        private val availabilityRepository: AvailabilityRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProgressViewModel(
            examPackRepository, attemptRepository, planRepository, studySessionRepository,
            topicProgressRepository, targetSyllabusRepository,
            rollingPlanRepository, studyPreferenceRepository,
            revisionRepository, availabilityRepository,
        ) as T
    }
}
