package com.exam.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamPackTest {

    private fun leaf(id: String, minutes: Int? = null) =
        SyllabusNode(id = id, title = id, kind = SyllabusNodeKind.SUBTOPIC, order = 0, estimatedMinutes = minutes)

    private fun node(id: String, vararg children: SyllabusNode) =
        SyllabusNode(id = id, title = id, kind = SyllabusNodeKind.TOPIC, order = 0, children = children.toList())

    private fun samplePack(): ExamPack {
        val subject = ExamSubject(
            id = "quant",
            name = "Quant",
            order = 0,
            nodes = listOf(
                node(
                    "quant.a",
                    node("quant.a.x", leaf("quant.a.x.1"), leaf("quant.a.x.2")),
                    leaf("quant.a.y"),
                ),
                leaf("quant.b"),
            ),
        )
        return ExamPack(1, "ssc_cgl", "SSC CGL", "2026.v1", listOf(subject))
    }

    @Test
    fun `leafIds finds every leaf regardless of depth`() {
        val pack = samplePack()
        assertEquals(setOf("quant.a.x.1", "quant.a.x.2", "quant.a.y", "quant.b"), pack.leafIds().toSet())
    }

    @Test
    fun `a node with no children is its own leaf`() {
        val onlyLeaf = leaf("solo")
        assertEquals(listOf("solo"), onlyLeaf.leafIds())
    }

    @Test
    fun `findNode locates a node at any depth by stable id, not position`() {
        val pack = samplePack()
        assertEquals("quant.a.x.1", pack.findNode("quant.a.x.1")?.id)
        assertEquals("quant.a", pack.findNode("quant.a")?.id)
        assertNull(pack.findNode("does.not.exist"))
    }

    @Test
    fun `findSubjectOf resolves the owning subject for a deep node`() {
        val pack = samplePack()
        assertEquals("quant", pack.findSubjectOf("quant.a.x.2")?.id)
    }

    @Test
    fun `subtreeIds is inclusive of the node itself and every descendant`() {
        val pack = samplePack()
        assertEquals(setOf("quant.a", "quant.a.x", "quant.a.x.1", "quant.a.x.2", "quant.a.y"), pack.subtreeIds("quant.a"))
        assertEquals(setOf("quant.a.y"), pack.subtreeIds("quant.a.y"))
    }

    @Test
    fun `totalMinutes sums the whole subtree`() {
        val tree = node("p", leaf("c1", 30), leaf("c2", 45))
        assertEquals(75, tree.totalMinutes())
    }

    @Test
    fun `reordering children does not change any node's id`() {
        val a = samplePack()
        val subject = a.subjects.single()
        val reordered = subject.copy(nodes = subject.nodes.reversed())
        assertEquals(a.leafIds().toSet(), reordered.leafIds().toSet())
        assertTrue(reordered.flatten().all { node -> a.subjects.single().flatten().any { it.id == node.id } })
    }
}
