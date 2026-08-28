package com.exam.assistant.domain

/**
 * The static, versioned content of one exam's syllabus. Replaces the
 * positional [SyllabusSection]/[SyllabusTopicNode] model — every node here
 * carries a permanent [SyllabusNode.id] assigned once by
 * tools/assign_syllabus_ids.py, not derived from array position.
 */
data class ExamPack(
    val schemaVersion: Int,
    val examId: String,
    val displayName: String,
    val syllabusVersion: String,
    val subjects: List<ExamSubject>,
)

data class ExamSubject(
    val id: String,
    val name: String,
    val shortName: String? = null,
    val order: Int,
    val questions: Int? = null,
    val nodes: List<SyllabusNode>,
)

data class SyllabusNode(
    val id: String,
    val title: String,
    val kind: SyllabusNodeKind,
    val order: Int,
    val estimatedMinutes: Int? = null,
    val children: List<SyllabusNode> = emptyList(),
)

enum class SyllabusNodeKind {
    UNIT,
    CHAPTER,
    SECTION,
    TOPIC,
    SUBTOPIC,
}

/** Sum of this node's own estimated time plus every descendant's, recursively. */
fun SyllabusNode.totalMinutes(): Int =
    (estimatedMinutes ?: 0) + children.sumOf { it.totalMinutes() }

/** Leaf ids under this node — the node itself if it has no children. */
fun SyllabusNode.leafIds(): List<String> =
    if (children.isEmpty()) listOf(id) else children.flatMap { it.leafIds() }

fun ExamSubject.leafIds(): List<String> = nodes.flatMap { it.leafIds() }

fun ExamPack.leafIds(): List<String> = subjects.flatMap { it.leafIds() }

fun ExamSubject.totalMinutes(): Int = nodes.sumOf { it.totalMinutes() }

/** All nodes in this subject's tree, any depth, parent before children. */
fun ExamSubject.flatten(): List<SyllabusNode> = nodes.flatMap { it.flatten() }

fun SyllabusNode.flatten(): List<SyllabusNode> = listOf(this) + children.flatMap { it.flatten() }

fun ExamPack.findNode(nodeId: String): SyllabusNode? =
    subjects.firstNotNullOfOrNull { subject -> subject.flatten().firstOrNull { it.id == nodeId } }

fun ExamPack.findSubjectOf(nodeId: String): ExamSubject? =
    subjects.firstOrNull { subject -> subject.flatten().any { it.id == nodeId } }

/**
 * Every node id from the root of [nodeId] down through its descendants
 * (inclusive) — the set an inclusion/exclusion override at [nodeId] applies
 * to.
 */
fun ExamPack.subtreeIds(nodeId: String): Set<String> {
    val node = findNode(nodeId) ?: return emptySet()
    return node.flatten().map { it.id }.toSet()
}
