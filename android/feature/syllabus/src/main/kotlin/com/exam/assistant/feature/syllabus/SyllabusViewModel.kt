package com.exam.assistant.feature.syllabus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.SyllabusNode
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.leafIds
import com.exam.assistant.domain.totalMinutes
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.isNodeEffectivelyExcluded
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
) : ViewModel() {

    private val _state = MutableStateFlow(SyllabusUiState())
    val state: StateFlow<SyllabusUiState> = _state.asStateFlow()

    private var pack: ExamPack? = null
    private var attemptId: String? = null
    private var coveredNodeIds: Set<String> = emptySet()
    private var openNodeIds: Set<String> = emptySet()
    private var targetOverrides: Map<String, TargetNodeState> = emptyMap()
    private var classGroupLeafIds: Map<String, List<String>> = emptyMap()
    private var defaultExpansionSeeded = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val attempt = attemptRepository.activeAttempt()
            attemptId = attempt?.id
            pack = attempt?.let { examPackRepository.examPackFor(it.examId) }
            targetOverrides = attempt?.let { active ->
                targetSyllabusRepository.overridesFor(active.id).associate { it.nodeId to it.state }
            }.orEmpty()
            reloadProgress()
        }
    }

    private suspend fun reloadProgress() {
        val id = attemptId ?: run { rebuild(); return }
        coveredNodeIds = topicProgressRepository.allOnce(id)
            .filter { it.status == TopicProgressStatus.COVERED }
            .map { it.nodeId }
            .toSet()
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
            leaves.forEach { leafId ->
                val current = topicProgressRepository.byNode(id, leafId)
                val isCovered = current?.status == TopicProgressStatus.COVERED
                if (allDone == isCovered) {
                    // Only flip leaves that don't already match the target state.
                    topicProgressRepository.toggle(id, leafId, nowMs)
                }
            }
            reloadProgress()
        }
    }

    private fun rebuild() {
        val examPack = pack ?: return
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
            val doneCount = allLeaves.count { it in coveredNodeIds }
            val percent = if (allLeaves.isNotEmpty()) doneCount * 100 / allLeaves.size else 0
            val doneMinutes = subject.nodes.sumOf { doneMinutesFor(it) }
            val excludedCount = subject.nodes.count { node ->
                isNodeEffectivelyExcluded(node.id, emptyList(), targetOverrides)
            }

            SyllabusSubjectCard(
                key = cardKey,
                name = subject.name,
                subjectId = subject.id,
                percent = percent,
                timeSpentMinutes = doneMinutes,
                expanded = cardKey in openNodeIds,
                rows = rows,
                firstTopicKey = subject.nodes.firstOrNull()?.id,
                firstTopicTitle = subject.nodes.firstOrNull()?.title.orEmpty(),
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

        val allLeavesGlobal = examPack.leafIds()
        val doneGlobal = allLeavesGlobal.count { it in coveredNodeIds }
        val percentGlobal = if (allLeavesGlobal.isNotEmpty()) doneGlobal * 100 / allLeavesGlobal.size else 0
        val totalDoneMinutesGlobal = examPack.subjects.sumOf { subject -> subject.nodes.sumOf { doneMinutesFor(it) } }

        _state.update {
            SyllabusUiState(
                loading = false,
                subjects = subjects,
                allCount = subjects.size,
                dueCount = subjects.count { it.percent < 100 },
                completedPercent = percentGlobal,
                timeSpentMinutes = totalDoneMinutesGlobal,
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
            percent = if (leaves.isEmpty()) 0 else doneCount * 100 / leaves.size,
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

    /** Minutes credited proportionally to how much of this node's leaf set is covered. */
    private fun doneMinutesFor(node: SyllabusNode): Int {
        val leaves = node.leafIds()
        if (leaves.isEmpty()) return 0
        val done = leaves.count { it in coveredNodeIds }
        return (node.totalMinutes() * done) / leaves.size
    }

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
        val percent = if (leaves.isEmpty()) 0 else doneCount * 100 / leaves.size
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SyllabusViewModel(examPackRepository, topicProgressRepository, attemptRepository, targetSyllabusRepository) as T
    }
}
