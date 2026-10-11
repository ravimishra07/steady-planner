package com.exam.assistant.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.InsightPeriod
import com.exam.assistant.domain.InsightPlan
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudyPlanBlock
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.StudySessionRecord
import com.exam.assistant.domain.StudySessionStatus
import com.exam.assistant.domain.SyllabusNode
import com.exam.assistant.domain.SyllabusSection
import com.exam.assistant.domain.SyllabusTopicNode
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.computeInsights
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.findSubjectOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProgressViewModel(
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val planRepository: PlanRepository,
    private val studySessionRepository: StudySessionRepository,
    private val topicProgressRepository: TopicProgressRepository,
    private val targetSyllabusRepository: TargetSyllabusRepository,
    private val rollingPlanRepository: RollingPlanRepository,
    private val studyPreferenceRepository: StudyPreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    private var pack: ExamPack? = null
    private var attemptId: String? = null
    private var cachedPlan: InsightPlan? = null
    private var sessions = emptyList<StudySessionRecord>()
    private var covered = emptySet<String>()
    private var excluded = emptySet<String>()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        try {
            val attempt = attemptRepository.activeAttempt()
            if (attempt == null) {
                _state.update { it.copy(loading = false, hasPlan = false, data = null, loadFailed = false) }
                return@launch
            }
            attemptId = attempt.id
            val preferences = studyPreferenceRepository.forAttempt(attempt.id)
            pack = examPackRepository.examPackFor(attempt.examId)
            val start = studySessionRepository.earliestStudyDate(attempt.id)?.coerceAtMost(LocalDate.now().minusDays(83))
                ?: LocalDate.now().minusDays(83)
            val actual = studySessionRepository.between(attempt.id, start, LocalDate.now())
            val intent = planRepository.planBetween(attempt.id, start, LocalDate.now())
            sessions = historyProjection(pack!!, intent, actual)
            covered = topicProgressRepository.allOnce(attempt.id)
                .filter { it.status == TopicProgressStatus.COVERED }
                .map { it.nodeId }
                .toSet()
            excluded = targetSyllabusRepository.overridesFor(attempt.id)
                .filter { it.state == TargetNodeState.EXCLUDED }
                .map { it.nodeId }
                .toSet()
            cachedPlan = InsightPlan(
                daysUntilTarget = ChronoUnit.DAYS.between(LocalDate.now(), attempt.targetCompletionDate).toInt().coerceAtLeast(0),
                weekdayHours = (preferences?.weekdayTargetMinutes ?: 4 * 60) / 60f,
                weekendHours = (preferences?.weekendTargetMinutes ?: 7 * 60) / 60f,
                startedOn = Instant.ofEpochMilli(attempt.createdAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate(),
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
        val shouldExclude = subject.nodes.any { it.id !in excluded }
        val now = System.currentTimeMillis()
        subject.nodes.forEach { node ->
            if (shouldExclude) targetSyllabusRepository.setState(id, node.id, TargetNodeState.EXCLUDED, now)
            else targetSyllabusRepository.clearOverride(id, node.id)
        }
        excluded = if (shouldExclude) excluded + subject.nodes.map { it.id } else excluded - subject.nodes.map { it.id }.toSet()
        rollingPlanRepository.replenish()
        rebuild()
    }

    private fun rebuild() {
        val plan = cachedPlan ?: return
        val currentPack = pack ?: return
        val sections = currentPack.subjects.map { subject ->
            SyllabusSection(
                name = subject.name,
                questions = subject.questions ?: 0,
                topics = subject.nodes.map(SyllabusNode::toLegacyNode),
            )
        }
        val excludedSections = currentPack.subjects.mapIndexedNotNull { index, subject ->
            "section_$index".takeIf { subject.nodes.isNotEmpty() && subject.nodes.all { it.id in excluded } }
        }.toSet()
        val data = computeInsights(
            sessions = sessions,
            sections = sections,
            doneLeaves = covered,
            excludedSectionKeys = excludedSections,
            plan = plan,
            period = _state.value.period,
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
                        excluded = subject.nodes.isNotEmpty() && subject.nodes.all { it.id in excluded },
                    )
                },
                loadFailed = false,
            )
        }
    }

    private fun historyProjection(
        pack: ExamPack,
        plan: List<StudyPlanBlock>,
        actual: List<StudySession>,
    ): List<StudySessionRecord> {
        val actualByPlan = actual.filter { it.planBlockId != null }.associateBy { it.planBlockId }
        val planned = plan.filter { it.status != PlanBlockStatus.RESCHEDULED && it.status != PlanBlockStatus.SKIPPED }.map { block ->
            val session = actualByPlan[block.id]
            block.toRecord(pack, session)
        }
        val unplanned = actual.filter { it.planBlockId == null }.map { it.toRecord(pack) }
        return planned + unplanned
    }

    private fun StudyPlanBlock.toRecord(pack: ExamPack, actual: StudySession?): StudySessionRecord {
        val node = nodeId?.let(pack::findNode)
        val subject = nodeId?.let(pack::findSubjectOf)
        return StudySessionRecord(
            id = id,
            date = scheduledDate,
            startMinuteOfDay = startMinuteOfDay,
            durationMinutes = plannedMinutes,
            nodeKey = nodeId.orEmpty(),
            title = node?.title ?: customTitle.orEmpty(),
            sectionName = subject?.name.orEmpty(),
            subjectId = subjectId.orEmpty(),
            isRevision = activityType == StudyActivityType.REVISION,
            completed = actual?.status == StudySessionStatus.COMPLETED || status == PlanBlockStatus.COMPLETED,
            planned = true,
            focusedMinutes = actual?.focusedSeconds?.div(60)?.coerceAtLeast(1),
        )
    }

    private fun StudySession.toRecord(pack: ExamPack): StudySessionRecord {
        val node = nodeId?.let(pack::findNode)
        val subject = nodeId?.let(pack::findSubjectOf)
        val localTime = Instant.ofEpochMilli(startedAtEpochMs).atZone(ZoneId.of(timeZoneId)).toLocalTime()
        return StudySessionRecord(
            id = id,
            date = studyDate,
            startMinuteOfDay = localTime.hour * 60 + localTime.minute,
            durationMinutes = (focusedSeconds / 60).coerceAtLeast(1),
            nodeKey = nodeId.orEmpty(),
            title = node?.title ?: customTitle.orEmpty(),
            sectionName = subject?.name.orEmpty(),
            subjectId = subjectId.orEmpty(),
            isRevision = activityType == StudyActivityType.REVISION,
            completed = status == StudySessionStatus.COMPLETED,
            planned = false,
            focusedMinutes = (focusedSeconds / 60).coerceAtLeast(1),
        )
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProgressViewModel(
            examPackRepository, attemptRepository, planRepository, studySessionRepository,
            topicProgressRepository, targetSyllabusRepository,
            rollingPlanRepository, studyPreferenceRepository,
        ) as T
    }
}

private fun SyllabusNode.toLegacyNode(): SyllabusTopicNode = SyllabusTopicNode(
    name = title,
    hours = estimatedMinutes?.div(60.0),
    children = children.map(SyllabusNode::toLegacyNode),
    id = id,
)
