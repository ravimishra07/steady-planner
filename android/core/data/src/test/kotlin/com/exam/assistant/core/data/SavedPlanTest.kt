package com.exam.assistant.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlanTest {

    private fun plan(commitments: Set<String>) = SavedPlan("cgl", 100, "col", 4f, 7f, "", commitments = commitments)

    @Test
    fun `commitments decode stable ids times and days`() {
        val decoded = plan(setOf("id|coaching|960|1140|1,2,3,4,5,6")).decodedCommitments().single()

        assertEquals("id", decoded.id)
        assertEquals("coaching", decoded.kind)
        assertEquals(960, decoded.startMinute)
        assertEquals(1140, decoded.endMinute)
        assertEquals((1..6).toSet(), decoded.days)
        assertEquals(null, decoded.customLabel)
    }

    @Test
    fun `commitments decode optional custom labels`() {
        val decoded = plan(setOf("id|other|960|1140|1,2|Mock+test+at+school")).decodedCommitments().single()

        assertEquals("Mock test at school", decoded.customLabel)
    }

    @Test
    fun `malformed commitments fail closed per row`() {
        val decoded = plan(setOf("broken", "id|meal|900|800|0", "id|meal|780|840|0,7,x")).decodedCommitments()

        assertEquals(1, decoded.size)
        assertEquals(setOf(0), decoded.single().days)
        assertTrue(plan(setOf("id|meal|780|840|")).decodedCommitments().isEmpty())
    }
}
