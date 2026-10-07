package com.exam.assistant.feature.syllabus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.StageLabelStore
import com.exam.assistant.core.data.repo.ChapterStageRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.domain.ChapterPriority
import com.exam.assistant.domain.ChapterStage
import com.exam.assistant.domain.ChapterStageMark
import com.exam.assistant.domain.RevisionState
import com.exam.assistant.domain.StageLabels
import com.exam.assistant.domain.chapterRevisionCount
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.PyqPriorityTable
import com.exam.assistant.domain.SyllabusNode
import com.exam.assistant.domain.SyllabusMetrics
import com.exam.assistant.domain.TopicProgress
import com.exam.assistant.domain.coverage
import com.exam.assistant.domain.syllabusMetrics
import com.exam.assistant.domain.targetStates
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.leafIds
import com.exam.assistant.domain.totalMinutes
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.isNodeEffectivelyExcluded
import com.exam.assistant.domain.cleanChapterTitle
import com.exam.assistant.domain.withChapterTitles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Coverage reads from [TopicProgressRepository] (the one source of truth for
 * "what have I covered", spec §20/§38) instead of the legacy `doneLeaves`
 * set. Open/expanded rows are UI-only state (spec §10's
 * `SyllabusViewPreferences`) kept in memory here — not persisted, since
 * expand/collapse was never durable product behavior worth a store.
 */
class SyllabusViewModel(
    private val examPackRepository: ExamPackRepository,
    private val topicProgressRepository: TopicProgressRepository,
    private val attemptRepository: AttemptRepository,
    private val targetSyllabusRepository: TargetSyllabusRepository,
    private val rollingPlanRepository: RollingPlanRepository,
    private val chapterStageRepository: ChapterStageRepository,
    private val stageLabelStore: StageLabelStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SyllabusUiState())
    val state: StateFlow<SyllabusUiState> = _state.asStateFlow()

    private var pack: ExamPack? = null
    /** The pack with bundled titles, for "Reset to original name". */
    private var basePack: ExamPack? = null
    private var chapterTitles: Map<String, String> = emptyMap()
    private var attemptId: String? = null
    private var progress: List<TopicProgress> = emptyList()
    private var metrics: SyllabusMetrics? = null
    private val coveredNodeIds: Set<String> get() = metrics?.coveredIds.orEmpty()
    private var openNodeIds: Set<String> = emptySet()
    private var targetOverrides: Map<String, TargetNodeState> = emptyMap()
    private var classGroupLeafIds: Map<String, List<String>> = emptyMap()
    private var defaultExpansionSeeded = false
    private var priorityTable: PyqPriorityTable = PyqPriorityTable.EMPTY
    private var priorityLoaded = false
    private var stageMarks: Map<String, Map<ChapterStage, ChapterStageMark>> = emptyMap()
    private var revisionStates: Map<String, RevisionState> = emptyMap()
    private var stageLabels = StageLabels()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val attempt = attemptRepository.activeAttempt()
            attemptId = attempt?.id
            basePack = attempt?.let { examPackRepository.examPackFor(it.examId) }
            chapterTitles = attempt?.let { examPackRepository.chapterTitles(it.id) }.orEmpty()
            pack = basePack?.withChapterTitles(chapterTitles)
            targetOverrides = attempt?.let { active ->
                targetStates(targetSyllabusRepository.overridesFor(active.id))
            }.orEmpty()
            reloadProgress()
            if (!priorityLoaded) {
                // Off the first-paint path: the tree shows first, the yield labels fill in after.
                priorityTable = examPackRepository.pyqPriorities()
                priorityLoaded = true
                rebuild()
            }
        }
    }

    private suspend fun reloadProgress() {
        val id = attemptId ?: run { rebuild(); return }
        progress = topicProgressRepository.allOnce(id)
        stageMarks = chapterStageRepository.marksFor(id)
            .groupBy { it.chapterId }
            .mapValues { (_, marks) -> marks.associateBy { it.stage } }
        revisionStates = chapterStageRepository.revisionStatesFor(id)
        stageLabels = stageLabelStore.load()
        rebuild()
    }

    fun toggleExpand(key: String) {
        openNodeIds = if (key in openNodeIds) openNodeIds - key else openNodeIds + key
        rebuild()
    }

    fun toggleTick(key: String) {
        val examPack = pack ?: run {
            _state.value = SyllabusUiState(loading = false, isEmpty = true)
            return
        }
        if (examPack.subjects.isEmpty()) {
            _state.value = SyllabusUiState(loading = false, isEmpty = true)
            return
        }
        val id = attemptId ?: return
        val leaves = classGroupLeafIds[key]
            ?: examPack.findNode(key)?.leafIds()
            ?: return
        val allDone = leaves.isNotEmpty() && leaves.all { it in coveredNodeIds }
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            topicProgressRepository.setCovered(id, leaves, covered = !allDone, nowMs = nowMs)
            rollingPlanRepository.replenish(nowMs = nowMs)
            reloadProgress()
        }
    }

    /**
     * One tap on a chapter's stage chip. Learned is the chapter's covered tick, so it takes the
     * same path as the status dot; Revised adds one; every other stage toggles.
     */
    fun tapStage(chapterId: String, stage: ChapterStage) {
        if (stage == ChapterStage.LEARNED) {
            toggleTick(chapterId)
            return
        }
        val id = attemptId ?: return
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            if (stage == ChapterStage.REVISED) {
                chapterStageRepository.adjustCount(id, chapterId, stage, delta = 1, nowMs = nowMs)
            } else {
                chapterStageRepository.toggle(id, chapterId, stage, nowMs)
            }
            reloadProgress()
        }
    }

    /** Long-press on Revised takes back one hand-logged revision. Focus revisions are not touched. */
    fun undoRevision(chapterId: String) {
        val id = attemptId ?: return
        viewModelScope.launch {
            chapterStageRepository.adjustCount(id, chapterId, ChapterStage.REVISED, delta = -1, nowMs = System.currentTimeMillis())
            reloadProgress()
        }
    }

    /** Renames a chapter everywhere (§5.2). Null, blank or the original title resets it. */
    fun renameChapter(chapterId: String, title: String?) {
        val id = attemptId ?: return
        val original = basePack?.findNode(chapterId)?.title ?: return
        viewModelScope.launch {
            examPackRepository.renameChapter(id, chapterId, cleanChapterTitle(title, original))
            chapterTitles = examPackRepository.chapterTitles(id)
            pack = basePack?.withChapterTitles(chapterTitles)
            rebuild()
        }
    }

    /** Park or unpark a chapter: the same exclusion Organise writes, then the plan refills. */
    fun toggleParked(chapterId: String) {
        val id = attemptId ?: return
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            if (targetOverrides[chapterId] == TargetNodeState.EXCLUDED) {
                targetSyllabusRepository.clearOverride(id, chapterId)
            } else {
                targetSyllabusRepository.setState(id, chapterId, TargetNodeState.EXCLUDED, nowMs)
            }
            targetOverrides = targetStates(targetSyllabusRepository.overridesFor(id))
            rollingPlanRepository.replenish(nowMs = nowMs)
            rebuild()
        }
    }

    fun saveStageLabels(labels: StageLabels) {
        viewModelScope.launch {
            stageLabelStore.save(labels)
            stageLabels = stageLabelStore.load()
            rebuild()
        }
    }

    private fun stageChipsFor(node: SyllabusNode, learned: Boolean): List<StageChipUi> {
        val marks = stageMarks[node.id].orEmpty()
        return stageLabels.visibleStages().map { stage ->
            val count = when (stage) {
                ChapterStage.LEARNED -> if (learned) 1 else 0
                ChapterStage.REVISED -> chapterRevisionCount(node, revisionStates, marks[stage]?.count ?: 0)
                else -> if (stage in marks) 1 else 0
            }
            StageChipUi(stage = stage, customLabel = stageLabels.labelFor(stage), count = count)
        }
    }

    private fun rebuild() {
        val examPack = pack ?: return
        val canonical = syllabusMetrics(examPack, targetOverrides, progress)
        metrics = canonical
        if (!defaultExpansionSeeded) {
            examPack.subjects.firstOrNull()?.let { subject ->
                openNodeIds = openNodeIds + subjectCardKey(subject.id)
                subject.nodes.firstNotNullOfOrNull(::classNumberFor)?.let { classNumber ->
                    openNodeIds = openNodeIds + classGroupKey(subject.id, classNumber)
                    subject.nodes.firstOrNull { node ->
                        classNumberFor(node) == classNumber && node.children.isNotEmpty()
                    }?.let { firstChapter ->
                        openNodeIds = openNodeIds + firstChapter.id
                    }
                }
            }
            defaultExpansionSeeded = true
        }
        val rebuiltClassGroups = mutableMapOf<String, List<String>>()
        val subjects = examPack.subjects.map { subject ->
            val cardKey = subjectCardKey(subject.id)
            val classGroups = subject.nodes
                .mapNotNull { node -> classNumberFor(node)?.let { it to node } }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .toSortedMap()
            val ungroupedNodes = subject.nodes.filter { classNumberFor(it) == null }
            val rows = buildClassRows(
                subjectId = subject.id,
                subjectName = subject.name,
                classGroups = classGroups,
                classGroupLeaves = rebuiltClassGroups,
            ) + ungroupedNodes.flatMapIndexed { index, node ->
                buildRows(
                    node = node,
                    ancestorContinues = emptyList(),
                    isLast = index == ungroupedNodes.lastIndex,
                    subjectId = subject.id,
                    sectionName = subject.name,
                )
            }

            val allLeaves = subject.leafIds()
            val includedLeaves = canonical.targetLeafIds.intersect(allLeaves.toSet())
            val nextLeafId = includedLeaves.firstOrNull { it !in coveredNodeIds }
            val nextLeaf = nextLeafId?.let(examPack::findNode)
            val percent = canonical.bySubject[subject.id]?.percent ?: 0
            val excludedCount = subject.nodes.count { node ->
                isNodeEffectivelyExcluded(node.id, emptyList(), targetOverrides)
            }

            SyllabusSubjectCard(
                key = cardKey,
                name = subject.name,
                subjectId = subject.id,
                percent = percent,
                expanded = cardKey in openNodeIds,
                rows = rows,
                firstTopicKey = nextLeafId,
                firstTopicTitle = nextLeaf?.title.orEmpty(),
                doneChapterCount = subject.nodes.count { node -> node.leafIds().all { it in coveredNodeIds } },
                totalChapterCount = subject.nodes.size,
                totalEstimatedMinutes = subject.nodes.sumOf { it.totalMinutes() },
                questions = subject.questions ?: 0,
                chapterStats = subject.nodes.map { node ->
                    SyllabusChapterStat(
                        classNumber = classNumberFor(node),
                        complete = node.leafIds().all { it in coveredNodeIds },
                    )
                },
                excludedCount = excludedCount,
            )
        }
        classGroupLeafIds = rebuiltClassGroups

        _state.update {
            SyllabusUiState(
                loading = false,
                subjects = subjects,
                allCount = subjects.size,
                dueCount = subjects.count { it.percent < 100 },
                completedPercent = canonical.overall.percent,
                pyqFirstYear = priorityTable.firstYear,
                pyqLastYear = priorityTable.lastYear,
                stageLabels = stageLabels,
            )
        }
    }

    private fun buildClassRows(
        subjectId: String,
        subjectName: String,
        classGroups: Map<Int, List<SyllabusNode>>,
        classGroupLeaves: MutableMap<String, List<String>>,
    ): List<SyllabusTreeRow> = classGroups.entries.flatMapIndexed { groupIndex, (classNumber, chapters) ->
        val key = classGroupKey(subjectId, classNumber)
        val leaves = chapters.flatMap(SyllabusNode::leafIds)
        classGroupLeaves[key] = leaves
        val doneCount = leaves.count { it in coveredNodeIds }
        val tickState = when {
            leaves.isEmpty() || doneCount == 0 -> com.exam.assistant.domain.SyllabusTickState.NONE
            doneCount == leaves.size -> com.exam.assistant.domain.SyllabusTickState.ALL
            else -> com.exam.assistant.domain.SyllabusTickState.PARTIAL
        }
        val header = SyllabusTreeRow(
            key = key,
            name = "",
            estimatedMinutes = chapters.sumOf(SyllabusNode::totalMinutes),
            depth = 0,
            hasChildren = chapters.isNotEmpty(),
            expanded = key in openNodeIds,
            tickState = tickState,
            percent = coverage(leaves.toSet(), coveredNodeIds).percent,
            doneLeafCount = doneCount,
            totalLeafCount = leaves.size,
            ancestorContinues = emptyList(),
            isLastChild = groupIndex == classGroups.size - 1,
            subjectId = subjectId,
            sectionName = subjectName,
            topicPath = subjectName,
            classNumber = classNumber,
            isClassGroup = true,
        )
        if (key !in openNodeIds) {
            listOf(header)
        } else {
            listOf(header) + chapters.flatMapIndexed { chapterIndex, chapter ->
                buildRows(
                    node = chapter,
                    ancestorContinues = listOf(false),
                    isLast = chapterIndex == chapters.lastIndex,
                    subjectId = subjectId,
                    sectionName = subjectName,
                )
            }
        }
    }

    private fun subjectCardKey(subjectId: String): String = "subject_$subjectId"

    private fun classGroupKey(subjectId: String, classNumber: Int): String =
        "class_${subjectId}_$classNumber"

    private fun classNumberFor(node: SyllabusNode): Int? =
        node.id.split('.').getOrNull(2)?.toIntOrNull()

    private fun buildRows(
        node: SyllabusNode,
        ancestorContinues: List<Boolean>,
        isLast: Boolean,
        subjectId: String,
        sectionName: String,
        ancestorIds: List<String> = emptyList(),
    ): List<SyllabusTreeRow> {
        val leaves = node.leafIds()
        val doneCount = leaves.count { it in coveredNodeIds }
        val percent = coverage(leaves.toSet(), coveredNodeIds).percent
        val tickState = when {
            leaves.isEmpty() -> com.exam.assistant.domain.SyllabusTickState.NONE
            doneCount == 0 -> com.exam.assistant.domain.SyllabusTickState.NONE
            doneCount == leaves.size -> com.exam.assistant.domain.SyllabusTickState.ALL
            else -> com.exam.assistant.domain.SyllabusTickState.PARTIAL
        }
        val row = SyllabusTreeRow(
            key = node.id,
            name = node.title,
            estimatedMinutes = node.totalMinutes(),
            depth = ancestorContinues.size,
            hasChildren = node.children.isNotEmpty(),
            expanded = node.id in openNodeIds,
            tickState = tickState,
            percent = percent,
            doneLeafCount = doneCount,
            totalLeafCount = leaves.size,
            ancestorContinues = ancestorContinues,
            isLastChild = isLast,
            subjectId = subjectId,
            sectionName = sectionName,
            topicPath = sectionName,
            classNumber = node.id.split('.').getOrNull(2)?.toIntOrNull(),
            excluded = isNodeEffectivelyExcluded(node.id, ancestorIds, targetOverrides),
            priority = if (ancestorIds.isEmpty() && priorityLoaded) priorityTable.forChapter(node.id) else null,
            isChapter = ancestorIds.isEmpty(),
            renamed = ancestorIds.isEmpty() && node.id in chapterTitles,
            originalName = if (ancestorIds.isEmpty() && node.id in chapterTitles) {
                basePack?.findNode(node.id)?.title.orEmpty()
            } else {
                node.title
            },
            stageChips = if (ancestorIds.isEmpty()) {
                stageChipsFor(node, learned = tickState == com.exam.assistant.domain.SyllabusTickState.ALL)
            } else {
                emptyList()
            },
        )
        if (node.children.isEmpty() || node.id !in openNodeIds) {
            return listOf(row)
        }
        val childAncestors = ancestorContinues + !isLast
        val childRows = node.children.flatMapIndexed { index, child ->
            buildRows(
                node = child,
                ancestorContinues = childAncestors,
                isLast = index == node.children.lastIndex,
                subjectId = subjectId,
                sectionName = sectionName,
                ancestorIds = ancestorIds + node.id,
            )
        }
        return listOf(row) + childRows
    }

    class Factory(
        private val examPackRepository: ExamPackRepository,
        private val topicProgressRepository: TopicProgressRepository,
        private val attemptRepository: AttemptRepository,
        private val targetSyllabusRepository: TargetSyllabusRepository,
        private val rollingPlanRepository: RollingPlanRepository,
        private val chapterStageRepository: ChapterStageRepository,
        private val stageLabelStore: StageLabelStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SyllabusViewModel(
                examPackRepository,
                topicProgressRepository,
                attemptRepository,
                targetSyllabusRepository,
                rollingPlanRepository,
                chapterStageRepository,
                stageLabelStore,
            ) as T
    }
}
