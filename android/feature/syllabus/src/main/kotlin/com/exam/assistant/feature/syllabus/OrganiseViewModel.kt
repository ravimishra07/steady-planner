package com.exam.assistant.feature.syllabus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.CustomSyllabusChapter
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.AvailabilityRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.StudySessionRecord
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.effectiveTargetLeafIds
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.findSubjectOf
import com.exam.assistant.domain.generateInitialPlan
import com.exam.assistant.domain.leafIds
import com.exam.assistant.domain.totalMinutes
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrganiseViewModel(
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val targetSyllabusRepository: TargetSyllabusRepository,
    private val topicProgressRepository: TopicProgressRepository,
    private val availabilityRepository: AvailabilityRepository,
    private val planRepository: PlanRepository,
    private val planStore: PlanStore,
    private val studySessionStore: StudySessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OrganiseUiState())
    val state: StateFlow<OrganiseUiState> = _state.asStateFlow()

    private var pack: ExamPack? = null
    private var originalExcluded = emptySet<String>()
    private var draftExcluded = emptySet<String>()
    private var originalCustom = emptyList<CustomSyllabusChapter>()
    private var draftCustom = emptyList<CustomSyllabusChapter>()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val attempt = attemptRepository.activeAttempt() ?: run {
            _state.value = OrganiseUiState(loading = false)
            return@launch
        }
        pack = examPackRepository.examPackFor(attempt.examId)
        originalCustom = examPackRepository.customChapters(attempt.examId)
        draftCustom = originalCustom
        originalExcluded = targetSyllabusRepository.overridesFor(attempt.id)
            .filter { it.state == TargetNodeState.EXCLUDED }
            .map { it.nodeId }
            .toSet()
        draftExcluded = originalExcluded
        val planned = planRepository.planBetween(attempt.id, LocalDate.now(), LocalDate.now().plusDays(6))
        val todaySessions = studySessionStore.sessionsFor(LocalDate.now())
            .sortedBy { it.startMinuteOfDay }
            .map { session ->
                OrganisePlanRow(
                    id = session.id,
                    title = session.title,
                    startMinuteOfDay = session.startMinuteOfDay,
                    durationMinutes = session.durationMinutes,
                    isRevision = session.isRevision,
                )
            }
        rebuild(
            examId = attempt.examId,
            todaySessions = todaySessions,
            week = (0L..6L).map { offset ->
                val date = LocalDate.now().plusDays(offset)
                val blocks = planned.filter { it.scheduledDate == date }
                OrganiseDaySummary(date, blocks.sumOf { it.plannedMinutes }, blocks.size)
            },
        )
    }

    fun selectSubject(subjectId: String) = _state.update { it.copy(selectedSubjectId = subjectId) }

    fun toggleIncluded(nodeId: String) {
        draftExcluded = if (nodeId in draftExcluded) draftExcluded - nodeId else draftExcluded + nodeId
        rebuild()
    }

    fun addChapter(title: String, hours: Float, subjectName: String) {
        if (title.isBlank() || hours < 0.5f) return
        val selected = _state.value.selectedSubjectId.ifBlank { "custom" }
        draftCustom = draftCustom + CustomSyllabusChapter(
            id = "custom_${UUID.randomUUID()}",
            examId = _state.value.examId,
            subjectId = selected,
            subjectName = _state.value.subjects.firstOrNull { it.first == selected }?.second ?: subjectName,
            title = title.trim(),
            estimatedMinutes = (hours * 60).toInt(),
        )
        rebuildWithDraftPack()
    }

    fun removeChapter(id: String) {
        draftCustom = draftCustom.filterNot { it.id == id }
        draftExcluded = draftExcluded - id
        rebuildWithDraftPack()
    }

    fun discard() {
        draftCustom = originalCustom
        draftExcluded = originalExcluded
        rebuildWithDraftPack()
    }

    fun apply() = viewModelScope.launch {
        val attempt = attemptRepository.activeAttempt() ?: return@launch
        val savedPlan = planStore.load() ?: return@launch
        val today = LocalDate.now()
        val previousPlan = planRepository.planBetween(attempt.id, today, today.plusDays(6))
        val previousCompatibility = studySessionStore.loadAll().filter { it.date in today..today.plusDays(6) }
        _state.update { it.copy(saving = true, saveFailed = false) }
        var generatedWeek = emptyList<com.exam.assistant.domain.StudyPlanBlock>()
        runCatching {
            examPackRepository.saveCustomChapters(attempt.examId, draftCustom)
            val allKnownIds = originalExcluded + draftExcluded
            allKnownIds.forEach { nodeId -> targetSyllabusRepository.clearOverride(attempt.id, nodeId) }
            draftExcluded.forEach { nodeId ->
                targetSyllabusRepository.setState(attempt.id, nodeId, TargetNodeState.EXCLUDED, System.currentTimeMillis())
            }
            val updatedPack = examPackRepository.examPackFor(attempt.examId)
            val overrideMap = draftExcluded.associateWith { TargetNodeState.EXCLUDED }
            val includedLeaves = updatedPack.effectiveTargetLeafIds(overrideMap)
            val covered = topicProgressRepository.allOnce(attempt.id)
                .filter { it.status == TopicProgressStatus.COVERED }
                .map { it.nodeId }
                .toSet()
            val excludedLeaves = updatedPack.leafIds().toSet() - includedLeaves + covered
            val generated = generateInitialPlan(
                attemptId = attempt.id,
                pack = updatedPack,
                startDate = LocalDate.now(),
                days = 7,
                weekdayHours = savedPlan.weekdayHours,
                weekendHours = savedPlan.weekendHours,
                weeklyAvailability = availabilityRepository.weeklyFor(attempt.id),
                excludedLeafIds = excludedLeaves,
                nowMs = System.currentTimeMillis(),
            )
            generatedWeek = generated
            planRepository.replaceFutureAuto(attempt.id, LocalDate.now(), generated)
            studySessionStore.replaceFutureGeneratedPlan(
                attempt.id,
                LocalDate.now(),
                generated.mapNotNull { block ->
                    val nodeId = block.nodeId ?: return@mapNotNull null
                    val node = updatedPack.findNode(nodeId) ?: return@mapNotNull null
                    val subject = updatedPack.findSubjectOf(nodeId) ?: return@mapNotNull null
                    StudySessionRecord(
                        id = block.id,
                        date = block.scheduledDate,
                        startMinuteOfDay = block.startMinuteOfDay,
                        durationMinutes = block.plannedMinutes,
                        nodeKey = nodeId,
                        title = node.title,
                        sectionName = subject.name,
                        subjectId = subject.id,
                    )
                },
            )
            originalCustom = draftCustom
            originalExcluded = draftExcluded
            pack = updatedPack
        }.onSuccess {
            rebuild(week = (0L..6L).map { offset ->
                val date = today.plusDays(offset)
                val blocks = generatedWeek.filter { it.scheduledDate == date }
                OrganiseDaySummary(date, blocks.sumOf { it.plannedMinutes }, blocks.size)
            })
            _state.update { it.copy(saving = false, saved = true) }
        }.onFailure {
            runCatching { examPackRepository.saveCustomChapters(attempt.examId, originalCustom) }
            runCatching {
                (originalExcluded + draftExcluded).forEach { nodeId -> targetSyllabusRepository.clearOverride(attempt.id, nodeId) }
                originalExcluded.forEach { nodeId ->
                    targetSyllabusRepository.setState(attempt.id, nodeId, TargetNodeState.EXCLUDED, System.currentTimeMillis())
                }
            }
            runCatching { planRepository.replaceFutureAuto(attempt.id, today, previousPlan) }
            runCatching { studySessionStore.replaceFutureGeneratedPlan(attempt.id, today, previousCompatibility) }
            _state.update { it.copy(saving = false, saveFailed = true) }
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    private fun rebuildWithDraftPack() = viewModelScope.launch {
        pack = examPackRepository.packWithCustom(_state.value.examId, draftCustom)
        rebuild()
    }

    private fun rebuild(
        examId: String = _state.value.examId,
        week: List<OrganiseDaySummary> = _state.value.week,
        todaySessions: List<OrganisePlanRow> = _state.value.todaySessions,
    ) {
        val currentPack = pack ?: return
        val subjects = currentPack.subjects.map { it.id to it.name }.ifEmpty {
            listOf("custom" to "My syllabus")
        }
        val selected = _state.value.selectedSubjectId.takeIf { id -> subjects.any { it.first == id } } ?: subjects.first().first
        val customIds = draftCustom.map { it.id }.toSet()
        val rows = currentPack.subjects.flatMap { subject ->
            subject.nodes.map { node ->
                OrganiseChapterRow(
                    id = node.id,
                    subjectId = subject.id,
                    subjectName = subject.name,
                    title = node.title,
                    estimatedMinutes = node.totalMinutes(),
                    excluded = node.id in draftExcluded,
                    custom = node.id in customIds,
                )
            }
        }
        _state.value = OrganiseUiState(
            loading = false,
            examId = examId,
            selectedSubjectId = selected,
            subjects = subjects,
            chapters = rows,
            week = week,
            todaySessions = todaySessions,
            beforeIncludedCount = rows.count { it.id !in originalExcluded },
            dirty = draftExcluded != originalExcluded || draftCustom != originalCustom,
        )
    }

    class Factory(
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val targetSyllabusRepository: TargetSyllabusRepository,
        private val topicProgressRepository: TopicProgressRepository,
        private val availabilityRepository: AvailabilityRepository,
        private val planRepository: PlanRepository,
        private val planStore: PlanStore,
        private val studySessionStore: StudySessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OrganiseViewModel(
            examPackRepository, attemptRepository, targetSyllabusRepository, topicProgressRepository,
            availabilityRepository, planRepository, planStore, studySessionStore,
        ) as T
    }
}
