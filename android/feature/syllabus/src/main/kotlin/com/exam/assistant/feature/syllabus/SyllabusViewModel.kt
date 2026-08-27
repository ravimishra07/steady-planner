package com.exam.assistant.feature.syllabus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.SyllabusUiState as StoredSyllabusUi
import com.exam.assistant.domain.SyllabusSection
import com.exam.assistant.domain.SyllabusTopicNode
import com.exam.assistant.domain.leafKeys
import com.exam.assistant.domain.sectionSubjectId
import com.exam.assistant.domain.splitHours
import com.exam.assistant.domain.tickState
import com.exam.assistant.domain.topicHours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class SyllabusViewModel(
    private val syllabusRepository: SyllabusRepository,
    private val syllabusStore: SyllabusStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SyllabusUiState())
    val state: StateFlow<SyllabusUiState> = _state.asStateFlow()

    private var sections: List<SyllabusSection> = emptyList()
    private var stored: StoredSyllabusUi = StoredSyllabusUi()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            sections = syllabusRepository.tier1Sections()
            stored = syllabusStore.load()
            rebuild()
        }
    }

    fun toggleExpand(key: String) {
        val nextOpen = stored.openNodes.toMutableSet()
        if (key in nextOpen) nextOpen.remove(key) else nextOpen.add(key)
        stored = stored.copy(openNodes = nextOpen)
        viewModelScope.launch {
            syllabusStore.save(stored)
            rebuild()
        }
    }

    fun toggleTick(key: String) {
        val node = nodeAt(key) ?: return
        val leaves = leafKeys(node, key)
        val allDone = leaves.all { it in stored.doneLeaves }
        val nextDone = stored.doneLeaves.toMutableSet()
        leaves.forEach { leaf ->
            if (allDone) nextDone.remove(leaf) else nextDone.add(leaf)
        }
        stored = stored.copy(doneLeaves = nextDone)
        viewModelScope.launch {
            syllabusStore.save(stored)
            rebuild()
        }
    }

    private suspend fun rebuild() {
        val subjects = sections.mapIndexed { sectionIndex, section ->
            val cardKey = "subject_$sectionIndex"
            val subjectId = sectionSubjectId(sectionIndex)
            val rows = section.topics.flatMapIndexed { topicIndex, topic ->
                buildRows(
                    node = topic,
                    key = "t1_${sectionIndex}_$topicIndex",
                    hours = topicHours(topic),
                    depth = 0,
                    ancestorContinues = emptyList(),
                    isLast = topicIndex == section.topics.lastIndex,
                    subjectId = subjectId,
                    sectionName = section.name,
                )
            }

            val allLeaves = section.topics.flatMapIndexed { ti, topic -> leafKeys(topic, "t1_${sectionIndex}_$ti") }
            val doneCount = allLeaves.count { it in stored.doneLeaves }
            val totalCount = allLeaves.size
            val percent = if (totalCount > 0) doneCount * 100 / totalCount else 0

            val totalHours = section.topics.sumOf { topicHours(it) }
            val doneHours = section.topics.foldIndexed(0.0) { ti, acc, topic ->
                val leaves = leafKeys(topic, "t1_${sectionIndex}_$ti")
                val done = leaves.count { it in stored.doneLeaves }
                val fraction = if (leaves.isEmpty()) 0.0 else done.toDouble() / leaves.size
                acc + topicHours(topic) * fraction
            }

            SyllabusSubjectCard(
                key = cardKey,
                name = section.name,
                shortLabel = sectionTabLabel(section.name),
                subjectId = subjectId,
                percent = percent,
                timeSpentLabel = formatHoursMinutes(doneHours),
                expanded = cardKey in stored.openNodes,
                rows = rows,
                firstTopicKey = section.topics.indices.firstOrNull()?.let { "t1_${sectionIndex}_$it" },
                firstTopicTitle = section.topics.firstOrNull()?.name.orEmpty(),
            )
        }

        val allLeavesGlobal = sections.flatMapIndexed { sectionIndex, section ->
            section.topics.flatMapIndexed { topicIndex, topic ->
                leafKeys(topic, "t1_${sectionIndex}_$topicIndex")
            }
        }
        val doneGlobal = allLeavesGlobal.count { it in stored.doneLeaves }
        val totalGlobal = allLeavesGlobal.size
        val percentGlobal = if (totalGlobal > 0) doneGlobal * 100.0 / totalGlobal else 0.0
        val totalDoneHoursGlobal = sections.foldIndexed(0.0) { sectionIndex, sectionAcc, section ->
            sectionAcc + section.topics.foldIndexed(0.0) { ti, acc, topic ->
                val leaves = leafKeys(topic, "t1_${sectionIndex}_$ti")
                val done = leaves.count { it in stored.doneLeaves }
                val fraction = if (leaves.isEmpty()) 0.0 else done.toDouble() / leaves.size
                acc + topicHours(topic) * fraction
            }
        }

        _state.update {
            SyllabusUiState(
                loading = false,
                subjects = subjects,
                allCount = subjects.size,
                dueCount = subjects.count { it.percent < 100 },
                completedPercentLabel = String.format(Locale.US, "%.2f%%", percentGlobal),
                timeSpentLabel = formatHoursMinutes(totalDoneHoursGlobal),
            )
        }
    }

    private fun buildRows(
        node: SyllabusTopicNode,
        key: String,
        hours: Double,
        depth: Int,
        ancestorContinues: List<Boolean>,
        isLast: Boolean,
        subjectId: String,
        sectionName: String,
    ): List<SyllabusTreeRow> {
        val leaves = leafKeys(node, key)
        val doneCount = leaves.count { it in stored.doneLeaves }
        val percent = if (leaves.isEmpty()) 0 else doneCount * 100 / leaves.size
        val row = SyllabusTreeRow(
            key = key,
            name = node.name,
            hoursLabel = formatHours(hours),
            depth = depth,
            hasChildren = node.children.isNotEmpty(),
            expanded = key in stored.openNodes,
            tickState = tickState(leaves, stored.doneLeaves),
            percent = percent,
            doneLeafCount = doneCount,
            totalLeafCount = leaves.size,
            ancestorContinues = ancestorContinues,
            isLastChild = isLast,
            subjectId = subjectId,
            sectionName = sectionName,
            topicPath = sectionName,
        )
        if (node.children.isEmpty() || key !in stored.openNodes) {
            return listOf(row)
        }
        val shares = splitHours(hours, node.children.size)
        val childAncestors = ancestorContinues + !isLast
        val childRows = node.children.flatMapIndexed { index, child ->
            buildRows(
                node = child,
                key = "${key}_$index",
                hours = shares[index],
                depth = depth + 1,
                ancestorContinues = childAncestors,
                isLast = index == node.children.lastIndex,
                subjectId = subjectId,
                sectionName = sectionName,
            )
        }
        return listOf(row) + childRows
    }

    private fun nodeAt(key: String): SyllabusTopicNode? {
        if (!key.startsWith("t1_")) return null
        val indices = key.removePrefix("t1_").split("_").mapNotNull { it.toIntOrNull() }
        if (indices.isEmpty()) return null
        val section = sections.getOrNull(indices[0]) ?: return null
        var node: SyllabusTopicNode? = null
        var children = section.topics
        indices.drop(1).forEach { childIndex ->
            node = children.getOrNull(childIndex) ?: return null
            children = node.children
        }
        return node
    }

    private fun sectionTabLabel(name: String): String = when {
        name.contains("Quant", ignoreCase = true) -> "Quant"
        name.contains("Reasoning", ignoreCase = true) -> "Reasoning"
        name.contains("Awareness", ignoreCase = true) -> "GA"
        name.contains("English", ignoreCase = true) -> "English"
        else -> name
    }

    private fun formatHoursMinutes(hours: Double): String {
        val totalMinutes = Math.round(hours * 60)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return String.format(Locale.US, "%dh %02dm", h, m)
    }

    private fun formatHours(hours: Double): String {
        val value = if (hours == hours.toLong().toDouble()) hours.toLong().toString() else {
            String.format(Locale.US, "%.1f", hours)
        }
        return "${value}h"
    }

    class Factory(
        private val syllabusRepository: SyllabusRepository,
        private val syllabusStore: SyllabusStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SyllabusViewModel(syllabusRepository, syllabusStore) as T
    }
}
