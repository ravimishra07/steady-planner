package com.exam.assistant.feature.syllabus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.SyllabusUiState as StoredSyllabusUi
import com.exam.assistant.domain.SyllabusSection
import com.exam.assistant.domain.SyllabusTickState
import com.exam.assistant.domain.SyllabusTopicNode
import com.exam.assistant.domain.leafKeys
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
            if (stored.sectionIndex >= sections.size) {
                stored = stored.copy(sectionIndex = 0)
            }
            rebuild()
        }
    }

    fun selectSection(index: Int) {
        if (index !in sections.indices) return
        stored = stored.copy(sectionIndex = index)
        viewModelScope.launch {
            syllabusStore.save(stored)
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
        val section = sections.getOrNull(stored.sectionIndex) ?: return
        val doneHours = section.topics.foldIndexed(0.0) { topicIndex, acc, topic ->
            val leaves = leafKeys(topic, topicKey(stored.sectionIndex, topicIndex))
            val doneCount = leaves.count { it in stored.doneLeaves }
            val fraction = if (leaves.isEmpty()) 0.0 else doneCount.toDouble() / leaves.size
            acc + topicHours(topic) * fraction
        }
        val totalHours = section.topics.sumOf { topicHours(it) }.toInt()
        val rows = section.topics.flatMapIndexed { topicIndex, topic ->
            buildRows(
                node = topic,
                key = "t1_${stored.sectionIndex}_$topicIndex",
                hours = topicHours(topic),
                depth = 0,
            )
        }
        _state.update {
            SyllabusUiState(
                loading = false,
                sectionIndex = stored.sectionIndex,
                sectionTabs = sections.map { sectionTabLabel(it.name) },
                summary = "${doneHours.toInt()} of $totalHours hrs done · ${section.topics.size} topics",
                rows = rows,
            )
        }
    }

    private fun buildRows(
        node: SyllabusTopicNode,
        key: String,
        hours: Double,
        depth: Int,
    ): List<SyllabusTreeRow> {
        val leaves = leafKeys(node, key)
        val row = SyllabusTreeRow(
            key = key,
            name = node.name,
            hoursLabel = formatHours(hours),
            depth = depth,
            hasChildren = node.children.isNotEmpty(),
            expanded = key in stored.openNodes,
            tickState = tickState(leaves, stored.doneLeaves),
        )
        if (node.children.isEmpty() || key !in stored.openNodes) {
            return listOf(row)
        }
        val shares = splitHours(hours, node.children.size)
        val childRows = node.children.flatMapIndexed { index, child ->
            buildRows(
                node = child,
                key = "${key}_$index",
                hours = shares[index],
                depth = depth + 1,
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

    private fun topicKey(sectionIndex: Int, topicIndex: Int) = "t1_${sectionIndex}_$topicIndex"

    private fun formatHours(hours: Double): String {
        val value = if (hours == hours.toLong().toDouble()) hours.toLong().toString() else {
            String.format(Locale.US, "%.1f", hours)
        }
        return "${value}h"
    }

    private fun sectionTabLabel(name: String): String = when {
        name.contains("Quant", ignoreCase = true) -> "Quant"
        name.contains("Reasoning", ignoreCase = true) -> "Reasoning"
        name.contains("Awareness", ignoreCase = true) -> "GA"
        name.contains("English", ignoreCase = true) -> "English"
        else -> name
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
