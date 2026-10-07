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
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.PlanningOrder
import com.exam.assistant.domain.PyqPriorityTable
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.TopicProgress
import com.exam.assistant.domain.syllabusMetrics
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.totalMinutes
import com.exam.assistant.domain.cleanChapterTitle
import com.exam.assistant.domain.withChapterTitles
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
    private val topicProgressRepository: TopicProgressRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OrganiseUiState())
    val state: StateFlow<OrganiseUiState> = _state.asStateFlow()

    private var pack: ExamPack? = null
    private var basePack: ExamPack? = null
    private var chapterTitles: Map<String, String> = emptyMap()
    private var originalExcluded = emptySet<String>()
    private var draftExcluded = emptySet<String>()
    private var originalCustom = emptyList<CustomSyllabusChapter>()
    private var draftCustom = emptyList<CustomSyllabusChapter>()
    private var originalOrder = PlanningOrder.DEFAULT
    private var draftOrder = PlanningOrder.DEFAULT
    private var originalRows = emptyList<OrganiseChapterRow>()
    private var progress = emptyList<TopicProgress>()
    private var originalRemainingMinutes = 0
    private var priorityTable: PyqPriorityTable = PyqPriorityTable.EMPTY
    private var calibrationFactor = 1.0

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val attempt = attemptRepository.activeAttempt() ?: run {
            _state.value = OrganiseUiState(loading = false)
            return@launch
        }
        basePack = examPackRepository.examPackFor(attempt.examId)
        chapterTitles = examPackRepository.chapterTitles(attempt.id)
        pack = basePack?.withChapterTitles(chapterTitles)
        originalCustom = examPackRepository.customChapters(attempt.examId)
        draftCustom = originalCustom
        originalExcluded = targetSyllabusRepository.overridesFor(attempt.id)
            .filter { it.state == TargetNodeState.EXCLUDED }
            .mapTo(mutableSetOf()) { it.nodeId }
        draftExcluded = originalExcluded
        originalOrder = studyPreferenceRepository.forAttempt(attempt.id)?.planningOrder ?: PlanningOrder.DEFAULT
        draftOrder = originalOrder
        originalRows = rowsFor(pack!!)
        progress = topicProgressRepository.allOnce(attempt.id)
        priorityTable = examPackRepository.pyqPriorities()
        calibrationFactor = rollingPlanRepository.calibration().factor
        originalRemainingMinutes = remainingMinutesFor(pack!!, originalExcluded)
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

    /** Renames take effect at once, like on Syllabus: a name is not a plan change to preview. */
    fun renameChapter(chapterId: String, title: String?) = viewModelScope.launch {
        val attempt = attemptRepository.activeAttempt() ?: return@launch
        val original = basePack?.findNode(chapterId)?.title ?: return@launch
        examPackRepository.renameChapter(attempt.id, chapterId, cleanChapterTitle(title, original))
        chapterTitles = examPackRepository.chapterTitles(attempt.id)
        pack = basePack?.withChapterTitles(chapterTitles)
        originalRows = originalRows.map { row -> row.copy(title = chapterTitles[row.id] ?: row.originalTitle) }
        refreshPreview()
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
            originalRemainingMinutes = remainingMinutesFor(pack ?: return@onSuccess, originalExcluded)
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
        basePack = examPackRepository.packWithCustom(_state.value.examId, draftCustom)
        pack = basePack?.withChapterTitles(chapterTitles)
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

    /** Uncovered work in the chapters that are in play, through the same metric every other screen uses. */
    private fun remainingMinutesFor(currentPack: ExamPack, excluded: Set<String>): Int =
        syllabusMetrics(currentPack, excluded.associateWith { TargetNodeState.EXCLUDED }, progress, calibrationFactor).remainingMinutes

    private fun rowsFor(currentPack: ExamPack): List<OrganiseChapterRow> {
        val customIds = draftCustom.mapTo(mutableSetOf()) { it.id }
        return currentPack.subjects.flatMap { subject ->
            subject.nodes.map { node ->
                OrganiseChapterRow(
                    id = node.id,
                    subjectId = subject.id,
                    subjectName = subject.name,
                    title = node.title,
                    originalTitle = if (node.id in chapterTitles) basePack?.findNode(node.id)?.title ?: node.title else node.title,
                    renamed = node.id in chapterTitles,
                    estimatedMinutes = node.totalMinutes(),
                    excluded = node.id in draftExcluded,
                    custom = node.id in customIds,
                    priority = priorityTable.forChapter(node.id),
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
            remainingMinutes = remainingMinutesFor(currentPack, draftExcluded),
            beforeRemainingMinutes = originalRemainingMinutes,
            changedChapterCount = changedChapters,
            planningOrder = draftOrder,
            orderChanged = draftOrder != originalOrder,
            dirty = draftExcluded != originalExcluded || draftCustom != originalCustom || draftOrder != originalOrder,
            pyqFirstYear = priorityTable.firstYear,
            pyqLastYear = priorityTable.lastYear,
        )
    }

    class Factory(
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val targetSyllabusRepository: TargetSyllabusRepository,
        private val planRepository: PlanRepository,
        private val studyPreferenceRepository: StudyPreferenceRepository,
        private val rollingPlanRepository: RollingPlanRepository,
        private val topicProgressRepository: TopicProgressRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OrganiseViewModel(
            examPackRepository,
            attemptRepository,
            targetSyllabusRepository,
            planRepository,
            studyPreferenceRepository,
            rollingPlanRepository,
            topicProgressRepository,
        ) as T
    }
}
