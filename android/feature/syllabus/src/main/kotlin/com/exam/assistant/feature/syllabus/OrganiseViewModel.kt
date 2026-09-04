package com.exam.assistant.feature.syllabus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.CustomSyllabusChapter
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.PlanningOrder
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.findNode
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
    private val planRepository: PlanRepository,
    private val studyPreferenceRepository: StudyPreferenceRepository,
    private val rollingPlanRepository: RollingPlanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OrganiseUiState())
    val state: StateFlow<OrganiseUiState> = _state.asStateFlow()

    private var pack: ExamPack? = null
    private var originalExcluded = emptySet<String>()
    private var draftExcluded = emptySet<String>()
    private var originalCustom = emptyList<CustomSyllabusChapter>()
    private var draftCustom = emptyList<CustomSyllabusChapter>()
    private var originalOrder = PlanningOrder.DEFAULT
    private var draftOrder = PlanningOrder.DEFAULT
    private var originalRows = emptyList<OrganiseChapterRow>()

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
            .mapTo(mutableSetOf()) { it.nodeId }
        draftExcluded = originalExcluded
        originalOrder = studyPreferenceRepository.forAttempt(attempt.id)?.planningOrder ?: PlanningOrder.DEFAULT
        draftOrder = originalOrder
        originalRows = rowsFor(pack!!)
        refreshPreview()
    }

    fun selectSubject(subjectId: String) = _state.update { it.copy(selectedSubjectId = subjectId) }

    fun setOrder(order: PlanningOrder) {
        draftOrder = order
        refreshPreviewAsync()
    }

    fun toggleIncluded(nodeId: String) {
        draftExcluded = if (nodeId in draftExcluded) draftExcluded - nodeId else draftExcluded + nodeId
        refreshPreviewAsync()
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
        refreshDraftPackAndPreview()
    }

    fun removeChapter(id: String) {
        draftCustom = draftCustom.filterNot { it.id == id }
        draftExcluded = draftExcluded - id
        refreshDraftPackAndPreview()
    }

    fun discard() {
        draftCustom = originalCustom
        draftExcluded = originalExcluded
        draftOrder = originalOrder
        refreshDraftPackAndPreview()
    }

    fun apply() = viewModelScope.launch {
        val attempt = attemptRepository.activeAttempt() ?: return@launch
        val previousPreferences = studyPreferenceRepository.forAttempt(attempt.id) ?: return@launch
        val today = LocalDate.now()
        val previousPlan = planRepository.planBetween(attempt.id, today, attempt.targetCompletionDate)
        _state.update { it.copy(saving = true, saveFailed = false) }

        runCatching {
            examPackRepository.saveCustomChapters(attempt.examId, draftCustom)
            targetSyllabusRepository.replaceStates(
                attempt.id,
                draftExcluded.associateWith { TargetNodeState.EXCLUDED },
                System.currentTimeMillis(),
            )
            studyPreferenceRepository.upsert(previousPreferences.copy(planningOrder = draftOrder))
            checkNotNull(rollingPlanRepository.replenish(fromDate = today))
        }.onSuccess {
            originalCustom = draftCustom
            originalExcluded = draftExcluded
            originalOrder = draftOrder
            originalRows = rowsFor(pack ?: return@onSuccess)
            _state.update { it.copy(saving = false, saved = true) }
        }.onFailure {
            runCatching { examPackRepository.saveCustomChapters(attempt.examId, originalCustom) }
            runCatching {
                targetSyllabusRepository.replaceStates(
                    attempt.id,
                    originalExcluded.associateWith { TargetNodeState.EXCLUDED },
                    System.currentTimeMillis(),
                )
            }
            runCatching { studyPreferenceRepository.upsert(previousPreferences) }
            runCatching { planRepository.replaceFutureAuto(attempt.id, today, previousPlan) }
            _state.update { it.copy(saving = false, saveFailed = true) }
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    private fun refreshDraftPackAndPreview() = viewModelScope.launch {
        pack = examPackRepository.packWithCustom(_state.value.examId, draftCustom)
        refreshPreview()
    }

    private fun refreshPreviewAsync() {
        viewModelScope.launch { refreshPreview() }
    }

    private suspend fun refreshPreview() {
        val attempt = attemptRepository.activeAttempt() ?: return
        val currentPack = pack ?: return
        val preview = rollingPlanRepository.preview(
            pack = currentPack,
            overridesByNodeId = draftExcluded.associateWith { TargetNodeState.EXCLUDED },
            planningOrder = draftOrder,
        )
        val today = LocalDate.now()
        val week = (0L..6L).map { offset ->
            val date = today.plusDays(offset)
            val blocks = preview?.blocks.orEmpty().filter { it.scheduledDate == date }
            OrganiseDaySummary(date, blocks.sumOf { it.plannedMinutes }, blocks.size)
        }
        val todayRows = preview?.blocks.orEmpty()
            .filter { it.scheduledDate == today }
            .sortedBy { it.startMinuteOfDay }
            .map { block ->
                OrganisePlanRow(
                    id = block.id,
                    title = block.nodeId?.let(currentPack::findNode)?.title ?: block.customTitle.orEmpty(),
                    startMinuteOfDay = block.startMinuteOfDay,
                    durationMinutes = block.plannedMinutes,
                    isRevision = block.activityType == StudyActivityType.REVISION,
                )
            }
        rebuild(attempt.examId, week, todayRows)
    }

    private fun rowsFor(currentPack: ExamPack): List<OrganiseChapterRow> {
        val customIds = draftCustom.mapTo(mutableSetOf()) { it.id }
        return currentPack.subjects.flatMap { subject ->
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
    }

    private fun rebuild(
        examId: String,
        week: List<OrganiseDaySummary>,
        todaySessions: List<OrganisePlanRow>,
    ) {
        val currentPack = pack ?: return
        val subjects = currentPack.subjects.map { it.id to it.name }.ifEmpty { listOf("custom" to "My syllabus") }
        val selected = _state.value.selectedSubjectId.takeIf { id -> subjects.any { it.first == id } }
            ?: subjects.first().first
        val rows = rowsFor(currentPack)
        val originalById = originalRows.associateBy { it.id }
        val changedChapters = (rows.map { it.id } + originalRows.map { it.id }).distinct().count { id ->
            val before = originalById[id]
            val after = rows.firstOrNull { it.id == id }
            before?.excluded != after?.excluded || before?.estimatedMinutes != after?.estimatedMinutes
        }
        _state.value = OrganiseUiState(
            loading = false,
            saving = _state.value.saving,
            saveFailed = _state.value.saveFailed,
            examId = examId,
            selectedSubjectId = selected,
            subjects = subjects,
            chapters = rows,
            week = week,
            todaySessions = todaySessions,
            beforeIncludedCount = originalRows.count { !it.excluded },
            beforeTotalMinutes = originalRows.filterNot { it.excluded }.sumOf { it.estimatedMinutes },
            changedChapterCount = changedChapters,
            planningOrder = draftOrder,
            orderChanged = draftOrder != originalOrder,
            dirty = draftExcluded != originalExcluded || draftCustom != originalCustom || draftOrder != originalOrder,
        )
    }

    class Factory(
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val targetSyllabusRepository: TargetSyllabusRepository,
        private val planRepository: PlanRepository,
        private val studyPreferenceRepository: StudyPreferenceRepository,
        private val rollingPlanRepository: RollingPlanRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OrganiseViewModel(
            examPackRepository,
            attemptRepository,
            targetSyllabusRepository,
            planRepository,
            studyPreferenceRepository,
            rollingPlanRepository,
        ) as T
    }
}
