package com.exam.assistant.feature.syllabus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.SyllabusNode
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.leafIds
import com.exam.assistant.domain.totalMinutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

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
) : ViewModel() {

    private val _state = MutableStateFlow(SyllabusUiState())
    val state: StateFlow<SyllabusUiState> = _state.asStateFlow()

    private var pack: ExamPack? = null
    private var attemptId: String? = null
    private var coveredNodeIds: Set<String> = emptySet()
    private var openNodeIds: Set<String> = emptySet()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            pack = examPackRepository.examPack()
            attemptId = attemptRepository.activeAttempt()?.id
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
        val examPack = pack ?: return
        val id = attemptId ?: return
        val node = examPack.findNode(key) ?: return
        val leaves = node.leafIds()
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
        val subjects = examPack.subjects.map { subject ->
            val cardKey = "subject_${subject.id}"
            val rows = subject.nodes.flatMapIndexed { index, node ->
                buildRows(
                    node = node,
                    ancestorContinues = emptyList(),
                    isLast = index == subject.nodes.lastIndex,
                    subjectId = subject.id,
                    sectionName = subject.name,
                )
            }

            val allLeaves = subject.leafIds()
            val doneCount = allLeaves.count { it in coveredNodeIds }
            val percent = if (allLeaves.isNotEmpty()) doneCount * 100 / allLeaves.size else 0
            val doneMinutes = subject.nodes.sumOf { doneMinutesFor(it) }

            SyllabusSubjectCard(
                key = cardKey,
                name = subject.name,
                shortLabel = sectionTabLabel(subject.name),
                subjectId = subject.id,
                percent = percent,
                timeSpentLabel = formatHoursMinutes(doneMinutes),
                expanded = cardKey in openNodeIds,
                rows = rows,
                firstTopicKey = subject.nodes.firstOrNull()?.id,
                firstTopicTitle = subject.nodes.firstOrNull()?.title.orEmpty(),
            )
        }

        val allLeavesGlobal = examPack.leafIds()
        val doneGlobal = allLeavesGlobal.count { it in coveredNodeIds }
        val percentGlobal = if (allLeavesGlobal.isNotEmpty()) doneGlobal * 100.0 / allLeavesGlobal.size else 0.0
        val totalDoneMinutesGlobal = examPack.subjects.sumOf { subject -> subject.nodes.sumOf { doneMinutesFor(it) } }

        _state.update {
            SyllabusUiState(
                loading = false,
                subjects = subjects,
                allCount = subjects.size,
                dueCount = subjects.count { it.percent < 100 },
                completedPercentLabel = String.format(Locale.US, "%.2f%%", percentGlobal),
                timeSpentLabel = formatHoursMinutes(totalDoneMinutesGlobal),
            )
        }
    }

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
            hoursLabel = formatHours(node.totalMinutes() / 60.0),
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
            )
        }
        return listOf(row) + childRows
    }

    private fun sectionTabLabel(name: String): String = when {
        name.contains("Quant", ignoreCase = true) -> "Quant"
        name.contains("Reasoning", ignoreCase = true) -> "Reasoning"
        name.contains("Awareness", ignoreCase = true) -> "GA"
        name.contains("English", ignoreCase = true) -> "English"
        else -> name
    }

    private fun formatHoursMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.US, "%dh %02dm", h, m)
    }

    private fun formatHours(hours: Double): String {
        val value = if (hours == hours.toLong().toDouble()) hours.toLong().toString() else {
            String.format(Locale.US, "%.1f", hours)
        }
        return "${value}h"
    }

    class Factory(
        private val examPackRepository: ExamPackRepository,
        private val topicProgressRepository: TopicProgressRepository,
        private val attemptRepository: AttemptRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SyllabusViewModel(examPackRepository, topicProgressRepository, attemptRepository) as T
    }
}
